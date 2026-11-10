# Kaminari Proxmox Gateway

Separate, self-hosted FastAPI service for typed, allowlisted Proxmox operations. It is intentionally not an arbitrary shell/SSH/unrestricted Proxmox proxy.

## Run

```bash
cd gateway
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
export GATEWAY_GATEWAY_TOKEN="replace-with-long-random-token"
export GATEWAY_PROXMOX_BASE_URL="https://proxmox.example:8006/api2/json"
export GATEWAY_PROXMOX_API_TOKEN="kaminari@pve!kaminari=secret"
export GATEWAY_MUTATIONS_ENABLED="false"
uvicorn app.main:app --host 0.0.0.0 --port 8443 --ssl-keyfile ... --ssl-certfile ...
```

Use a reverse proxy or uvicorn TLS settings for HTTPS. Do not expose publicly by default; prefer private network/VPN.

## Endpoints

- `GET /healthz`
- `GET /readyz`
- `GET /v1/proxmox/nodes`
- `GET /v1/proxmox/qemu?node=...`
- `GET /v1/proxmox/lxc?node=...`
- `GET /v1/proxmox/status?node=...&kind=qemu|lxc&vmid=...`
- `GET /v1/proxmox/storage?node=...`
- `GET /v1/proxmox/tasks?node=...`
- `POST /v1/commands/execute`

## Tests

```bash
cd gateway
PYTHONPATH=. python -m pytest -q
```

## Security notes

- Gateway token is compared in constant time.
- Proxmox API token stays server-side.
- Mutations are disabled unless `GATEWAY_MUTATIONS_ENABLED=true`.
- Mutation requests require an approval token equal to the command nonce.
- Nonces are replay-protected in memory for their expiry window.
- Request size and rate limits are enforced.
- Structured errors only; no stack traces are returned.
- Audit events are logged without secrets.