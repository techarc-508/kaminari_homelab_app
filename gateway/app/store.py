"""Durable SQLite state for the Kaminari gateway.

Every security-relevant record lives here so it survives a gateway restart, a
container recreation and a host reboot:

* append-only audit events,
* idempotency reservations with their stored outcome,
* consumed command nonces (replay protection),
* the TOTP step-up enrollment,
* one-time step-up challenges bound to a request, action, target and args.

There is deliberately no in-memory production path. If this store cannot be
written the gateway fails closed instead of executing a mutation without an
audit trail.
"""
from __future__ import annotations

import json
import sqlite3
import threading
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Dict, Iterator, List, Optional

_SCHEMA = """
CREATE TABLE IF NOT EXISTS audit_events (
    seq            INTEGER PRIMARY KEY AUTOINCREMENT,
    audit_id       TEXT NOT NULL UNIQUE,
    occurred_at    TEXT NOT NULL,
    actor          TEXT NOT NULL,
    operation_id   TEXT NOT NULL,
    target         TEXT NOT NULL,
    risk           TEXT NOT NULL,
    outcome        TEXT NOT NULL,
    correlation_id TEXT,
    details_json   TEXT NOT NULL DEFAULT '{}'
);

CREATE TRIGGER IF NOT EXISTS audit_events_append_only_update
BEFORE UPDATE ON audit_events
BEGIN
    SELECT RAISE(ABORT, 'audit_events is append-only');
END;

CREATE TRIGGER IF NOT EXISTS audit_events_append_only_delete
BEFORE DELETE ON audit_events
BEGIN
    SELECT RAISE(ABORT, 'audit_events is append-only');
END;

CREATE TABLE IF NOT EXISTS idempotency (
    key          TEXT PRIMARY KEY,
    fingerprint  TEXT NOT NULL,
    state        TEXT NOT NULL,
    payload_json TEXT,
    created_at   REAL NOT NULL,
    expires_at   REAL NOT NULL
);

CREATE TABLE IF NOT EXISTS replay_nonces (
    nonce      TEXT PRIMARY KEY,
    expires_at REAL NOT NULL
);

CREATE TABLE IF NOT EXISTS stepup_enrollment (
    id             INTEGER PRIMARY KEY CHECK (id = 1),
    secret_b32     TEXT NOT NULL,
    created_at     TEXT NOT NULL,
    verified_at    TEXT,
    last_used_step INTEGER
);

CREATE TABLE IF NOT EXISTS stepup_challenges (
    challenge_id TEXT PRIMARY KEY,
    request_id   TEXT NOT NULL,
    action       TEXT NOT NULL,
    target       TEXT NOT NULL,
    args_hash    TEXT NOT NULL,
    binding_hash TEXT NOT NULL,
    created_at   REAL NOT NULL,
    expires_at   REAL NOT NULL,
    consumed_at  REAL,
    attempts     INTEGER NOT NULL DEFAULT 0
);
"""


