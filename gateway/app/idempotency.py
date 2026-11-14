"""Idempotency stores.

`SqliteIdempotencyStore` is the production store: reservations and stored
outcomes are durable, so a duplicate request cannot execute a mutation twice
across a restart. `InMemoryIdempotencyStore` is a test double only and reports
`durable = False`.
"""
from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Any, Dict, Optional, Protocol


@dataclass
class IdempotencyRecord:
    key: str
    fingerprint: str
    state: str
    result_ref: Optional[str] = None
    expires_at: float = 0.0


class IdempotencyStore(Protocol):
    durable: bool

    def reserve(self, key: str, fingerprint: str, ttl_seconds: int) -> bool: ...

    def complete(self, key: str, result_ref: str) -> bool: ...

    def release(self, key: str) -> None: ...

    def get(self, key: str) -> Optional[IdempotencyRecord]: ...


class SqliteIdempotencyStore:
    """Durable reservation store built on `SqliteStore`."""

    durable = True

    def __init__(self, store: Any):
        self._store = store

    def reserve_state(self, key: str, fingerprint: str, ttl_seconds: int) -> str:
        return self._store.idempotency_reserve(key, fingerprint, ttl_seconds)

    def reserve(self, key: str, fingerprint: str, ttl_seconds: int) -> bool:
        return self.reserve_state(key, fingerprint, ttl_seconds) == "reserved"

    def complete(self, key: str, payload: Dict[str, Any], state: str = "complete") -> bool:
        return self._store.idempotency_complete(key, state, payload)

    def release(self, key: str) -> None:
        self._store.idempotency_release(key)

    def get(self, key: str) -> Optional[Dict[str, Any]]:
        return self._store.idempotency_get(key)


class InMemoryIdempotencyStore:
    """Single-process reservation store. `durable` is False by design."""

    durable = False

    def __init__(self, clock=time.monotonic):
        self._clock = clock
        self._records: Dict[str, IdempotencyRecord] = {}

    def _purge(self) -> None:
        now = self._clock()
        for key in [k for k, record in self._records.items() if record.expires_at <= now]:
            self._records.pop(key, None)

    def reserve(self, key: str, fingerprint: str, ttl_seconds: int) -> bool:
        if not key or not fingerprint:
            raise ValueError("idempotency key and fingerprint are required")
        self._purge()
        if key in self._records:
            return False
        self._records[key] = IdempotencyRecord(
            key=key,
            fingerprint=fingerprint,
            state="in_progress",
            expires_at=self._clock() + max(1, ttl_seconds),
        )
        return True

    def complete(self, key: str, result_ref: str) -> bool:
        record = self._records.get(key)
        if record is None:
            return False
        record.state = "complete"
        record.result_ref = result_ref
        return True

    def release(self, key: str) -> None:
        self._records.pop(key, None)

    def get(self, key: str) -> Optional[IdempotencyRecord]:
        self._purge()
        return self._records.get(key)
