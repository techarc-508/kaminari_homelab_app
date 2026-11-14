import sqlite3

import pytest
from fastapi.testclient import TestClient

from app import totp
from app.main import app, proxmox, settings, step_up_service, store
from app.proxmox import ProxmoxError
from support import auth_headers, make_envelope, owner_headers


def execute(client, envelope, approval=None, headers=None):
    body = {"envelope": envelope}
    if approval is not None:
        body["approval"] = approval
    return client.post(
        "/v1/commands/execute", headers=headers or auth_headers(), json=body
    )


# --------------------------------------------------------------- authentication


def test_missing_token_is_rejected(client):
    response = client.post("/v1/commands/execute", json={"envelope": make_envelope()})
    assert response.status_code == 401


def test_invalid_token_is_rejected(client):
    response = execute(client, make_envelope(), headers=auth_headers("wrong-token-value"))
    assert response.status_code == 401
    assert response.json()["code"] == "unauthenticated"


def test_expired_token_is_rejected(client):
    settings.token_expires_at = "2020-01-01T00:00:00Z"
    assert execute(client, make_envelope()).status_code == 401


def test_unparsable_token_expiry_fails_closed(client):
    settings.token_expires_at = "not-a-timestamp"
    assert execute(client, make_envelope()).status_code == 401


def test_previous_token_works_during_rotation(client):
    settings.previous_gateway_token = "previous-gateway-token"
    response = execute(
        client, make_envelope(), headers=auth_headers("previous-gateway-token")
    )
    assert response.status_code == 200


# ---------------------------------------------------------------- authorization


def test_insufficient_identity_is_denied(client):
    settings.gateway_identity = "kaminari_app"
    response = execute(client, make_envelope())
    assert response.status_code == 403
    assert response.json()["code"] == "operation_not_permitted"


def test_unknown_action_is_rejected(client):
    response = execute(client, make_envelope("shell.exec"))
    assert response.status_code == 400
    assert response.json()["code"] == "unknown_action"


def test_unknown_envelope_field_is_rejected(client):
    envelope = make_envelope()
    envelope["script"] = "rm -rf /"
    assert execute(client, envelope).status_code == 422


def test_unknown_request_field_is_rejected(client):
    envelope = make_envelope()
    response = client.post(
        "/v1/commands/execute",
        headers=auth_headers(),
        json={"envelope": envelope, "approval_token": envelope["nonce"]},
    )
    assert response.status_code == 422


def test_malformed_json_is_rejected(client):
    response = client.post(
        "/v1/commands/execute",
        headers={**auth_headers(), "Content-Type": "application/json"},
        content="{not json",
    )
    assert response.status_code == 422


def test_mutations_disabled_is_enforced(client, enrolled):
    settings.mutations_enabled = False
    envelope = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    approval = enrolled.approval(envelope)
    response = execute(client, envelope, approval)
    assert response.status_code == 403
    assert response.json()["code"] == "forbidden"


# -------------------------------------------------------------------- step-up


def test_mutation_without_enrollment_refuses(client):
    envelope = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    response = execute(client, envelope)
    assert response.status_code == 403
    assert response.json()["code"] == "step_up_unavailable"


def test_challenge_requires_enrollment(client):
    response = client.post(
        "/v1/stepup/challenge",
        headers=auth_headers(),
        json={
            "command_id": make_envelope()["command_id"],
            "action": "vm.start",
            "target": "proxmox-gateway",
            "args": {"node": "pve", "vmid": "100"},
        },
    )
    assert response.status_code == 409
    assert response.json()["code"] == "step_up_unavailable"


def test_expired_totp_code_is_rejected(client, enrolled):
    envelope = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    clock = step_up_service.clock
    challenge_id = enrolled.challenge(envelope)
    stale_step = totp.step_for(clock()) - 5
    approval = {
        "challenge_id": challenge_id,
        "code": totp.code_for_step(enrolled.secret, stale_step),
    }
    response = execute(client, envelope, approval)
    assert response.status_code == 403
    assert response.json()["code"] == "invalid_totp"


def test_reused_totp_step_is_rejected(client, enrolled):
    first = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    assert execute(client, first, enrolled.approval(first)).status_code == 200

    second = make_envelope("vm.start", args={"node": "pve", "vmid": "101"})
    challenge_id = enrolled.challenge(second)
    approval = {
        "challenge_id": challenge_id,
        "code": totp.code_for_step(
            enrolled.secret, totp.step_for(step_up_service.clock())
        ),
    }
    response = execute(client, second, approval)
    assert response.status_code == 409
    assert response.json()["code"] == "totp_replayed"


