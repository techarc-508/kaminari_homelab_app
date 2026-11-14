"""Typed command validation and execution.

Every command is checked against the shared contract before anything runs:
schema, UUID, nonce shape, timestamp window, target, allowlisted action, exact
argument set and argument patterns. Nothing here interprets free text or opens a
shell.

Mutations require a step-up verifier; the pre-Phase-2 nonce echo is gone. A
mutation is only executed after a one-time approval bound to this exact command
was verified, and idempotency is reserved durably before the Proxmox call.
"""
from __future__ import annotations

import hashlib
import json
import re
import time
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Mapping, Optional, Protocol

from fastapi import HTTPException

from .catalogue import (
    ACTIONS_BY_ID,
    ALL_ACTIONS,
    ALLOWED_REPLY_TOPICS,
    COMMAND_SCHEMAS,
    COMMAND_TARGET,
    MUTATION_ACTIONS,
)
from .models import CommandResultOut
from .proxmox import ProxmoxClient, ProxmoxError

COMMAND_EXPIRY_MS = 5 * 60 * 1000
COMMAND_CLOCK_SKEW_MS = 60 * 1000
IDEMPOTENCY_TTL_SECONDS = 15 * 60

NODE_PATTERN = re.compile(r"^[a-z0-9][a-z0-9_-]{0,31}$")
VMID_PATTERN = re.compile(r"^[0-9]{1,10}$")
ARG_PATTERNS = {
    "node": NODE_PATTERN,
    "vmid": VMID_PATTERN,
}


class ReplayGuard(Protocol):
    def nonce_check_and_store(self, nonce: str, expires_at: float) -> bool: ...


@dataclass
class ExecutionOutcome:
    status: str
    result: CommandResultOut
    expired: bool = False
    replayed: bool = False


def parse_timestamp(value: str) -> int:
    try:
        if value.endswith("Z"):
            value = value[:-1] + "+00:00"
        dt = datetime.fromisoformat(value)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return int(dt.timestamp() * 1000)
    except (ValueError, TypeError, OSError):
        raise HTTPException(status_code=400, detail="Invalid timestamp")


def validate_args(spec: Any, args: Mapping[str, Any]) -> None:
    if not isinstance(args, Mapping):
        raise HTTPException(status_code=400, detail="Invalid args")
    if set(args) != set(spec.args):
        raise HTTPException(
            status_code=400, detail="Command args do not match the action contract"
        )
    for key in spec.args:
        value = str(args.get(key, ""))
        pattern = ARG_PATTERNS.get(key)
        if pattern is None or not pattern.fullmatch(value):
            raise HTTPException(status_code=400, detail=f"Invalid {key}")


def validate_envelope(envelope: Mapping[str, Any], now_ms: int, mutations_enabled: bool) -> None:
    if envelope.get("schema") not in COMMAND_SCHEMAS:
        raise HTTPException(status_code=400, detail="Unknown command schema")
    try:
        uuid.UUID(str(envelope.get("command_id", "")))
    except (ValueError, AttributeError):
        raise HTTPException(status_code=400, detail="command_id must be a UUID")
    nonce = str(envelope.get("nonce", ""))
    if not re.fullmatch(r"[A-Za-z0-9_-]{43}", nonce):
        raise HTTPException(status_code=400, detail="Invalid nonce")
    issued_at = parse_timestamp(str(envelope.get("issued_at", "")))
    expires_at = parse_timestamp(str(envelope.get("expires_at", "")))
    if expires_at <= issued_at:
        raise HTTPException(status_code=400, detail="expires_at must be after issued_at")
    if expires_at - issued_at > COMMAND_EXPIRY_MS:
        raise HTTPException(
            status_code=400, detail="command window cannot exceed five minutes"
        )
    if abs(now_ms - issued_at) > COMMAND_CLOCK_SKEW_MS:
        raise HTTPException(status_code=400, detail="issued_at is outside the allowed skew")
    if now_ms >= expires_at:
        raise HTTPException(status_code=400, detail="Command request has expired")
    if envelope.get("target") != COMMAND_TARGET:
        raise HTTPException(status_code=400, detail="Invalid command target")
    action = str(envelope.get("action", ""))
    spec = ACTIONS_BY_ID.get(action)
    if action not in ALL_ACTIONS or spec is None:
        raise HTTPException(status_code=400, detail="Unknown command action")
    if action in MUTATION_ACTIONS and not mutations_enabled:
        raise HTTPException(status_code=403, detail="Mutations are disabled on this gateway")
    requested_by = str(envelope.get("requested_by", ""))
    if not re.fullmatch(r"android:[A-Za-z0-9_-]{1,56}", requested_by):
        raise HTTPException(status_code=400, detail="Invalid requested_by")
    if str(envelope.get("reply_topic", "")) not in ALLOWED_REPLY_TOPICS:
        raise HTTPException(status_code=400, detail="Invalid reply_topic")
    validate_args(spec, envelope.get("args") or {})
    expected_confirmation = action in MUTATION_ACTIONS
    if bool(envelope.get("requires_confirmation", False)) != expected_confirmation:
        raise HTTPException(status_code=400, detail="requires_confirmation mismatch")


