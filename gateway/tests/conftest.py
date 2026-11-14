"""Gateway test configuration.

The environment is configured *before* `app.main` is imported so the settings
object, durable store and step-up service are built against a throwaway
database for the whole session.
"""
from __future__ import annotations

import os
import tempfile
from pathlib import Path

import pytest

_TMP = Path(tempfile.mkdtemp(prefix="kf-gateway-tests-"))

os.environ["GATEWAY_GATEWAY_TOKEN"] = "test-gateway-token"
os.environ["GATEWAY_OWNER_TOKEN"] = "test-owner-token"
os.environ["GATEWAY_MUTATIONS_ENABLED"] = "true"
os.environ["GATEWAY_DB_PATH"] = str(_TMP / "gateway.db")
os.environ.setdefault("GATEWAY_PROXMOX_BASE_URL", "https://proxmox.example:8006/api2/json")
os.environ.setdefault("GATEWAY_PROXMOX_API_TOKEN", "root@pam!token=secret")

from fastapi.testclient import TestClient  # noqa: E402

from app import totp  # noqa: E402
from app.main import (  # noqa: E402
    app,
    proxmox,
    rate_limiter,
    settings,
    step_up_service,
    store,
)
from support import Clock, auth_headers, owner_headers  # noqa: E402


@pytest.fixture(autouse=True)
def isolated_state():
    """Reset durable state and pin a deterministic step-up clock per test."""
    store.reset_all_state()
    rate_limiter._events.clear()
    clock = Clock()
    original_clock = step_up_service.clock
    step_up_service.clock = clock
    original = (
        settings.gateway_identity,
        settings.mutations_enabled,
        settings.token_expires_at,
        settings.owner_token_expires_at,
        settings.previous_gateway_token,
        settings.owner_token,
    )
    yield clock
    step_up_service.clock = original_clock
    (
        settings.gateway_identity,
        settings.mutations_enabled,
        settings.token_expires_at,
        settings.owner_token_expires_at,
        settings.previous_gateway_token,
        settings.owner_token,
    ) = original


@pytest.fixture
def client(monkeypatch):
    async def async_get(path, params=None):
        if path == "/version":
            return {"version": "8.1"}
        if path == "/nodes":
            return [{"node": "pve", "status": "online"}]
        if path.endswith("/qemu"):
            return [{"vmid": 100, "name": "vm", "status": "running"}]
        if path.endswith("/lxc"):
            return [{"vmid": 200, "name": "ct", "status": "running"}]
        if path.endswith("/status/current"):
            return {"status": "running", "vmid": 100}
        if path.endswith("/storage"):
            return [{"storage": "local", "type": "dir"}]
        if path.endswith("/tasks"):
            return [{"upid": "UPID:task", "status": "OK"}]
        return {}

    async def async_post(path, params=None):
        return "UPID:started"

    monkeypatch.setattr(proxmox, "get", async_get)
    monkeypatch.setattr(proxmox, "post", async_post)
    return TestClient(app)


class Approver:
    def __init__(self, client: TestClient, secret: str, clock: Clock):
        self.client = client
        self.secret = secret
        self.clock = clock

    def challenge(self, envelope: dict) -> str:
        response = self.client.post(
            "/v1/stepup/challenge",
            headers=auth_headers(),
            json={
                "command_id": envelope["command_id"],
                "action": envelope["action"],
                "target": envelope["target"],
                "args": envelope["args"],
            },
        )
        assert response.status_code == 200, response.text
        return response.json()["challenge_id"]

    def approval(self, envelope: dict) -> dict:
        self.clock.advance(30)
        challenge_id = self.challenge(envelope)
        code = totp.code_for_step(self.secret, totp.step_for(self.clock()))
        return {"challenge_id": challenge_id, "code": code}


@pytest.fixture
def enrolled(client, isolated_state):
    """Enroll TOTP and expose a helper that issues bound approvals."""
    start = client.post("/v1/stepup/enroll/start", headers=owner_headers())
    assert start.status_code == 200, start.text
    secret = start.json()["secret"]
    code = totp.code_for_step(secret, totp.step_for(isolated_state()))
    confirm = client.post(
        "/v1/stepup/enroll/confirm", headers=owner_headers(), json={"code": code}
    )
    assert confirm.status_code == 200, confirm.text
    return Approver(client, secret, isolated_state)
