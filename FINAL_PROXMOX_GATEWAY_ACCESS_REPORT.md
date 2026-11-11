# Final Proxmox gateway access report

Date: 2026-11-11 UTC.

## PVE environment

| Item | Value |
|---|---|
| PVE version | 9.2.11 |
| Node | `pve` |
| API | `https://<private-pve-host>:8006/api2/json` |
| Guests | 1 QEMU, 5 LXC |

## Service identity

| Item | Value |
|---|---|
| Account | `<gateway-user>@pve` |
| Token | `<token-id>` |
| Privilege separation | enabled |
| Role | `KaminariRead` (custom, read-only) |
| Privileges | `Datastore.Audit`, `Sys.Audit`, `VM.Audit` |
| ACL path | `/` |
| Expiry | none (service token) |
| Mutations | none |

The token secret is only in `~/.config/kaminari-nifty/PVE_GATEWAY_PRIVATE_INFO.txt` (600). It is not in the APK, source, docs or Git.

## Verification (direct PVE API, token auth)

| Endpoint | HTTP |
|---|---|
| `/version` | 200 |
| `/nodes` | 200 |
| `/nodes/pve/qemu` | 200 |
| `/nodes/pve/lxc` | 200 |
| `/nodes/pve/status` | 200 |

No mutation endpoint was called. The token has no power/configuration privilege.

## Gateway

The FastAPI service in `gateway/` is **not deployed**. It exposes read-only inventory/status routes and a typed command endpoint whose mutation path is disabled (`GATEWAY_MUTATIONS_ENABLED=false`). No new CT/VM was created.

Deployment requires:

- an existing always-on private host with TLS (Mint VM or another persistent host),
- `GATEWAY_GATEWAY_TOKEN` for the Android caller,
- the PVE token above,
- PVE CA trust for `GATEWAY_PROXMOX_VERIFY_TLS=true`,
- a private-network/VPN path; do not expose publicly by default.

## Revocation

```bash
pveum user token remove <gateway-user>@pve <token-id>
pveum user delete <gateway-user>@pve
```

Backup before change: `/root/kaminari-user.cfg.backup-20261111T111543Z`.

## What the app can do

The Android Command Centre uses a **mock executor** only. It does not call the gateway and cannot change Proxmox state. The gateway client exists as a boundary but is not wired into the app container.
