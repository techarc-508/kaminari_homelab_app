"""Audit event contract and the durable SQLite sink.

Two sinks exist: `SqliteAuditSink` is the production sink and is append-only at
the database level (triggers reject UPDATE/DELETE); `InMemoryAuditSink` remains
only for isolated unit tests and explicitly reports `durable = False`.
"""
from __future__ import annotations

import uuid
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Deque, Dict, List, Optional, Protocol

_SENSITIVE_FRAGMENTS = (
    "token",
    "secret",
    "password",
    "authorization",
    "private_key",
    "api_key",
    "totp",
    "code",
)


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _safe_details(details: Dict[str, Any]) -> Dict[str, str]:
    return {
        str(key): str(value)
        for key, value in details.items()
        if not any(fragment in str(key).lower() for fragment in _SENSITIVE_FRAGMENTS)
    }


@dataclass(frozen=True)
class AuditEvent:
    audit_id: str
    occurred_at: str
    actor: str
    operation_id: str
    target: str
    risk: str
    outcome: str
    correlation_id: Optional[str] = None
    details: Dict[str, str] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "audit_id": self.audit_id,
            "occurred_at": self.occurred_at,
            "actor": self.actor,
            "operation_id": self.operation_id,
            "target": self.target,
            "risk": self.risk,
            "outcome": self.outcome,
            "correlation_id": self.correlation_id,
            "details": _safe_details(self.details),
        }


def new_audit_event(
    *,
    actor: str,
    operation_id: str,
    target: str,
    risk: str,
    outcome: str,
    correlation_id: Optional[str] = None,
    details: Optional[Dict[str, Any]] = None,
) -> AuditEvent:
    return AuditEvent(
        audit_id=str(uuid.uuid4()),
        occurred_at=_now_iso(),
        actor=actor,
        operation_id=operation_id,
        target=target,
        risk=risk,
        outcome=outcome,
        correlation_id=correlation_id,
        details=_safe_details(details or {}),
    )


class AuditSink(Protocol):
    durable: bool

    def append(self, event: AuditEvent) -> None: ...

    def events(self) -> List[AuditEvent]: ...


class SqliteAuditSink:
    """Durable, append-only audit sink."""

    durable = True

    def __init__(self, store: Any):
        self._store = store

    def append(self, event: AuditEvent) -> None:
        self._store.append_audit(event.to_dict())

    def events(self, limit: int = 500) -> List[AuditEvent]:
        return [
            AuditEvent(
                audit_id=row["audit_id"],
                occurred_at=row["occurred_at"],
                actor=row["actor"],
                operation_id=row["operation_id"],
                target=row["target"],
                risk=row["risk"],
                outcome=row["outcome"],
                correlation_id=row["correlation_id"],
                details=row["details"],
            )
            for row in self._store.list_audit(limit)
        ]

    def count(self) -> int:
        return self._store.audit_count()


class InMemoryAuditSink:
    """Bounded process-local sink for tests only."""

    durable = False

    def __init__(self, max_events: int = 1000):
        self._events: Deque[AuditEvent] = deque(maxlen=max(1, max_events))

    def append(self, event: AuditEvent) -> None:
        self._events.append(event)

    def events(self) -> List[AuditEvent]:
        return list(self._events)

    def count(self) -> int:
        return len(self._events)
