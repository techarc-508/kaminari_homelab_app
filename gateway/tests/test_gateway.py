from app.main import settings, step_up_service, store
from support import auth_headers, make_envelope, owner_headers


def execute(client, envelope, approval=None, headers=None):
    body = {"envelope": envelope}
    if approval is not None:
        body["approval"] = approval
    return client.post(
        "/v1/commands/execute", headers=headers or auth_headers(), json=body
    )


def test_healthz_is_unauthenticated(client):
    response = client.get("/healthz")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_readyz_reports_durable_state(client):
    response = client.get("/readyz")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ready"
    assert body["durable_state"] is True
    assert body["step_up_enrolled"] is False


def test_read_command_completes(client):
    response = execute(client, make_envelope("host.health"))
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "completed"
    assert body["result"]["ok"] is True
    assert body["replayed"] is False
    assert body["identity"] == "kf_gateway"


def test_canonical_and_legacy_command_schemas_are_accepted(client):
    for schema in ("kf.command.v1", "rplab.command.v1"):
        response = execute(client, make_envelope("host.health", schema=schema))
        assert response.status_code == 200, (schema, response.text)


def test_unknown_command_schema_is_rejected(client):
    response = execute(client, make_envelope("host.health", schema="unknown.v9"))
    assert response.status_code == 400
    assert response.json()["code"] == "invalid_request"


def test_unknown_reply_topic_is_rejected(client):
    envelope = make_envelope("host.health", reply_topic="not-a-result-topic")
    response = execute(client, envelope)
    assert response.status_code == 400
    assert response.json()["code"] == "invalid_request"


def test_legacy_reply_topic_still_correlates(client):
    envelope = make_envelope("host.health", reply_topic="rplab-command-results")
    assert execute(client, envelope).status_code == 200


def test_unexpected_args_are_rejected(client):
    envelope = make_envelope("host.health", args={"node": "pve"})
    response = execute(client, envelope)
    assert response.status_code == 400
    assert response.json()["code"] == "invalid_request"


def test_invalid_vmid_is_rejected(client):
    envelope = make_envelope("vm.status", args={"node": "pve", "vmid": "abc"})
    assert execute(client, envelope).status_code == 400


def test_raw_proxmox_passthrough_routes_are_gone(client):
    for path in (
        "/v1/proxmox/nodes",
        "/v1/proxmox/qemu?node=pve",
        "/v1/proxmox/lxc?node=pve",
        "/v1/proxmox/storage?node=pve",
        "/v1/proxmox/tasks?node=pve",
        "/v1/proxmox/status?node=pve&kind=qemu&vmid=100",
    ):
        assert client.get(path, headers=auth_headers()).status_code == 404, path


def test_mutation_without_approval_is_refused(client, enrolled):
    envelope = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    response = execute(client, envelope)
    assert response.status_code == 403
    assert response.json()["code"] == "approval_required"


def test_mutation_with_bound_approval_completes(client, enrolled):
    envelope = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    approval = enrolled.approval(envelope)
    response = execute(client, envelope, approval)
    assert response.status_code == 200, response.text
    assert response.json()["status"] == "completed"
    assert response.json()["result"]["ok"] is True


def test_replayed_command_returns_stored_result(client):
    envelope = make_envelope("host.health")
    first = execute(client, envelope)
    second = execute(client, {
        **envelope,
        "nonce": "Z" * 43,
    })
    assert first.status_code == 200
    # The same command_id with different content is a conflict, not a re-run.
    assert second.status_code == 409
    assert second.json()["code"] == "idempotency_conflict"


def test_owner_reads_audit_and_secrets_are_absent(client, enrolled):
    envelope = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    approval = enrolled.approval(envelope)
    assert execute(client, envelope, approval).status_code == 200

    response = client.get("/v1/audit/recent?limit=100", headers=owner_headers())
    assert response.status_code == 200
    events = response.json()["events"]
    assert any(event["operation_id"] == "host.health" or event["operation_id"] == "vm.start" for event in events)
    serialized = response.text.lower()
    assert "test-gateway-token" not in serialized
    assert "test-owner-token" not in serialized
    assert enrolled.secret.lower() not in serialized
    assert approval["code"] not in response.text


def test_gateway_identity_comes_from_configuration(client):
    settings.gateway_identity = "kf_gateway"
    response = execute(client, make_envelope("node.list"))
    assert response.status_code == 200
    assert response.json()["identity"] == "kf_gateway"
    assert store.audit_count() > 0
    assert step_up_service.available() is False
