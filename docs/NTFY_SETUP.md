# ntfy setup and live topology

## Canonical URL

Use **`https://ntfy.rplab.qzz.io`**.

The hostname `https://nfty.rplab.qzz.io` is a typo/mistyped record and returns HTTP 502 from Cloudflare. It is not the canonical endpoint. Do not use it in new configurations.

## Live deployment (verified 2026-11-11)

```
Internet
  │ HTTPS
  ▼
Cloudflare (ntfy.rplab.qzz.io)
  │
  ▼
CT 103 — cloudflared tunnel
  │
  ▼
CT 104 — docker-stack, container "ntfy"
  │  image: binwiederhier/ntfy, ntfy 2.28.0
  │  published port: 192.168.0.104:8201 -> container :80
  ▼
Docker volume utilities_ntfy_data -> /var/lib/ntfy
```

Evidence:

- `docker ps`: `ntfy | binwiederhier/ntfy | ...`
- `ntfy --version`: `2.28.0`
- health from the Mint VM: `200 {"healthy":true}`
- health from Proxmox: `200` for `ntfy.rplab.qzz.io`; `502` for `nfty.rplab.qzz.io`
- server stats: `users=0`; `/v1/config` reports `enable_login=false`, `require_login=false`
- no `server.yml` auth file is configured; the container runs ntfy defaults with a `/var/lib/ntfy` volume

## Authentication status

Authentication is **disabled**. Anyone who knows a topic name can read or publish to it. This is safe only for non-sensitive homelab topics and only while the endpoint is not abused. No admin account and no app token can be created until auth is enabled.

Do not enable auth casually: existing producers (PVE webhook, watchtower, docker-events) currently publish anonymously. Enabling `auth-default-access=deny-all` without updating every producer would break production alerts.

### Safe future enablement

1. Back up the `utilities_ntfy_data` volume and the compose file.
2. Create an auth database and a non-admin `kaminari_app` user.
3. Keep `KAMINARI-FUJI-Homelab` readable by existing producers, or migrate each producer to an explicit token.
4. Test PVE webhook, watchtower, docker-events and the Android app.
5. Only then set a default-deny policy.
6. Create a separate `kaminari_owner_admin` admin, never used by the app.

## Topics

| Topic | Status | Use |
|---|---|---|
| `KAMINARI-FUJI-Homelab` | verified production | PVE webhook, watchtower, docker-events; app reads only |
| `kaminari-onboarding-test` | verified test | harmless publish/subscribe tests |
| `homelab-alerts`, `iot-telemetry`, `backups`, `build-server`, `security` | unverified candidates | do not assume they exist |

## Keeping the server healthy

- Monitor `https://ntfy.rplab.qzz.io/v1/health`.
- Keep the Cloudflare tunnel and Docker restart policy in place.
- The ntfy container uses `restart: unless-stopped`.
- Do not expose the LAN port directly to the internet; use the tunnel.
