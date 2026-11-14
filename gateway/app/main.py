import hashlib
import json
import logging
import uuid
from typing import Any

from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials

from .audit import SqliteAuditSink, new_audit_event
from .auth import RateLimiter, security, token_expired, verify_gateway_token
from .catalogue import ADMIN_IDENTITIES, COMMAND_TARGET, authorize_action
from .commands import execute_command, validate_args
from .config import Settings
from .errors import GatewayError
from .idempotency import SqliteIdempotencyStore
from .models import (
    CommandExecuteRequest,
    StepUpChallengeRequest,
    StepUpConfirmRequest,
)
from .proxmox import ProxmoxClient
from .stepup import TotpStepUpService
from .store import SqliteStore

settings = Settings()
settings.check_token_separation()

logger = logging.getLogger("kaminari-gateway")
logging.basicConfig(level=logging.INFO, format="%(message)s")

store = SqliteStore(settings.db_path)
audit_sink = SqliteAuditSink(store)
idempotency_store = SqliteIdempotencyStore(store)
step_up_service = TotpStepUpService(
    store,
    challenge_ttl_seconds=settings.step_up_challenge_ttl_seconds,
    window_steps=settings.step_up_window_steps,
    max_attempts=settings.step_up_max_attempts,
    account=settings.owner_identity,
)
rate_limiter = RateLimiter(settings.rate_limit_per_minute)
proxmox = ProxmoxClient(
    base_url=str(settings.proxmox_base_url),
    api_token=settings.proxmox_api_token,
    verify_tls=settings.proxmox_verify_tls,
    timeout_seconds=settings.proxmox_timeout_seconds,
)

app = FastAPI(title="Kaminari Proxmox Gateway", version="2.0.0")


@app.exception_handler(GatewayError)
async def gateway_error_handler(request: Request, exc: GatewayError) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={"error": exc.message, "code": exc.code},
    )


@app.exception_handler(HTTPException)
async def http_error_handler(request: Request, exc: HTTPException) -> JSONResponse:
    code = {
        400: "invalid_request",
        401: "unauthenticated",
        403: "forbidden",
        404: "not_found",
        409: "conflict",
        413: "request_too_large",
        422: "invalid_request",
        429: "rate_limited",
        503: "not_ready",
    }.get(exc.status_code, "request_failed")
    return JSONResponse(
        status_code=exc.status_code,
        content={"error": str(exc.detail), "code": code},
    )


@app.exception_handler(RequestValidationError)
async def validation_error_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    return JSONResponse(
        status_code=422,
        content={"error": "Request validation failed", "code": "invalid_request"},
    )


@app.middleware("http")
async def limit_request_size(request: Request, call_next):
    content_length = request.headers.get("content-length")
    if content_length and int(content_length) > settings.max_request_bytes:
        return JSONResponse(
            status_code=413,
            content={"error": "Request too large", "code": "request_too_large"},
        )
    return await call_next(request)


def audit_log(event: str, **fields: Any) -> None:
    """Structured log line. Secrets, TOTP codes and tokens are never logged."""
    blocked = ("token", "secret", "code", "password", "authorization")
    safe = {
        key: value
        for key, value in fields.items()
        if not any(fragment in key.lower() for fragment in blocked)
    }
    logger.info(json.dumps({"event": event, **safe}, default=str))


def record_audit(event: Any) -> None:
    """Persist an audit event, failing closed if the store is unavailable."""
    try:
        audit_sink.append(event)
    except Exception as exc:  # noqa: BLE001
        logger.error(
            json.dumps({"event": "audit_store_failure", "error": type(exc).__name__})
        )
        raise HTTPException(status_code=503, detail="Audit store unavailable")


async def authenticate(
    credentials: HTTPAuthorizationCredentials = Depends(security),
) -> str:
    if token_expired(settings.token_expires_at):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Gateway token has expired"
        )
    if not verify_gateway_token(
        credentials.credentials,
        settings.gateway_token,
        settings.previous_gateway_token,
    ):
        audit_log("auth_failed")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid gateway token"
        )
    client_key = hashlib.sha256(credentials.credentials.encode("utf-8")).hexdigest()[:16]
    if not rate_limiter.check(client_key):
        audit_log("rate_limited")
        raise HTTPException(status_code=429, detail="Rate limit exceeded")
    return credentials.credentials


async def authenticate_owner(
    credentials: HTTPAuthorizationCredentials = Depends(security),
) -> str:
    if not settings.owner_token:
        raise GatewayError(
            "owner_not_configured",
            "Owner credential is not configured on this gateway",
            503,
        )
    if settings.owner_identity not in ADMIN_IDENTITIES:
        raise GatewayError(
            "owner_identity_not_admin",
            "Configured owner identity is not an administrator",
            503,
        )
    if token_expired(settings.owner_token_expires_at):
        raise HTTPException(status_code=401, detail="Owner token has expired")
    if not verify_gateway_token(
        credentials.credentials, settings.owner_token, settings.previous_owner_token
    ):
        audit_log("owner_auth_failed")
        raise HTTPException(status_code=401, detail="Invalid owner token")
    return credentials.credentials


@app.get("/healthz")
async def healthz() -> dict:
    return {"status": "ok"}


@app.get("/readyz")
async def readyz() -> dict:
    try:
        store.health()
    except Exception:  # noqa: BLE001
        raise HTTPException(status_code=503, detail="State store unavailable")
    return {
        "status": "ready",
        "durable_state": True,
        "mutations_enabled": settings.mutations_enabled,
        "step_up_enrolled": step_up_service.status()["enrolled"],
    }