def test_every_mutation_is_routed_through_step_up(client, enrolled):
    ordered = [
        make_envelope("vm.start", args={"node": "pve", "vmid": "100"}),
        make_envelope("vm.start", args={"node": "pve", "vmid": "101"}),
    ]
    for envelope in ordered:
        approval = enrolled.approval(envelope)
        assert execute(client, envelope, approval).status_code == 200


def test_reused_approval_cannot_authorise_a_second_request(client, enrolled):
    envelope = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    approval = enrolled.approval(envelope)
    assert execute(client, envelope, approval).status_code == 200

    other = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    response = execute(client, other, approval)
    assert response.status_code == 409
    assert response.json()["code"] == "approval_already_used"


# --------------------------------------------------------------- binding tests


def test_altered_action_invalidates_approval(client, enrolled):
    envelope = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    approval = enrolled.approval(envelope)
    envelope["action"] = "vm.stop"
    response = execute(client, envelope, approval)
    assert response.status_code == 403
    assert response.json()["code"] == "approval_binding_mismatch"


def test_altered_args_invalidate_approval(client, enrolled):
    envelope = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    approval = enrolled.approval(envelope)
    envelope["args"] = {"node": "pve", "vmid": "101"}
    response = execute(client, envelope, approval)
    assert response.status_code == 403
    assert response.json()["code"] == "approval_binding_mismatch"


def test_altered_command_id_invalidates_approval(client, enrolled):
    envelope = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    approval = enrolled.approval(envelope)
    other = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    response = execute(client, other, approval)
    assert response.status_code == 403
    assert response.json()["code"] == "approval_binding_mismatch"


# ------------------------------------------------------- replay / idempotency


def test_replayed_nonce_is_rejected(client):
    envelope = make_envelope("host.health")
    assert execute(client, envelope).status_code == 200
    response = execute(client, envelope)
    assert response.status_code == 409
    assert response.json()["code"] == "replayed_request"


def test_command_id_reuse_with_different_content_is_rejected(client):
    first = make_envelope("host.health")
    assert execute(client, first).status_code == 200
    second = make_envelope("node.list")
    second["command_id"] = first["command_id"]
    response = execute(client, second)
    assert response.status_code == 409
    assert response.json()["code"] == "idempotency_conflict"


# ------------------------------------------------------------------ rate limit


def test_rate_limit_cannot_be_bypassed_with_new_nonces(client):
    statuses = [execute(client, make_envelope()).status_code for _ in range(70)]
    assert 429 in statuses
    assert statuses.count(429) > 0


# --------------------------------------------------------------- fail-closed


def test_audit_store_failure_fails_closed(client, monkeypatch):
    calls = {"post": 0}

    async def counting_post(path, params=None):
        calls["post"] += 1
        return "UPID:started"

    def boom(event):
        raise sqlite3.OperationalError("disk I/O error")

    monkeypatch.setattr(proxmox, "post", counting_post)
    monkeypatch.setattr(store, "append_audit", boom)
    response = execute(client, make_envelope("vm.start", args={"node": "pve", "vmid": "100"}))
    assert response.status_code == 503
    assert calls["post"] == 0


def test_proxmox_authorization_failure_is_reported_as_a_failure(client, enrolled, monkeypatch):
    async def forbidden(path, params=None):
        raise ProxmoxError(403, "Permission check failed")

    monkeypatch.setattr(proxmox, "post", forbidden)
    envelope = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    response = execute(client, envelope, enrolled.approval(envelope))
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "failed"
    assert body["result"]["ok"] is False


# ------------------------------------------------------------------ owner paths


def test_owner_routes_reject_the_gateway_token(client):
    assert client.get("/v1/audit/recent", headers=auth_headers()).status_code == 401


def test_owner_routes_are_disabled_without_an_owner_token(client, monkeypatch):
    monkeypatch.setattr(settings, "owner_token", None)
    response = client.get("/v1/audit/recent", headers=owner_headers())
    assert response.status_code == 503
    assert response.json()["code"] == "owner_not_configured"


def test_enrollment_is_owner_only(client):
    response = client.post("/v1/stepup/enroll/start", headers=auth_headers())
    assert response.status_code == 401
