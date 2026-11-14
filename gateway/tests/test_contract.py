import json
from pathlib import Path

import pytest

from app import catalogue
from app.approval import UnavailableStepUpVerifier
from app.audit import InMemoryAuditSink, new_audit_event
from app.idempotency import InMemoryIdempotencyStore
from app.main import audit_sink


def test_catalogue_has_the_approved_shape():
    assert len(catalogue.TOPICS) == 22
    assert len({t.id for t in catalogue.TOPICS}) == 22
    assert "kf-builds" not in catalogue.TOPICS_BY_ID
    assert len(catalogue.IDENTITIES) == 7
    assert [i.name for i in catalogue.IDENTITIES if i.ntfy_admin] == ["kfadmin"]
    assert len(catalogue.ACTIONS) == 14
    assert len(catalogue.ACTIONS_BY_ID) == 14
    assert catalogue.COMMAND_REPLY_TOPIC == "kf-command-result"


def test_contract_declares_the_canonical_command_schema():
    contract = json.loads(
        (
            Path(__file__).resolve().parents[2] / "contracts" / "kf_contract.v1.json"
        ).read_text(encoding="utf-8")
    )
    assert contract["schema"] == "kf.contract.v1"
    assert contract["commandSchema"] == "kf.command.v1"
    assert contract["legacyCommandSchemas"] == ["rplab.command.v1"]
    assert "rplab.command.v1" in catalogue.COMMAND_SCHEMAS
    assert "kf.command.v1" in catalogue.COMMAND_SCHEMAS


def test_identity_permissions_are_deny_by_default():
    assert catalogue.can_write("kaminari_app", "kf-command-request")
    assert not catalogue.can_write("kaminari_app", "kf-command-result")
    assert not catalogue.can_write("kaminari_app", "kf-admin-audit")
    assert not catalogue.can_write("kaminari_app", "kf-web")
    assert catalogue.can_read("kaminari_app", "kf-command-result")

    assert catalogue.can_read("kf_gateway", "kf-command-request")
    assert catalogue.can_write("kf_gateway", "kf-command-result")
    assert catalogue.can_write("kf_gateway", "kf-admin-audit")
    assert not catalogue.can_write("kf_gateway", "kf-command-request")
    assert not catalogue.can_read("kf_gateway", "kf-services")

    assert catalogue.can_write("kf_monitor", "kf-pve-host")
    assert not catalogue.can_read("kf_monitor", "kf-pve-host")
    assert catalogue.permission_for("anonymous", "kf-web") is None


def test_action_authorization_rejects_other_identities():
    spec = catalogue.authorize_action("kf_gateway", "host.health")
    assert spec.risk == "L0"
    with pytest.raises(Exception) as exc:
        catalogue.authorize_action("kaminari_app", "host.health")
    assert getattr(exc.value, "status_code", None) == 403
    with pytest.raises(Exception) as exc:
        catalogue.authorize_action("kf_gateway", "shell.exec")
    assert getattr(exc.value, "status_code", None) == 400


def test_audit_sink_is_durable_and_redacts_sensitive_keys():
    assert audit_sink.durable is True
    event = new_audit_event(
        actor="kf_gateway",
        operation_id="host.health",
        target="pve",
        risk="L0",
        outcome="ALLOWED",
        details={"api_token": "x", "totp_code": "123456", "node": "pve"},
    )
    details = event.to_dict()["details"]
    assert "api_token" not in details
    assert "totp_code" not in details
    assert details["node"] == "pve"


def test_in_memory_audit_sink_is_bounded_and_not_durable():
    sink = InMemoryAuditSink(max_events=2)
    assert sink.durable is False
    for _ in range(3):
        sink.append(
            new_audit_event(
                actor="kf_gateway",
                operation_id="host.health",
                target="pve",
                risk="L0",
                outcome="ALLOWED",
            )
        )
    assert sink.count() == 2


def test_idempotency_test_double_contract():
    store = InMemoryIdempotencyStore()
    assert store.durable is False
    assert store.reserve("k1", "fp", 60) is True
    assert store.reserve("k1", "fp", 60) is False
    assert store.get("k1").state == "in_progress"
    assert store.complete("k1", "result-1") is True
    assert store.get("k1").result_ref == "result-1"
    store.release("k1")
    assert store.get("k1") is None


def test_unavailable_step_up_is_truthfully_not_step_up():
    unavailable = UnavailableStepUpVerifier()
    assert unavailable.is_step_up is False
    assert unavailable.available() is False
    with pytest.raises(Exception) as exc:
        unavailable.verify({"challenge_id": "x", "code": "123456"}, {"nonce": "n"})
    assert getattr(exc.value, "code", None) == "step_up_unavailable"


def test_nonce_echo_approval_is_gone():
    import app.approval as approval_module

    assert not hasattr(approval_module, "NonceEchoLegacyApproval")
