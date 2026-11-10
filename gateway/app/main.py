import logging
import time
from typing import Any, Dict

from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials

from .auth import RateLimiter, security, verify_gateway_token
from .commands import (
    ReplayCache,
    execute_command,
    COMMAND_TARGET,
)
from .config import Settings
from .models import CommandExecuteRequest, ErrorResponse
from .proxmox import ProxmoxClient

settings = Settings()
logger = logging.getLogger("kaminari-gateway")
logging.basicConfig(level=logging.INFO, format="%(message)s")

rate_limiter = RateLimiter(settings.rate_limit_per_minute)
replay_cache = ReplayCache()
proxmox = ProxmoxClient(
    base_url=str(settings.proxmox_base_url),
    api_token=settings.proxmox_api_token,
    verify_tls=settings.proxmox_verify_tls,
    timeout_seconds=settings.proxmox_timeout_seconds,
)

app = FastAPI(title="Kaminari Proxmox Gateway", version="1.0.0")


@app.middleware("http")
async def limit_request_size(request: Request, call_next):
    content_length = request.headers.get("content-length")
    if content_length and int(content_length) > settings.max_request_bytes:
        return JSONResponse(status_code=413, content={"error": "Request too large", "code": "request_too_large"})
    return await call_next(request)


def audit(event: str, **fields: Any) -> None:
    safe = {k: v for k, v in fields.items() if "token" not in k.lower() and "secret" not in k.lower()}
    logger.info({"event": event, **safe})


async def authenticate(credentials: HTTPAuthorizationCredentials = Depends(security)) -> str:
    if not verify_gateway_token(credentials.credentials, settings.gateway_token):
        audit("auth_failed")
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid gateway token")
    client_key = credentials.credentials[:8]
    if not rate_limiter.check(client_key):
        raise HTTPException(status_code=429, detail="Rate limit exceeded")
    return credentials.credentials


@app.get("/healthz")
async def healthz() -> dict:
    return {"status": "ok"}


@app.get("/readyz")
async def readyz(token: str = Depends(authenticate)) -> dict:
    try:
        await proxmox.get("/version")
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=503, detail="Proxmox not ready")
    return {"status": "ready"}


@app.get("/v1/proxmox/nodes")
async def nodes(token: str = Depends(authenticate)) -> Any:
    return await proxmox.get("/nodes")


@app.get("/v1/proxmox/qemu")
async def qemu(node: str, token: str = Depends(authenticate)) -> Any:
    return await proxmox.get(f"/nodes/{node}/qemu")


@app.get("/v1/proxmox/lxc")
async def lxc(node: str, token: str = Depends(authenticate)) -> Any:
    return await proxmox.get(f"/nodes/{node}/lxc")


@app.get("/v1/proxmox/status")
async def status(node: str, kind: str, vmid: int, token: str = Depends(authenticate)) -> Any:
    if kind not in {"qemu", "lxc"}:
        raise HTTPException(status_code=400, detail="kind must be qemu or lxc")
    return await proxmox.get(f"/nodes/{node}/{kind}/{vmid}/status/current")


@app.get("/v1/proxmox/storage")
async def storage(node: str, token: str = Depends(authenticate)) -> Any:
    return await proxmox.get(f"/nodes/{node}/storage")


@app.get("/v1/proxmox/tasks")
async def tasks(node: str, token: str = Depends(authenticate)) -> Any:
    return await proxmox.get(f"/nodes/{node}/tasks")


@app.post("/v1/commands/execute")
async def execute(payload: CommandExecuteRequest, token: str = Depends(authenticate)) -> dict:
    envelope = payload.envelope.model_dump(by_alias=True)
    audit("command_received", action=envelope.get("action"), command_id=envelope.get("command_id"))
    status_value, result, expired = await execute_command(
        envelope=envelope,
        approval_token=payload.approval_token,
        proxmox=proxmox,
        mutations_enabled=settings.mutations_enabled,
        replay_cache=replay_cache,
    )
    audit("command_completed", action=envelope.get("action"), status=status_value, ok=result.ok)
    return {
        "status": status_value,
        "result": result.model_dump(),
        "expired": expired,
    }
