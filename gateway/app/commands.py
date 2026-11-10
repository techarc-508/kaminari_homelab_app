import re
import time
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, Optional, Set

from fastapi import HTTPException

from .models import CommandResultOut
from .proxmox import ProxmoxClient, ProxmoxError

COMMAND_TARGET = "proxmox-gateway"
COMMAND_SCHEMA = "rplab.command.v1"
COMMAND_EXPIRY_MS = 5 * 60 * 1000
COMMAND_CLOCK_SKEW_MS = 60 * 1000

NODE_PATTERN = re.compile(r"^[a-z0-9][a-z0-9_-]{0,31}$")
VMID_PATTERN = re.compile(r"^[0-9]{1,10}$")
RESOURCE_NAME = re.compile(r"^[a-z0-9][a-z0-9_-]{0,31}$")

READ_ACTIONS = {
    "host.health",
    "node.list",
    "vm.list",
    "container.list",
    "vm.status",
    "container.status",
    "storage.summary",
    "task.history",
}
MUTATION_ACTIONS = {
    "vm.start",
    "vm.stop",
    "vm.restart",
    "container.start",
    "container.stop",
    "container.restart",
}
ALL_ACTIONS = READ_ACTIONS | MUTATION_ACTIONS


class ReplayCache:
    def __init__(self):
        self._seen: dict[str, float] = {}

    def check_and_store(self, nonce: str, expires_at_ms: int) -> bool:
        now_ms = int(time.time() * 1000)
        self._seen = {k: v for k, v in self._seen.items() if v > now_ms}
        if nonce in self._seen:
            return False
        self._seen[nonce] = expires_at_ms
        return True


def parse_timestamp(value: str) -> int:
    try:
        # Accept ISO-8601 UTC with millisecond precision.
        if value.endswith("Z"):
            value = value[:-1] + "+00:00"
        dt = datetime.fromisoformat(value)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return int(dt.timestamp() * 1000)
    except (ValueError, TypeError, OSError):
        raise HTTPException(status_code=400, detail="Invalid timestamp")


def validate_envelope(envelope: dict, now_ms: int, mutations_enabled: bool) -> None:
    if envelope.get("schema") != COMMAND_SCHEMA:
        raise HTTPException(status_code=400, detail="Unknown command schema")
    try:
        uuid.UUID(envelope.get("command_id", ""))
    except (ValueError, AttributeError):
        raise HTTPException(status_code=400, detail="command_id must be a UUID")
    nonce = envelope.get("nonce", "")
    if not re.fullmatch(r"[A-Za-z0-9_-]{43}", nonce):
        raise HTTPException(status_code=400, detail="Invalid nonce")
    issued_at = parse_timestamp(envelope.get("issued_at", ""))
    expires_at = parse_timestamp(envelope.get("expires_at", ""))
    if expires_at <= issued_at:
        raise HTTPException(status_code=400, detail="expires_at must be after issued_at")
    if expires_at - issued_at > COMMAND_EXPIRY_MS:
        raise HTTPException(status_code=400, detail="command window cannot exceed five minutes")
    if abs(now_ms - issued_at) > COMMAND_CLOCK_SKEW_MS:
        raise HTTPException(status_code=400, detail="issued_at is outside the allowed skew")
    if now_ms >= expires_at:
        raise HTTPException(status_code=400, detail="Command request has expired")
    if envelope.get("target") != COMMAND_TARGET:
        raise HTTPException(status_code=400, detail="Invalid command target")
    action = envelope.get("action")
    if action not in ALL_ACTIONS:
        raise HTTPException(status_code=400, detail="Unknown command action")
    if action in MUTATION_ACTIONS and not mutations_enabled:
        raise HTTPException(status_code=403, detail="Mutations are disabled on this gateway")
    requested_by = envelope.get("requested_by", "")
    if not re.fullmatch(r"android:[A-Za-z0-9_-]{1,56}", requested_by):
        raise HTTPException(status_code=400, detail="Invalid requested_by")
    args = envelope.get("args", {})
    if not isinstance(args, dict):
        raise HTTPException(status_code=400, detail="Invalid args")
    # Basic arg validation.
    if action in {"vm.list", "container.list", "task.history"}:
        node = args.get("node", "")
        if not NODE_PATTERN.fullmatch(node):
            raise HTTPException(status_code=400, detail="Invalid node")
    if action in {"vm.status", "container.status"} | MUTATION_ACTIONS:
        node = args.get("node", "")
        vmid = args.get("vmid", "")
        if not NODE_PATTERN.fullmatch(node):
            raise HTTPException(status_code=400, detail="Invalid node")
        if not VMID_PATTERN.fullmatch(vmid):
            raise HTTPException(status_code=400, detail="Invalid vmid")
    if action in {"vm.list", "container.list"}:
        pass
    required_confirmation = envelope.get("requires_confirmation", False)
    expected_confirmation = action in MUTATION_ACTIONS
    if required_confirmation != expected_confirmation:
        raise HTTPException(status_code=400, detail="requires_confirmation mismatch")
    # Confirmation binding for mutations is enforced by caller using approval token.


