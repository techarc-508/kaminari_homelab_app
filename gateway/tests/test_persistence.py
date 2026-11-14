import asyncio
import sqlite3
import threading

import pytest
from fastapi.testclient import TestClient

from app.main import app, proxmox, settings, store
from app.store import SqliteStore
from support import auth_headers, make_envelope


def execute(client, envelope, approval=None):
    body = {"envelope": envelope}
    if approval is not None:
        body["approval"] = approval
    return client.post("/v1/commands/execute", headers=auth_headers(), json=body)


def test_audit_events_survive_a_new_store_instance(client):
    assert execute(client, make_envelope("host.health")).status_code == 200
    fresh = SqliteStore(settings.db_path)
    events = fresh.list_audit(50)
    assert any(event["operation_id"] == "host.health" for event in events)
    assert fresh.audit_count() == store.audit_count()


def test_idempotency_result_survives_a_new_store_instance(client, enrolled):
    envelope = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    assert execute(client, envelope, enrolled.approval(envelope)).status_code == 200

    fresh = SqliteStore(settings.db_path)
    record = fresh.idempotency_get(envelope["command_id"])
    assert record is not None
    assert record["state"] == "complete"
    assert record["payload"]["result"]["ok"] is True


def test_consumed_challenge_is_durable(client, enrolled):
    envelope = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    approval = enrolled.approval(envelope)
    assert execute(client, envelope, approval).status_code == 200

    fresh = SqliteStore(settings.db_path)
    challenge = fresh.challenge_get(approval["challenge_id"])
    assert challenge is not None
    assert challenge["consumed_at"] is not None

    # A restarted gateway still refuses the consumed approval.
    response = execute(client, {
        **make_envelope("vm.start", args={"node": "pve", "vmid": "100"}),
    }, approval)
    assert response.status_code in {403, 409}


def test_totp_enrollment_is_durable(client, enrolled):
    fresh = SqliteStore(settings.db_path)
    enrollment = fresh.enrollment_get()
    assert enrollment is not None
    assert enrollment["verified_at"] is not None
    assert enrollment["secret_b32"] == enrolled.secret


def test_audit_events_are_append_only():
    conn = sqlite3.connect(settings.db_path)
    try:
        conn.execute(
            "INSERT INTO audit_events (audit_id, occurred_at, actor, operation_id, target, risk, outcome) "
            "VALUES ('a-1', '2026-01-01T00:00:00Z', 'kf_gateway', 'test', 'pve', 'L0', 'RECEIVED')"
        )
        with pytest.raises(sqlite3.DatabaseError):
            conn.execute("UPDATE audit_events SET outcome = 'TAMPERED' WHERE audit_id = 'a-1'")
        with pytest.raises(sqlite3.DatabaseError):
            conn.execute("DELETE FROM audit_events WHERE audit_id = 'a-1'")
    finally:
        conn.close()


def test_concurrent_duplicate_mutation_executes_once(client, enrolled, monkeypatch):
    calls = {"n": 0, "lock": threading.Lock()}

    async def counting_post(path, params=None):
        with calls["lock"]:
            calls["n"] += 1
        await asyncio.sleep(0.05)
        return "UPID:started"

    monkeypatch.setattr(proxmox, "post", counting_post)
    envelope = make_envelope("vm.start", args={"node": "pve", "vmid": "100"})
    approval = enrolled.approval(envelope)

    results = []
    barrier = threading.Barrier(4)

    def worker():
        local_client = TestClient(app)
        barrier.wait()
        results.append(execute(local_client, envelope, approval))

    threads = [threading.Thread(target=worker) for _ in range(4)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join(timeout=20)

    assert len(results) == 4
    assert sum(1 for response in results if response.status_code == 200) == 1
    assert calls["n"] == 1
