#!/usr/bin/env python3
"""Verify the Kaminari gateway route end to end.

Reads token from $KF_GATEWAY_TOKEN, base URL from argv[1], CA from argv[2].
Prints one line per check: NAME=PASS/FAIL detail.
"""
import base64
import datetime
import json
import os
import secrets
import ssl
import urllib.error
import urllib.request
import uuid

BASE = os.sys.argv[1]
CA = os.sys.argv[2]
TOKEN = os.environ.get("KF_GATEWAY_TOKEN", "")
CTX = ssl.create_default_context(cafile=CA)
RESULTS = []


def envelope(action, confirm=False, args=None):
    now = datetime.datetime.now(datetime.timezone.utc)
    fmt = lambda d: d.strftime("%Y-%m-%dT%H:%M:%S.") + f"{d.microsecond // 1000:03d}Z"
    return {
        "schema": "kf.command.v1",
        "command_id": str(uuid.uuid4()),
        "nonce": base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip("="),
        "issued_at": fmt(now),
        "expires_at": fmt(now + datetime.timedelta(minutes=5)),
        "requested_by": "android:kf-route-verify",
        "target": "proxmox-gateway",
        "action": action,
        "args": args or {},
        "reply_topic": "kf-command-result",
        "requires_confirmation": confirm,
    }


def call(path, body=None, token=None, timeout=20):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method="POST" if data else "GET")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=timeout) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except Exception as e:  # noqa: BLE001
        return 0, f"transport error: {type(e).__name__}: {e}"


def check(name, ok, detail=""):
    RESULTS.append((name, ok, detail))
    print(f"{name}={'PASS' if ok else 'FAIL'} {detail}")


status, body = call("/healthz")
check("healthz_tls", status == 200, f"http={status}")

status, body = call("/readyz")
ready = json.loads(body) if status == 200 else {}
check("readyz", status == 200 and ready.get("durable_state") is True,
      f"http={status} durable={ready.get('durable_state')} mutations={ready.get('mutations_enabled')}")

status, body = call("/v1/commands/execute", {"envelope": envelope("host.health")})
check("unauthenticated_refused", status == 401, f"http={status}")

status, body = call("/v1/commands/execute", {"envelope": envelope("host.health")}, token="kf_invalid_probe")
check("invalid_token_refused", status == 401, f"http={status}")

status, body = call("/v1/commands/execute", {"envelope": envelope("host.health")}, token=TOKEN)
result = json.loads(body).get("result", {}) if status == 200 else {}
check("authenticated_read", status == 200 and result.get("ok") is True and result.get("mock") is False,
      f"http={status} ok={result.get('ok')} mock={result.get('mock')} summary={result.get('summary')}")

status, body = call("/v1/commands/execute", {"envelope": envelope("vm.start", True, {})}, token=TOKEN)
detail = body[:160]
refused = status in (403, 400) and "mutations" in detail.lower()
check("mutation_refused", refused or status == 403, f"http={status} {detail}")

status, body = call("/v1/commands/execute", {"envelope": {"action": "host.health"}}, token=TOKEN)
check("malformed_envelope_refused", status in (400, 422), f"http={status}")

failed = [n for n, ok, _ in RESULTS if not ok]
print(f"SUMMARY total={len(RESULTS)} failed={len(failed)}")
sys_exit = 1 if failed else 0
raise SystemExit(sys_exit)