@app.get("/v1/stepup/status")
async def stepup_status(token: str = Depends(authenticate)) -> dict:
    return {
        **step_up_service.status(),
        "mutations_enabled": settings.mutations_enabled,
    }


@app.post("/v1/stepup/enroll/start")
async def stepup_enroll_start(token: str = Depends(authenticate_owner)) -> dict:
    info = step_up_service.start_enrollment()
    record_audit(
        new_audit_event(
            actor=settings.owner_identity,
            operation_id="stepup.enroll.start",
            target=COMMAND_TARGET,
            risk="L2",
            outcome="COMPLETED",
        )
    )
    audit_log("stepup_enroll_started", actor=settings.owner_identity)
    return info


@app.post("/v1/stepup/enroll/confirm")
async def stepup_enroll_confirm(
    payload: StepUpConfirmRequest, token: str = Depends(authenticate_owner)
) -> dict:
    try:
        step_up_service.confirm_enrollment(payload.code)
    except GatewayError as exc:
        record_audit(
            new_audit_event(
                actor=settings.owner_identity,
                operation_id="stepup.enroll.confirm",
                target=COMMAND_TARGET,
                risk="L2",
                outcome="REFUSED",
                details={"code": exc.code},
            )
        )
        raise
    record_audit(
        new_audit_event(
            actor=settings.owner_identity,
            operation_id="stepup.enroll.confirm",
            target=COMMAND_TARGET,
            risk="L2",
            outcome="COMPLETED",
        )
    )
    audit_log("stepup_enrolled", actor=settings.owner_identity)
    return {"enrolled": True}


@app.post("/v1/stepup/challenge")
async def stepup_challenge(
    payload: StepUpChallengeRequest, token: str = Depends(authenticate)
) -> dict:
    identity = settings.gateway_identity
    spec = authorize_action(identity, payload.action)
    if not spec.mutation or not spec.step_up:
        raise GatewayError(
            "step_up_not_required",
            "Step-up is only issued for mutating actions",
            400,
        )
    if payload.target != COMMAND_TARGET:
        raise GatewayError("invalid_target", "Invalid command target", 400)
    try:
        uuid.UUID(payload.command_id)
    except (ValueError, AttributeError):
        raise GatewayError("invalid_command_id", "command_id must be a UUID", 400)
    validate_args(spec, payload.args)
    record = step_up_service.create_challenge(
        request_id=payload.command_id,
        action=payload.action,
        target=payload.target,
        args=payload.args,
    )
    record_audit(
        new_audit_event(
            actor=identity,
            operation_id="stepup.challenge",
            target=payload.target,
            risk=spec.risk,
            outcome="COMPLETED",
            correlation_id=payload.command_id,
            details={"action": payload.action},
        )
    )
    return record


@app.post("/v1/commands/execute")
async def execute(
    payload: CommandExecuteRequest, token: str = Depends(authenticate)
) -> dict:
    envelope = payload.envelope.model_dump(by_alias=True)
    identity = settings.gateway_identity
    action_id = str(envelope.get("action", ""))
    spec = authorize_action(identity, action_id)
    command_id = envelope.get("command_id")
    target = str(envelope.get("target") or "proxmox-gateway")
    approval = payload.approval.model_dump() if payload.approval else None

    record_audit(
        new_audit_event(
            actor=identity,
            operation_id=action_id,
            target=target,
            risk=spec.risk,
            outcome="RECEIVED",
            correlation_id=command_id,
            details={"phase": "received"},
        )
    )

    try:
        outcome = await execute_command(
            envelope=envelope,
            approval=approval,
            proxmox=proxmox,
            mutations_enabled=settings.mutations_enabled,
            replay_guard=store,
            store=store,
            step_up_verifier=step_up_service,
        )
    except GatewayError as exc:
        record_audit(
            new_audit_event(
                actor=identity,
                operation_id=action_id,
                target=target,
                risk=spec.risk,
                outcome="REFUSED",
                correlation_id=command_id,
                details={"code": exc.code},
            )
        )
        audit_log("command_refused", action=action_id, code=exc.code)
        raise
    except HTTPException as exc:
        record_audit(
            new_audit_event(
                actor=identity,
                operation_id=action_id,
                target=target,
                risk=spec.risk,
                outcome="REFUSED",
                correlation_id=command_id,
                details={"status": str(exc.status_code)},
            )
        )
        audit_log("command_refused", action=action_id, status=exc.status_code)
        raise

    completed = new_audit_event(
        actor=identity,
        operation_id=action_id,
        target=target,
        risk=spec.risk,
        outcome="COMPLETED" if outcome.result.ok else "FAILED",
        correlation_id=command_id,
        details={
            "phase": "completed",
            "status": outcome.status,
            "replayed": str(outcome.replayed).lower(),
        },
    )
    record_audit(completed)
    audit_log(
        "command_completed",
        action=action_id,
        status=outcome.status,
        ok=outcome.result.ok,
        replayed=outcome.replayed,
        command_id=command_id,
        audit_id=completed.audit_id,
    )
    return {
        "status": outcome.status,
        "result": outcome.result.model_dump(),
        "expired": outcome.expired,
        "replayed": outcome.replayed,
        "correlation_id": command_id,
        "audit_id": completed.audit_id,
        "identity": identity,
    }


@app.get("/v1/audit/recent")
async def audit_recent(limit: int = 20, token: str = Depends(authenticate_owner)) -> dict:
    return {"events": store.list_audit(limit)}
