# Proxmox setup

## Live host

- Proxmox VE **9.2.11**
- Node `pve`
- API: `https://<private-pve-host>:8006/api2/json`
- Guests observed: 1 QEMU, 5 LXC

## Least-privilege gateway identity

Created on 2026-11-11:

| Item | Value |
|---|---|
| Account | `<gateway-user>@pve` |
| Token ID | `<token-id>` |
| Privilege separation | enabled (`privsep=1`) |
| ACL | `KaminariRead` on `/` |
| Role privileges | `Datastore.Audit`, `Sys.Audit`, `VM.Audit` |
| Mutations | not granted |
| Expiry | none (service token) |

`KaminariRead` is intentionally read-only. The gateway’s `GATEWAY_MUTATIONS_ENABLED` stays `false`; even if it were enabled, the token has no power/configuration privileges.

## Verified read-only calls

Using the token directly against the PVE API:

| Endpoint | Result |
|---|---|
| `/version` | 200 |
| `/nodes` | 200 |
| `/nodes/pve/qemu` | 200 |
| `/nodes/pve/lxc` | 200 |
| `/nodes/pve/status` | 200 |

The token secret is stored only in `~/.config/kaminari-nifty/PVE_GATEWAY_PRIVATE_INFO.txt` (mode 600). It is not in the APK, source, docs or Git.

## Revocation

```bash
pveum user token remove <gateway-user>@pve <token-id>
pveum user delete <gateway-user>@pve
```

Backup before change: `/root/kaminari-user.cfg.backup-20261111T111543Z` on the PVE host.

## Gateway hosting

No new CT/VM was created. The FastAPI gateway is not deployed. Candidate hosts are the existing Mint VM (not guaranteed always-on) or an existing persistent application host. Deploy only after choosing a host with TLS and a stable private-network path; see `GATEWAY_DEPLOYMENT.md`.