def envelope_fingerprint(envelope: Mapping[str, Any]) -> str:
    payload = {
        key: envelope.get(key)
        for key in ("schema", "action", "target", "args", "requested_by", "nonce")
    }
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


async def _run_action(action: str, args: Mapping[str, Any], proxmox: ProxmoxClient) -> CommandResultOut:
    node = args.get("node")
    if action == "host.health":
        data = await proxmox.get("/version")
        return CommandResultOut(
            ok=True,
            summary="Proxmox gateway healthy",
            detail={"proxmox": data, "gateway": "ok"},
        )
    if action == "node.list":
        data = await proxmox.get("/nodes")
        return CommandResultOut(ok=True, summary=f"{len(data)} node(s)", detail={"nodes": data})
    if action == "vm.list":
        data = await proxmox.get(f"/nodes/{node}/qemu")
        return CommandResultOut(ok=True, summary=f"{len(data)} VM(s)", detail={"vms": data})
    if action == "container.list":
        data = await proxmox.get(f"/nodes/{node}/lxc")
        return CommandResultOut(
            ok=True, summary=f"{len(data)} container(s)", detail={"containers": data}
        )
    if action == "vm.status":
        vmid = args["vmid"]
        data = await proxmox.get(f"/nodes/{node}/qemu/{vmid}/status/current")
        return CommandResultOut(
            ok=True,
            summary=f"VM {vmid} {data.get('status', 'unknown')}",
            detail={"status": data},
        )
    if action == "container.status":
        vmid = args["vmid"]
        data = await proxmox.get(f"/nodes/{node}/lxc/{vmid}/status/current")
        return CommandResultOut(
            ok=True,
            summary=f"Container {vmid} {data.get('status', 'unknown')}",
            detail={"status": data},
        )
    if action == "storage.summary":
        data = await proxmox.get(f"/nodes/{node}/storage")
        return CommandResultOut(
            ok=True, summary=f"{len(data)} storage pool(s)", detail={"storage": data}
        )
    if action == "task.history":
        data = await proxmox.get(f"/nodes/{node}/tasks")
        return CommandResultOut(ok=True, summary=f"{len(data)} task(s)", detail={"tasks": data})
    if action in {"vm.start", "vm.stop", "vm.restart"}:
        vmid = args["vmid"]
        op = action.split(".")[1]
        data = await proxmox.post(f"/nodes/{node}/qemu/{vmid}/status/{op}")
        return CommandResultOut(
            ok=True,
            summary=f"VM {vmid} {op} requested",
            detail={"result": data},
            task_id=str(data) if not isinstance(data, (dict, list)) else None,
        )
    if action in {"container.start", "container.stop", "container.restart"}:
        vmid = args["vmid"]
        op = action.split(".")[1]
        data = await proxmox.post(f"/nodes/{node}/lxc/{vmid}/status/{op}")
        return CommandResultOut(
            ok=True,
            summary=f"Container {vmid} {op} requested",
            detail={"result": data},
        )
    raise HTTPException(status_code=400, detail="Unknown command action")


