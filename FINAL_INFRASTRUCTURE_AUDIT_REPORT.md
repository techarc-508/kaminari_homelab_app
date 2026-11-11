# Final infrastructure audit report

Date: 2026-11-11 UTC. Scope: Proxmox VE host, existing ntfy deployment, network topology, least-privilege gateway identity. Read-only for all production services; the only writes were the owner-authorized PVE service account/token and one harmless message on the dedicated test topic.

## Executive result

| Area | Verdict | Evidence |
|---|---|---|
| Existing ntfy server | **Operational** | ntfy 2.28.0 Docker container on the Docker LXC; health 200 |
| Canonical public URL | **Verified** | `https://ntfy.rplab.qzz.io` (200 from Mint and PVE) |
| Old documented URL | **Broken** | `https://nfty.rplab.qzz.io` → Cloudflare 502 |
| Authentication | **Disabled** | `/v1/config` `enable_login=false`; `users=0` |
| Production topic | **Verified** | `KAMINARI-FUJI-Homelab`, 3 producers |
| Test topic | **Verified** | `kaminari-onboarding-test`; publish/read-back succeeded |
| PVE | **Operational** | PVE 9.2.11, node `pve` |
| Gateway identity | **Provisioned and verified** | `<gateway-user>@pve!<token-id>`, `KaminariRead`, read-only API 200s |
| Gateway service | **Not deployed** | No new CT/VM; deployment prerequisite documented |

## What was inspected

- Docker container `ntfy` (`binwiederhier/ntfy`) on the Docker LXC, volume `utilities_ntfy_data`.
- Published port `<private-ntfy-lan>:8201`; container IP `<container-ip>:80`.
- No host systemd unit; Docker restart policy `unless-stopped`.
- `ntfy 2.28.0`; defaults; no `server.yml` auth file; environment contains only `PATH`.
- `/v1/config` and `/v1/health`; server stats (`users=0`, `messages_published=628`, `messages_cached=35`).
- Cloudflare → cloudflared the cloudflared LXC → the Docker LXC path.
- Producer configs: PVE webhook, watchtower, `kaminari-docker-events`.
- PVE 9.2.11, node `pve`, guests: 1 QEMU, 5 LXC.
- PVE users/tokens/ACL before and after the authorized change.

## What was not changed

- No ntfy service restart, config edit, auth enablement or global access-policy change.
- No new LXC/CT/VM and no new ntfy instance.
- No production topic publish.
- No firewall or Cloudflare change.
- No PVE mutation permission, shell/SSH endpoint or root credential use.

## Key facts and limitations

- Authentication is off, so topic names are the only access control. This is adequate only for the current non-sensitive homelab alerts; enable auth carefully after updating all three producers.
- The old `nfty` hostname remains a 502; the earlier audit's blocker is explained by that spelling, not by a dead ntfy server.
- The LAN origin is HTTP only and is not usable by the Android app (HTTPS enforced + Android cleartext denied). Use the public HTTPS URL on the phone.
- The gateway is not deployed; only the least-privilege identity is ready. A deployment host with TLS and a stable private path is still required.
- No message bodies or attachment contents were inspected or published in reports.

## Deliverables

- `docs/NTFY_LIVE_INFRASTRUCTURE_AUDIT.md`
- `docs/NTFY_LIVE_TOPIC_INVENTORY.md`
- `docs/NTFY_LIVE_TOPOLOGY.md`
- `docs/PROXMOX_SETUP.md`
- `docs/GATEWAY_DEPLOYMENT.md`
- `FINAL_PROXMOX_GATEWAY_ACCESS_REPORT.md`