class SqliteStore:
    """Single-file, WAL-backed durable store.

    A connection is opened per transaction; SQLite serialises writers and the
    process lock keeps reservation checks atomic inside one gateway process.
    ``BEGIN IMMEDIATE`` is used wherever a read-then-write decision protects a
    security guarantee (reservations, nonce consumption, challenge use).
    """

    def __init__(self, db_path: str):
        self.path = str(db_path)
        if self.path == ":memory:":
            raise ValueError("the gateway store requires a file path, not :memory:")
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()
        self._initialize()

    # ------------------------------------------------------------------ setup

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.path, timeout=15.0, isolation_level=None)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA synchronous=FULL")
        conn.execute("PRAGMA busy_timeout=15000")
        conn.execute("PRAGMA foreign_keys=ON")
        return conn

    def _initialize(self) -> None:
        conn = self._connect()
        try:
            conn.executescript(_SCHEMA)
        finally:
            conn.close()

    @contextmanager
    def _tx(self, *, immediate: bool = False) -> Iterator[sqlite3.Connection]:
        with self._lock:
            conn = self._connect()
            try:
                conn.execute("BEGIN IMMEDIATE" if immediate else "BEGIN")
                yield conn
                conn.execute("COMMIT")
            except Exception:
                try:
                    conn.execute("ROLLBACK")
                except sqlite3.Error:
                    pass
                raise
            finally:
                conn.close()

    # ------------------------------------------------------------------ health

    def health(self) -> bool:
        with self._tx() as conn:
            conn.execute("SELECT 1").fetchone()
        return True

    # ------------------------------------------------------------------- audit

    def append_audit(self, event: Dict[str, Any]) -> None:
        details = event.get("details") or {}
        with self._tx(immediate=True) as conn:
            conn.execute(
                """
                INSERT INTO audit_events
                    (audit_id, occurred_at, actor, operation_id, target, risk,
                     outcome, correlation_id, details_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    event["audit_id"],
                    event["occurred_at"],
                    event["actor"],
                    event["operation_id"],
                    event["target"],
                    event["risk"],
                    event["outcome"],
                    event.get("correlation_id"),
                    json.dumps(details, sort_keys=True),
                ),
            )

    def list_audit(self, limit: int = 50) -> List[Dict[str, Any]]:
        bounded = max(1, min(int(limit), 500))
        with self._tx() as conn:
            rows = conn.execute(
                "SELECT * FROM audit_events ORDER BY seq DESC LIMIT ?", (bounded,)
            ).fetchall()
        return [self._audit_row(row) for row in rows]

    def audit_count(self) -> int:
        with self._tx() as conn:
            row = conn.execute("SELECT COUNT(*) AS n FROM audit_events").fetchone()
        return int(row["n"])

    @staticmethod
    def _audit_row(row: sqlite3.Row) -> Dict[str, Any]:
        return {
            "audit_id": row["audit_id"],
            "occurred_at": row["occurred_at"],
            "actor": row["actor"],
            "operation_id": row["operation_id"],
            "target": row["target"],
            "risk": row["risk"],
            "outcome": row["outcome"],
            "correlation_id": row["correlation_id"],
            "details": json.loads(row["details_json"] or "{}"),
        }

    # ------------------------------------------------------------- idempotency

    def idempotency_get(self, key: str) -> Optional[Dict[str, Any]]:
        with self._tx() as conn:
            conn.execute("DELETE FROM idempotency WHERE expires_at <= ?", (time.time(),))
            row = conn.execute(
                "SELECT * FROM idempotency WHERE key = ?", (key,)
            ).fetchone()
        return self._idempotency_row(row)

    def idempotency_reserve(
        self, key: str, fingerprint: str, ttl_seconds: int
    ) -> str:
        """Atomically reserve a command key.

        Returns ``reserved``, ``in_progress``, ``complete`` or ``conflict``.
        """
        if not key or not fingerprint:
            raise ValueError("idempotency key and fingerprint are required")
        now = time.time()
        expires_at = now + max(1, int(ttl_seconds))
        with self._tx(immediate=True) as conn:
            conn.execute("DELETE FROM idempotency WHERE expires_at <= ?", (now,))
            cursor = conn.execute(
                """
                INSERT OR IGNORE INTO idempotency
                    (key, fingerprint, state, created_at, expires_at)
                VALUES (?, ?, 'in_progress', ?, ?)
                """,
                (key, fingerprint, now, expires_at),
            )
            if cursor.rowcount == 1:
                return "reserved"
            row = conn.execute(
                "SELECT fingerprint, state FROM idempotency WHERE key = ?", (key,)
            ).fetchone()
        if row is None:
            return "in_progress"
        if row["fingerprint"] != fingerprint:
            return "conflict"
        if row["state"] == "in_progress":
            return "in_progress"
        return "complete"

    def idempotency_complete(
        self, key: str, state: str, payload: Dict[str, Any]
    ) -> bool:
        with self._tx(immediate=True) as conn:
            cursor = conn.execute(
                "UPDATE idempotency SET state = ?, payload_json = ? WHERE key = ?",
                (state, json.dumps(payload, sort_keys=True), key),
            )
            return cursor.rowcount > 0

    def idempotency_release(self, key: str) -> None:
        with self._tx(immediate=True) as conn:
            conn.execute("DELETE FROM idempotency WHERE key = ?", (key,))

    @staticmethod
    def _idempotency_row(row: Optional[sqlite3.Row]) -> Optional[Dict[str, Any]]:
        if row is None:
            return None
        payload = json.loads(row["payload_json"]) if row["payload_json"] else None
        return {
            "key": row["key"],
            "fingerprint": row["fingerprint"],
            "state": row["state"],
            "payload": payload,
            "expires_at": row["expires_at"],
        }

    # ----------------------------------------------------------- replay nonces

    def nonce_check_and_store(self, nonce: str, expires_at: float) -> bool:
        """Return True only for the first use of a nonce (durable)."""
        now = time.time()
        with self._tx(immediate=True) as conn:
            conn.execute("DELETE FROM replay_nonces WHERE expires_at <= ?", (now,))
            cursor = conn.execute(
                "INSERT OR IGNORE INTO replay_nonces (nonce, expires_at) VALUES (?, ?)",
                (nonce, max(expires_at, now + 1.0)),
            )
            return cursor.rowcount == 1

    # --------------------------------------------------------------- enrollment

    def enrollment_get(self) -> Optional[Dict[str, Any]]:
        with self._tx() as conn:
            row = conn.execute("SELECT * FROM stepup_enrollment WHERE id = 1").fetchone()
        if row is None:
            return None
        return {
            "secret_b32": row["secret_b32"],
            "created_at": row["created_at"],
            "verified_at": row["verified_at"],
            "last_used_step": row["last_used_step"],
        }

    def enrollment_create(self, secret_b32: str, created_at: str) -> bool:
        with self._tx(immediate=True) as conn:
            cursor = conn.execute(
                """
                INSERT OR IGNORE INTO stepup_enrollment
                    (id, secret_b32, created_at, verified_at, last_used_step)
                VALUES (1, ?, ?, NULL, NULL)
                """,
                (secret_b32, created_at),
            )
            return cursor.rowcount == 1

    def enrollment_mark_verified(self, verified_at: str, step: int) -> None:
        with self._tx(immediate=True) as conn:
            conn.execute(
                """
                UPDATE stepup_enrollment
                   SET verified_at = ?, last_used_step = ?
                 WHERE id = 1
                """,
                (verified_at, step),
            )

    def enrollment_set_last_step(self, step: int) -> None:
        with self._tx(immediate=True) as conn:
            conn.execute(
                "UPDATE stepup_enrollment SET last_used_step = ? WHERE id = 1",
                (step,),
            )

    # --------------------------------------------------------------- challenges

    def challenge_create(
        self,
        *,
        challenge_id: str,
        request_id: str,
        action: str,
        target: str,
        args_hash: str,
        binding_hash: str,
        ttl_seconds: int,
        now: float,
    ) -> Dict[str, Any]:
        expires_at = now + max(1, int(ttl_seconds))
        with self._tx(immediate=True) as conn:
            conn.execute("DELETE FROM stepup_challenges WHERE expires_at <= ?", (now,))
            conn.execute(
                """
                INSERT INTO stepup_challenges
                    (challenge_id, request_id, action, target, args_hash,
                     binding_hash, created_at, expires_at, consumed_at, attempts)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 0)
                """,
                (
                    challenge_id,
                    request_id,
                    action,
                    target,
                    args_hash,
                    binding_hash,
                    now,
                    expires_at,
                ),
            )
        return {
            "challenge_id": challenge_id,
            "request_id": request_id,
            "action": action,
            "target": target,
            "expires_at": expires_at,
        }

    def challenge_get(self, challenge_id: str) -> Optional[Dict[str, Any]]:
        with self._tx() as conn:
            row = conn.execute(
                "SELECT * FROM stepup_challenges WHERE challenge_id = ?",
                (challenge_id,),
            ).fetchone()
        if row is None:
            return None
        return {
            "challenge_id": row["challenge_id"],
            "request_id": row["request_id"],
            "action": row["action"],
            "target": row["target"],
            "args_hash": row["args_hash"],
            "binding_hash": row["binding_hash"],
            "created_at": row["created_at"],
            "expires_at": row["expires_at"],
            "consumed_at": row["consumed_at"],
            "attempts": row["attempts"],
        }

    def challenge_increment_attempts(self, challenge_id: str) -> int:
        with self._tx(immediate=True) as conn:
            conn.execute(
                "UPDATE stepup_challenges SET attempts = attempts + 1 WHERE challenge_id = ?",
                (challenge_id,),
            )
            row = conn.execute(
                "SELECT attempts FROM stepup_challenges WHERE challenge_id = ?",
                (challenge_id,),
            ).fetchone()
        return int(row["attempts"]) if row else 0

    def challenge_consume(self, challenge_id: str, now: float) -> bool:
        with self._tx(immediate=True) as conn:
            cursor = conn.execute(
                """
                UPDATE stepup_challenges
                   SET consumed_at = ?
                 WHERE challenge_id = ? AND consumed_at IS NULL
                """,
                (now, challenge_id),
            )
            return cursor.rowcount == 1

    def challenge_invalidate(self, challenge_id: str, now: float) -> None:
        with self._tx(immediate=True) as conn:
            conn.execute(
                """
                UPDATE stepup_challenges
                   SET consumed_at = COALESCE(consumed_at, ?)
                 WHERE challenge_id = ?
                """,
                (now, challenge_id),
            )

    # ---------------------------------------------------------------- test only

    def reset_all_state(self) -> None:
        """Destructive helper for isolated tests. No route calls this."""
        with self._tx(immediate=True) as conn:
            conn.execute("DROP TRIGGER IF EXISTS audit_events_append_only_update")
            conn.execute("DROP TRIGGER IF EXISTS audit_events_append_only_delete")
            for table in (
                "audit_events",
                "idempotency",
                "replay_nonces",
                "stepup_enrollment",
                "stepup_challenges",
            ):
                conn.execute(f"DELETE FROM {table}")
        conn = self._connect()
        try:
            conn.executescript(_SCHEMA)
        finally:
            conn.close()