def _stored_outcome(record: Mapping[str, Any]) -> ExecutionOutcome:
    payload = record.get("payload") or {}
    result_payload = payload.get("result") or {
        "ok": False,
        "summary": "Stored result is unavailable",
    }
    return ExecutionOutcome(
        status=str(payload.get("status", "completed")),
        result=CommandResultOut(**result_payload),
        expired=bool(payload.get("expired", False)),
        replayed=True,
    )


async def execute_command(
    *,
    envelope: Mapping[str, Any],
    approval: Optional[Mapping[str, Any]],
    proxmox: ProxmoxClient,
    mutations_enabled: bool,
    replay_guard: ReplayGuard,
    store: Any,
    step_up_verifier: Any,
) -> ExecutionOutcome:
    now_ms = int(time.time() * 1000)
    validate_envelope(envelope, now_ms, mutations_enabled)
    expires_at_ms = parse_timestamp(str(envelope["expires_at"]))
    if not replay_guard.nonce_check_and_store(
        str(envelope["nonce"]), expires_at_ms / 1000.0
    ):
        raise GatewayErrorFactory.replayed_request()

    action = str(envelope["action"])
    args = envelope.get("args") or {}
    command_id = str(envelope["command_id"])
    fingerprint = envelope_fingerprint(envelope)

    existing = store.idempotency_get(command_id)
    if existing is not None:
        if existing["fingerprint"] != fingerprint:
            raise GatewayErrorFactory.idempotency_conflict()
        if existing["state"] in {"complete", "failed"}:
            return _stored_outcome(existing)
        raise GatewayErrorFactory.duplicate_in_flight()

    if action in MUTATION_ACTIONS:
        if not getattr(step_up_verifier, "is_step_up", False) or not step_up_verifier.available():
            raise GatewayErrorFactory.step_up_unavailable()
        step_up_verifier.verify(approval, envelope)

    reservation = store.idempotency_reserve(
        command_id, fingerprint, IDEMPOTENCY_TTL_SECONDS
    )
    if reservation != "reserved":
        existing = store.idempotency_get(command_id)
        if existing is not None and existing["fingerprint"] == fingerprint and existing["state"] in {
            "complete",
            "failed",
        }:
            return _stored_outcome(existing)
        raise GatewayErrorFactory.duplicate_in_flight()

    try:
        result = await _run_action(action, args, proxmox)
        status_value = "completed"
    except ProxmoxError as exc:
        result = CommandResultOut(
            ok=False,
            summary=f"Proxmox error: {exc.message}",
            detail={"status_code": exc.status_code},
        )
        status_value = "failed"
    except HTTPException:
        store.idempotency_release(command_id)
        raise
    except Exception as exc:  # noqa: BLE001 - never double-execute an ambiguous mutation
        result = CommandResultOut(
            ok=False,
            summary="Gateway execution failed",
            detail={"error": type(exc).__name__},
        )
        status_value = "failed"

    payload = {
        "status": status_value,
        "result": result.model_dump(),
        "expired": False,
    }
    store.idempotency_complete(
        command_id, "complete" if result.ok else "failed", payload
    )
    return ExecutionOutcome(
        status=status_value, result=result, expired=False, replayed=False
    )


class GatewayErrorFactory:
    """Small factory so refusal codes stay consistent between modules."""

    @staticmethod
    def replayed_request():
        from .errors import GatewayError

        return GatewayError("replayed_request", "Replay detected: nonce already used", 409)

    @staticmethod
    def idempotency_conflict():
        from .errors import GatewayError

        return GatewayError(
            "idempotency_conflict",
            "This command_id was already used for different request content",
            409,
        )

    @staticmethod
    def duplicate_in_flight():
        from .errors import GatewayError

        return GatewayError(
            "duplicate_in_flight", "An identical request is already being processed", 409
        )

    @staticmethod
    def step_up_unavailable():
        from .errors import GatewayError

        return GatewayError(
            "step_up_unavailable",
            "Mutations require verified step-up authentication",
            403,
        )
