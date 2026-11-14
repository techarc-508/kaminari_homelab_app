"""Shared helpers for the gateway test suite.

Environment variables are set in `conftest.py` before `app` is imported.
"""
from __future__ import annotations

import time
import uuid

from app import totp
from app.catalogue import MUTATION_ACTIONS

GATEWAY_TOKEN = "test-gateway-token"
OWNER_TOKEN = "test-owner-token"


def auth_headers(token: str = GATEWAY_TOKEN) -> dict:
    return {"Authorization": f"Bearer {token}"}


def owner_headers() -> dict:
    return {"Authorization": f"Bearer {OWNER_TOKEN}"}


def iso_ms(ms: int) -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(ms / 1000)) + f".{ms % 1000:03d}Z"


def make_envelope(action: str = "host.health", args=None, schema: str = "kf.command.v1", **overrides):
    now = int(time.time() * 1000)
    envelope = {
        "schema": schema,
        "command_id": str(uuid.uuid4()),
        "nonce": (uuid.uuid4().hex + "A" * 16)[:43],
        "issued_at": iso_ms(now),
        "expires_at": iso_ms(now + 300_000),
        "requested_by": "android:profile-1",
        "target": "proxmox-gateway",
        "action": action,
        "args": args or {},
        "reply_topic": "kf-command-result",
        "requires_confirmation": action in MUTATION_ACTIONS,
    }
    envelope.update(overrides)
    return envelope


class Clock:
    """Deterministic clock so TOTP steps can be advanced without sleeping."""

    def __init__(self, start: float = 1_800_000_000.0):
        self.now = float(start)

    def __call__(self) -> float:
        return self.now

    def advance(self, seconds: float = 30.0) -> None:
        self.now += float(seconds)


def current_code(secret: str, clock: Clock) -> str:
    return totp.code_for_step(secret, totp.step_for(clock()))
