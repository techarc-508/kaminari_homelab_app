"""Live verification for a deployed gateway.

The gateway token is read from the container environment (`GATEWAY_GATEWAY_TOKEN`)
so the script can be piped over stdin without writing to the read-only rootfs:

    docker exec -i kaminari-gateway python - < verify_deployed.py
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = "http://127.0.0.1:8080"
token = os.environ.get("GATEWAY_GATEWAY_TOKEN") or sys.stdin.read().strip()


def call(path: str, method: str = "GET", body=None, authenticated: bool = True):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if authenticated:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return response.status, response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8")


def iso(ms: int) -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(ms / 1000)) + f".{ms % 1000:03d}Z"


def envelope(action: str = "host.health", args=None) -> dict:
    now = int(time.time() * 1000)
    return {
        "schema": "kf.command.v1",
        "command_id": str(uuid.uuid4()),
        "nonce": (uuid.uuid4().hex + "A" * 16)[:43],
        "issued_at": iso(now),
        "expires_at": iso(now + 300_000),
        "requested_by": "android:deploy-check",
        "target": "proxmox-gateway",
        "action": action,
        "args": args or {},
        "reply_topic": "kf-command-result",
        "requires_confirmation": False,
    }


print("healthz", *call("/healthz", authenticated=False))
print("readyz", *call("/readyz", authenticated=False))
print("unauthenticated", call("/v1/stepup/status", authenticated=False)[0])
print("stepup_status", *call("/v1/stepup/status"))
print("pve_read", *call("/v1/commands/execute", "POST", {"envelope": envelope()}))
print("removed_raw_route", call("/v1/proxmox/nodes")[0])
print("owner_endpoint_with_gateway_token", call("/v1/audit/recent")[0])
print(
    "mutation_without_step_up",
    *call(
        "/v1/commands/execute",
        "POST",
        {"envelope": envelope("vm.start", {"node": "pve", "vmid": "999999"})},
    ),
)
