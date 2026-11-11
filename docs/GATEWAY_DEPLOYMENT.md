# Gateway deployment

The gateway is a separate FastAPI service in `gateway/`. It is **not** part of the APK and is **not currently deployed**.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/healthz` | liveness |
| GET | `/readyz` | readiness |
| GET | `/v1/proxmox/nodes` | node list |
| GET | `/v1/proxmox/qemu` | QEMU inventory |
| GET | `/v1/proxmox/lxc` | LXC inventory |
| GET | `/v1/proxmox/status` | guest status |
| GET | `/v1/proxmox/storage` | storage summary |
| GET | `/v1/proxmox/tasks` | recent tasks |
| POST | `/v1/commands/execute` | typed command execution (mutations disabled by default) |

## Environment

```bash
GATEWAY_GATEWAY_TOKEN=<long-random-token>
GATEWAY_PROXMOX_BASE_URL=https://192.168.0.113:8006/api2/json
GATEWAY_PROXMOX_API_TOKEN=kaminari-gateway@pve!gateway-ro=<secret>
GATEWAY_PROXMOX_VERIFY_TLS=true
GATEWAY_MUTATIONS_ENABLED=false
```

Never put these values in the repository. The real values are in `~/.config/kaminari-nifty/PVE_GATEWAY_PRIVATE_INFO.txt` (600).

`GATEWAY_GATEWAY_TOKEN` authenticates the Android caller to the gateway; it is separate from the PVE token.

## TLS

PVE uses a private/self-signed certificate. For `GATEWAY_PROXMOX_VERIFY_TLS=true`, install the PVE CA certificate into the gateway trust store. Do not disable TLS verification in production. Expose the gateway only on the private network or a VPN/reverse proxy with TLS.

## Run

```bash
cd gateway
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
export GATEWAY_GATEWAY_TOKEN=...   # from private file
export GATEWAY_PROXMOX_BASE_URL=https://192.168.0.113:8006/api2/json
export GATEWAY_PROXMOX_API_TOKEN=... # from private file
export GATEWAY_MUTATIONS_ENABLED=false
uvicorn app.main:app --host 127.0.0.1 --port 8443
```

## Tests

```bash
cd gateway && PYTHONPATH=. python3 -m pytest -q
# 9 passed
```

## Safety

- Constant-time gateway token comparison.
- Request size and rate limits.
- Replay protection for command nonces.
- Structured errors; no stack traces.
- Mutations require an explicit approval token and are disabled by default.
- The Android app’s wired executor is the mock; the real gateway is a future phase.
