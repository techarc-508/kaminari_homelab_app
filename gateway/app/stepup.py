"""Server-verified TOTP step-up authentication.

The approval is **not** a nonce echo and **not** a self-asserted flag. A
short-lived one-time challenge is bound to the exact command id, action,
target and arguments, and it can only be consumed with a currently valid TOTP
code from the enrolled secret. Changing the action, target, args or command id
after approval invalidates it.

A TOTP step that has already authorised a command cannot authorise another
one, so a captured code cannot be replayed inside its own time window.
"""
from __future__ import annotations

import hashlib
import json
import time
import uuid
from typing import Any, Callable, Dict, Mapping, Optional

from . import totp
from .errors import GatewayError
from .store import SqliteStore

CHALLENGE_TTL_SECONDS = 60
MAX_ATTEMPTS = 5
WINDOW_STEPS = 1


def args_hash(args: Mapping[str, Any]) -> str:
    canonical = json.dumps({k: str(v) for k, v in args.items()}, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def binding_hash(request_id: str, action: str, target: str, args: Mapping[str, Any]) -> str:
    canonical = json.dumps(
        {
            "request_id": request_id,
            "action": action,
            "target": target,
            "args_hash": args_hash(args),
        },
        sort_keys=True,
        separators=(",", ":"),
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


class TotpStepUpService:
    """Real step-up verification backed by the durable store."""

    is_step_up = True

    def __init__(
        self,
        store: SqliteStore,
        *,
        clock: Callable[[], float] = time.time,
        challenge_ttl_seconds: int = CHALLENGE_TTL_SECONDS,
        window_steps: int = WINDOW_STEPS,
        max_attempts: int = MAX_ATTEMPTS,
        issuer: str = "Kaminari",
        account: str = "kfadmin",
    ):
        self.store = store
        self.clock = clock
        self.challenge_ttl_seconds = challenge_ttl_seconds
        self.window_steps = window_steps
        self.max_attempts = max_attempts
        self.issuer = issuer
        self.account = account

    # ------------------------------------------------------------------ status

    def available(self) -> bool:
        enrollment = self.store.enrollment_get()
        return bool(enrollment and enrollment["verified_at"])

    def status(self) -> Dict[str, Any]:
        enrollment = self.store.enrollment_get()
        verified = bool(enrollment and enrollment["verified_at"])
        return {
            "enrolled": verified,
            "pending_enrollment": bool(enrollment and not enrollment["verified_at"]),
            "step_up": True,
            "challenge_ttl_seconds": self.challenge_ttl_seconds,
        }

    # -------------------------------------------------------------- enrollment

    def start_enrollment(self) -> Dict[str, Any]:
        """Create a new TOTP secret. The secret is returned exactly once."""
        existing = self.store.enrollment_get()
        if existing and existing["verified_at"]:
            raise GatewayError(
                "already_enrolled",
                "Step-up is already enrolled; reset the enrollment to rotate it",
                409,
            )
        if existing:
            raise GatewayError(
                "enrollment_pending",
                "A pending enrollment exists; complete it before starting another",
                409,
            )
        secret = totp.generate_secret()
        self.store.enrollment_create(secret, totp.now_iso(self.clock()))
        return {
            "secret": secret,
            "otpauth_uri": totp.provisioning_uri(
                secret, account=self.account, issuer=self.issuer
            ),
            "requires_confirmation": True,
            "digits": totp.DIGITS,
            "period_seconds": totp.PERIOD_SECONDS,
        }

    def confirm_enrollment(self, code: str) -> None:
        enrollment = self.store.enrollment_get()
        if enrollment is None:
            raise GatewayError("not_enrolled", "No step-up enrollment is in progress", 409)
        if enrollment["verified_at"]:
            raise GatewayError("already_enrolled", "Step-up is already enrolled", 409)
        step = totp.verify(
            enrollment["secret_b32"],
            code,
            self.clock(),
            window_steps=self.window_steps,
            digits=totp.DIGITS,
        )
        if step is None:
            raise GatewayError("invalid_totp", "Invalid step-up code", 403)
        # The enrollment code is consumed: it can never authorise a command.
        self.store.enrollment_mark_verified(totp.now_iso(self.clock()), step)

    # -------------------------------------------------------------- challenges

    def create_challenge(
        self,
        *,
        request_id: str,
        action: str,
        target: str,
        args: Mapping[str, Any],
    ) -> Dict[str, Any]:
        if not self.available():
            raise GatewayError(
                "step_up_unavailable",
                "Step-up authentication is not enrolled on this gateway",
                409,
            )
        record = self.store.challenge_create(
            challenge_id=str(uuid.uuid4()),
            request_id=request_id,
            action=action,
            target=target,
            args_hash=args_hash(args),
            binding_hash=binding_hash(request_id, action, target, args),
            ttl_seconds=self.challenge_ttl_seconds,
            now=self.clock(),
        )
        record["expires_at"] = totp.now_iso(record["expires_at"])
        return record

    # -------------------------------------------------------------- verification

    def verify(self, approval: Optional[Mapping[str, Any]], envelope: Mapping[str, Any]) -> None:
        """Consume an approval bound to this exact request, or raise.

        Raises `GatewayError` with a machine-readable code for every refusal.
        """
        if not self.available():
            raise GatewayError(
                "step_up_unavailable",
                "Step-up authentication is not enrolled on this gateway",
                403,
            )
        if not approval:
            raise GatewayError("approval_required", "Step-up approval is required", 403)
        challenge_id = str(approval.get("challenge_id") or "")
        code = str(approval.get("code") or "")
        if not challenge_id or not code:
            raise GatewayError(
                "invalid_approval", "Approval requires challenge_id and code", 400
            )

        challenge = self.store.challenge_get(challenge_id)
        if challenge is None:
            raise GatewayError("invalid_approval", "Unknown step-up challenge", 403)
        if challenge["consumed_at"] is not None:
            raise GatewayError(
                "approval_already_used", "This step-up approval was already used", 409
            )
        now = self.clock()
        if now >= challenge["expires_at"]:
            raise GatewayError("approval_expired", "Step-up approval has expired", 403)
        if challenge["attempts"] >= self.max_attempts:
            raise GatewayError(
                "approval_locked", "Too many failed step-up attempts", 429
            )

        expected = binding_hash(
            str(envelope.get("command_id") or ""),
            str(envelope.get("action") or ""),
            str(envelope.get("target") or ""),
            envelope.get("args") or {},
        )
        if not (
            challenge["request_id"] == str(envelope.get("command_id") or "")
            and challenge["action"] == str(envelope.get("action") or "")
            and challenge["target"] == str(envelope.get("target") or "")
            and challenge["binding_hash"] == expected
        ):
            # A mismatch is treated as hostile: burn the challenge.
            self.store.challenge_invalidate(challenge_id, now)
            raise GatewayError(
                "approval_binding_mismatch",
                "Approval does not match this command's action, target or arguments",
                403,
            )

        enrollment = self.store.enrollment_get()
        if enrollment is None:
            raise GatewayError(
                "step_up_unavailable", "Step-up authentication is not enrolled", 403
            )
        step = totp.verify(
            enrollment["secret_b32"],
            code,
            now,
            window_steps=self.window_steps,
            digits=totp.DIGITS,
        )
        if step is None:
            attempts = self.store.challenge_increment_attempts(challenge_id)
            if attempts >= self.max_attempts:
                self.store.challenge_invalidate(challenge_id, now)
            raise GatewayError("invalid_totp", "Invalid step-up code", 403)

        last_step = enrollment["last_used_step"]
        if last_step is not None and step <= int(last_step):
            raise GatewayError(
                "totp_replayed",
                "This step-up code has already authorised a command",
                409,
            )

        if not self.store.challenge_consume(challenge_id, now):
            raise GatewayError(
                "approval_already_used", "This step-up approval was already used", 409
            )
        self.store.enrollment_set_last_step(step)
