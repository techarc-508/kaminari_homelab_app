import os
import time
import uuid

import httpx
import pytest
from fastapi.testclient import TestClient

os.environ.setdefault("GATEWAY_GATEWAY_TOKEN", "test-gateway-token")
os.environ.setdefault("GATEWAY_PROXMOX_BASE_URL", "https://proxmox.example:8006/api2/json")
os.environ.setdefault("GATEWAY_PROXMOX_API_TOKEN", "root@pam!token=secret")
os.environ.setdefault("GATEWAY_MUTATIONS_ENABLED", "true")

from app.main import app, proxmox, replay_cache
from app.commands import parse_timestamp
from datetime import datetime, timezone


def _ts(ms_offset=0):
    dt = datetime.fromtimestamp((time.time() * 1000 + ms_offset) / 1000, tz=timezone.utc)
    return dt.strftime("%Y-%m-%dT%H:%M:%S.") + f"{int((time.time()*1000)%1000):03d}Z"


@pytest.fixture(autouse=True)
def reset_replay():
    replay_cache._seen.clear()
    yield


@pytest.fixture
def client(monkeypatch):
    def fake_get(path, params=None):
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

    async def async_get(path, params=None):
        return fake_get(path, params)

    async def async_post(path, params=None):
        return "UPID:started"

    monkeypatch.setattr(proxmox, "get", async_get)
    monkeypatch.setattr(proxmox, "post", async_post)
    return TestClient(app)


def auth_headers():
    return {"Authorization": "Bearer test-gateway-token"}


def make_envelope(action="host.health", args=None, mutations=False):
    now = int(time.time() * 1000)
    issued = datetime.fromtimestamp(now / 1000, tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.") + f"{now % 1000:03d}Z"
    expires = datetime.fromtimestamp((now + 300000) / 1000, tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.") + f"{(now + 300000) % 1000:03d}Z"
    return {
        "schema": "rplab.command.v1",
        "command_id": str(uuid.uuid4()),
        "nonce": "A" * 43,
        "issued_at": issued,
        "expires_at": expires,
        "requested_by": "android:profile-1",
        "target": "proxmox-gateway",
        "action": action,
        "args": args or {},
        "reply_topic": "rplab-command-results",
        "requires_confirmation": action in {"vm.stop", "vm.restart", "container.stop", "container.restart", "vm.start", "container.start"},
    }


def test_healthz_unauthenticated(client):
    r = client.get("/healthz")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_nodes_requires_auth(client):
    r = client.get("/v1/proxmox/nodes")
    assert r.status_code == 401


def test_nodes_ok(client):
    r = client.get("/v1/proxmox/nodes", headers=auth_headers())
    assert r.status_code == 200
    assert r.json()[0]["node"] == "pve"


def test_command_host_health(client):
    env = make_envelope("host.health")
    r = client.post("/v1/commands/execute", headers=auth_headers(), json={"envelope": env})
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "completed"
    assert body["result"]["ok"] is True


def test_command_replay_rejected(client):
    env = make_envelope("host.health")
    # make nonce unique? replay uses same nonce; first succeeds
    r1 = client.post("/v1/commands/execute", headers=auth_headers(), json={"envelope": env})
    assert r1.status_code == 200
    r2 = client.post("/v1/commands/execute", headers=auth_headers(), json={"envelope": env})
    assert r2.status_code == 409


def test_mutation_requires_approval(client):
    env = make_envelope("vm.stop", {"node": "pve", "vmid": "100"})
    r = client.post("/v1/commands/execute", headers=auth_headers(), json={"envelope": env})
    assert r.status_code == 403


def test_mutation_with_approval(client):
    env = make_envelope("vm.stop", {"node": "pve", "vmid": "100"})
    r = client.post("/v1/commands/execute", headers=auth_headers(), json={"envelope": env, "approval_token": env["nonce"]})
    assert r.status_code == 200
    assert r.json()["result"]["ok"] is True


def test_invalid_vmid(client):
    env = make_envelope("vm.status", {"node": "pve", "vmid": "abc"})
    r = client.post("/v1/commands/execute", headers=auth_headers(), json={"envelope": env})
    assert r.status_code == 400


def test_rate_limit(client):
    # Exhaust by sending many requests with unique nonces? Rate limiter keys by token prefix, so any requests count.
    statuses = []
    for i in range(65):
        env = make_envelope("host.health")
        env["nonce"] = ("B" * 42) + str(i % 10)
        # ensure unique nonce length 43
        env["nonce"] = ("C" * 43)
        # nonce reuse will trigger replay after first; but rate limit should trigger 429 before/around
        r = client.post("/v1/commands/execute", headers=auth_headers(), json={"envelope": env})
        statuses.append(r.status_code)
    assert 429 in statuses