async def execute_command(
    envelope: dict,
    approval_token: Optional[str],
    proxmox: ProxmoxClient,
    mutations_enabled: bool,
    replay_cache: ReplayCache,
) -> tuple[str, CommandResultOut, bool]:
    now_ms = int(time.time() * 1000)
    validate_envelope(envelope, now_ms, mutations_enabled)
    expires_at = parse_timestamp(envelope["expires_at"])
    nonce = envelope["nonce"]
    if not replay_cache.check_and_store(nonce, expires_at):
        raise HTTPException(status_code=409, detail="Replay detected: nonce already used")
    action = envelope["action"]
    args = envelope.get("args", {})
    node = args.get("node")
    is_mutation = action in MUTATION_ACTIONS
    if is_mutation:
        if not approval_token or approval_token != nonce:
            raise HTTPException(status_code=403, detail="Missing or invalid approval token")
    try:
        if action == "host.health":
            data = await proxmox.get("/version")
            result = CommandResultOut(
                ok=True,
                summary="Proxmox gateway healthy",
                detail={"proxmox": data, "gateway": "ok"},
            )
        elif action == "node.list":
            data = await proxmox.get("/nodes")
            result = CommandResultOut(ok=True, summary=f"{len(data)} node(s)", detail={"nodes": data})
        elif action == "vm.list":
            data = await proxmox.get(f"/nodes/{node}/qemu")
            result = CommandResultOut(ok=True, summary=f"{len(data)} VM(s)", detail={"vms": data})
        elif action == "container.list":
            data = await proxmox.get(f"/nodes/{node}/lxc")
            result = CommandResultOut(ok=True, summary=f"{len(data)} container(s)", detail={"containers": data})
        elif action == "vm.status":
            vmid = args["vmid"]
            data = await proxmox.get(f"/nodes/{node}/qemu/{vmid}/status/current")
            result = CommandResultOut(ok=True, summary=f"VM {vmid} {data.get('status', 'unknown')}", detail={"status": data})
        elif action == "container.status":
            vmid = args["vmid"]
            data = await proxmox.get(f"/nodes/{node}/lxc/{vmid}/status/current")
            result = CommandResultOut(ok=True, summary=f"Container {vmid} {data.get('status', 'unknown')}", detail={"status": data})
        elif action == "storage.summary":
            data = await proxmox.get(f"/nodes/{node}/storage")
            result = CommandResultOut(ok=True, summary=f"{len(data)} storage pool(s)", detail={"storage": data})
        elif action == "task.history":
            data = await proxmox.get(f"/nodes/{node}/tasks")
            result = CommandResultOut(ok=True, summary=f"{len(data)} task(s)", detail={"tasks": data})
        elif action in {"vm.start", "vm.stop", "vm.restart"}:
            vmid = args["vmid"]
            op = action.split(".")[1]
            data = await proxmox.post(f"/nodes/{node}/qemu/{vmid}/status/{op}")
            result = CommandResultOut(ok=True, summary=f"VM {vmid} {op} requested", detail={"result": data}, task_id=str(data) if not isinstance(data, (dict, list)) else None)
        elif action in {"container.start", "container.stop", "container.restart"}:
            vmid = args["vmid"]
            op = action.split(".")[1]
            data = await proxmox.post(f"/nodes/{node}/lxc/{vmid}/status/{op}")
            result = CommandResultOut(ok=True, summary=f"Container {vmid} {op} requested", detail={"result": data})
        else:
            raise HTTPException(status_code=400, detail="Unknown command action")
    except ProxmoxError as exc:
        return "failed", CommandResultOut(ok=False, summary=f"Proxmox error: {exc.message}", detail={"status_code": exc.status_code}), False
    except Exception as exc:  # noqa: BLE001
        return "failed", CommandResultOut(ok=False, summary="Gateway execution failed", detail={"error": str(exc)}), False
    status = "completed"
    return status, result, False
