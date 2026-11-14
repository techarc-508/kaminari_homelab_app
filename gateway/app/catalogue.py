"""Typed gateway view of the shared `kf-` contract.

`contracts/kf_contract.v1.json` is the single cross-language data source. The
Android catalogue is tested against it; this module loads it directly, so the
gateway cannot drift from the approved topic/action model.
"""
from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Optional, Tuple

from .errors import GatewayError

_DEFAULT_PATH = Path(__file__).resolve().parents[2] / "contracts" / "kf_contract.v1.json"
CONTRACT_PATH = Path(os.environ.get("KAMINARI_CONTRACT_PATH", str(_DEFAULT_PATH)))


@dataclass(frozen=True)
class TopicSpec:
    id: str
    category: str
    description: str
    publisher: str
    readers: Tuple[str, ...]
    writers: Tuple[str, ...]
    reserved: bool
    retention_days: int
    priority: str
    schema: str
    open_decision: Optional[str] = None


@dataclass(frozen=True)
class IdentitySpec:
    name: str
    role: str
    ntfy_admin: bool
    purpose: str


@dataclass(frozen=True)
class ActionSpec:
    id: str
    title: str
    description: str
    target: str
    required_identity: str
    risk: str
    mutation: bool
    step_up: bool
    owner_confirmation: bool
    request_schema: str
    response_schema: str
    timeout_ms: int
    audit: bool
    args: Tuple[str, ...]


def _load() -> dict:
    try:
        raw = CONTRACT_PATH.read_text(encoding="utf-8")
    except OSError as exc:
        raise RuntimeError(f"Kaminari contract not readable at {CONTRACT_PATH}: {exc}") from exc
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Kaminari contract is not valid JSON: {exc}") from exc
    if data.get("schema") != "kf.contract.v1":
        raise RuntimeError("Unsupported Kaminari contract schema")
    return data


CONTRACT = _load()

IDENTITIES: Tuple[IdentitySpec, ...] = tuple(
    IdentitySpec(
        name=item["name"],
        role=item["role"],
        ntfy_admin=bool(item["ntfyAdmin"]),
        purpose=item["purpose"],
    )
    for item in CONTRACT["identities"]
)
IDENTITIES_BY_NAME: Dict[str, IdentitySpec] = {item.name: item for item in IDENTITIES}

TOPICS: Tuple[TopicSpec, ...] = tuple(
    TopicSpec(
        id=item["id"],
        category=item["category"],
        description=item["description"],
        publisher=item["publisher"],
        readers=tuple(item["readers"]),
        writers=tuple(item["writers"]),
        reserved=bool(item["reserved"]),
        retention_days=int(item["retentionDays"]),
        priority=item["priority"],
        schema=item["schema"],
        open_decision=item.get("openDecision"),
    )
    for item in CONTRACT["topics"]
)
TOPICS_BY_ID: Dict[str, TopicSpec] = {item.id: item for item in TOPICS}

ACTIONS: Tuple[ActionSpec, ...] = tuple(
    ActionSpec(
        id=item["id"],
        title=item["title"],
        description=item["description"],
        target=item["target"],
        required_identity=item["requiredIdentity"],
        risk=item["risk"],
        mutation=bool(item["mutation"]),
        step_up=bool(item["stepUp"]),
        owner_confirmation=bool(item["ownerConfirmation"]),
        request_schema=item["requestSchema"],
        response_schema=item["responseSchema"],
        timeout_ms=int(item["timeoutMs"]),
        audit=bool(item["audit"]),
        args=tuple(item["args"]),
    )
    for item in CONTRACT["actions"]
)
ACTIONS_BY_ID: Dict[str, ActionSpec] = {item.id: item for item in ACTIONS}

READ_ACTIONS = frozenset(item.id for item in ACTIONS if not item.mutation)
MUTATION_ACTIONS = frozenset(item.id for item in ACTIONS if item.mutation)
ALL_ACTIONS = frozenset(ACTIONS_BY_ID)

COMMAND_SCHEMA: str = CONTRACT["commandSchema"]
LEGACY_COMMAND_SCHEMAS = frozenset(CONTRACT.get("legacyCommandSchemas", []))
# Dual-accept during the migration: the canonical `kf-` schema plus the
# explicitly listed legacy schemas, so existing producers are not broken.
COMMAND_SCHEMAS = frozenset({COMMAND_SCHEMA} | set(LEGACY_COMMAND_SCHEMAS))
COMMAND_TARGET: str = ACTIONS[0].target if ACTIONS else "proxmox-gateway"
COMMAND_REPLY_TOPIC: str = CONTRACT["commandReplyTopic"]
LEGACY_COMMAND_REPLY_TOPICS = frozenset(CONTRACT.get("legacyCommandReplyTopics", []))
ALLOWED_REPLY_TOPICS = frozenset({COMMAND_REPLY_TOPIC} | set(LEGACY_COMMAND_REPLY_TOPICS))

ADMIN_IDENTITIES = frozenset(item.name for item in IDENTITIES if item.ntfy_admin)


def topic_spec(topic_id: str) -> Optional[TopicSpec]:
    return TOPICS_BY_ID.get(topic_id)


def permission_for(identity_name: str, topic_id: str) -> Optional[str]:
    """Return `read`, `write`, `admin` or None (denied). Deny by default."""
    identity = IDENTITIES_BY_NAME.get(identity_name)
    if identity is None:
        return None
    if identity.ntfy_admin:
        return "admin"
    topic = TOPICS_BY_ID.get(topic_id)
    if topic is None:
        return None
    if identity_name in topic.writers:
        return "write"
    if identity_name in topic.readers:
        return "read"
    return None


def can_read(identity_name: str, topic_id: str) -> bool:
    # A write-only grant does not imply read access in ntfy.
    return permission_for(identity_name, topic_id) in {"read", "admin"}


def can_write(identity_name: str, topic_id: str) -> bool:
    return permission_for(identity_name, topic_id) in {"write", "admin"}


def authorize_action(identity_name: str, action_id: str) -> ActionSpec:
    """Deny-by-default action authorization for the configured gateway identity."""
    identity = IDENTITIES_BY_NAME.get(identity_name)
    if identity is None:
        raise GatewayError("unknown_identity", "Gateway identity is not provisioned", 403)
    spec = ACTIONS_BY_ID.get(action_id)
    if spec is None:
        raise GatewayError("unknown_action", "Unknown command action", 400)
    if identity.ntfy_admin:
        return spec
    if spec.required_identity != identity_name:
        raise GatewayError(
            "operation_not_permitted",
            "This identity is not authorized for the requested operation",
            403,
        )
    return spec


def require_owner_identity(identity_name: str) -> None:
    """Owner-only operations require a configured administrator identity."""
    identity = IDENTITIES_BY_NAME.get(identity_name)
    if identity is None or not identity.ntfy_admin:
        raise GatewayError(
            "owner_identity_not_admin",
            "Configured owner identity is not an administrator",
            503,
        )
