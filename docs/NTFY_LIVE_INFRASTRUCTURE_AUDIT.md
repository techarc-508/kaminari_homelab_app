# ntfy live infrastructure audit

Date: 2026-11-11 UTC. Read-only inspection; no service restart, no config change, no message-body inspection.

## Server identity

| Item | Evidence |
|---|---|
| Version | `ntfy 2.28.0` (`/usr/bin/ntfy --version` inside the container) |
| Host | Proxmox node `pve`, PVE 9.2.11 |
| Runtime | Docker container `ntfy` (`binwiederhier/ntfy`) on CT 104 (`docker-stack`) |
| Container IP | `172.19.0.4` |
| Published LAN port | `192.168.0.104:8201` → container `:80` |
| Volume | `utilities_ntfy_data` → `/var/lib/ntfy` |
| Restart policy | `unless-stopped` |
| Process user | UID 100000 (container default) |
| Service manager | Docker; no host systemd unit |

The process was initially observed as PID 8671 on the PVE host, but it is a Docker container process; `nsenter` did not expose a separate listening socket. The LAN listener is the Docker port mapping on CT 104.

## Configuration

- The container command is `serve` with ntfy defaults.
- No `server.yml` auth file is mounted.
- Environment contains only `PATH` (no auth or topic variables).
- `/v1/config` reports:
  - `enable_login=false`
  - `require_login=false`
  - `enable_signup=false`
- Server stats: `users=0`; `messages_published=628`; `topics_active=3`; `subscribers=1` during the audit window.
- Messages are cached (stats show `messages_cached=35`), but the only mounted data path is `/var/lib/ntfy`; no separate cache DB file was visible. Do not assume durable history beyond the server’s own cache policy.

## Health and reachability

| Test | Result |
|---|---|
| `curl https://ntfy.rplab.qzz.io/v1/health` from Mint VM | `200 {"healthy":true}` |
| Same from PVE | `200` |
| `https://nfty.rplab.qzz.io/v1/health` from PVE | `502` |
| `http://192.168.0.104:8201/v1/health` from Mint VM | `200 {"healthy":true}` |

The public hostname is served through Cloudflare and the existing cloudflared tunnel. TLS is valid for `ntfy.rplab.qzz.io`.

## Auth and ACL state

Authentication is disabled. There is no auth database to inspect, no users, no tokens, and no ACLs. The existing producers publish anonymously:

- PVE webhook `kaminari-ntfy` → `http://192.168.0.104:8201/KAMINARI-FUJI-Homelab`
- watchtower → `ntfy://ntfy.rplab.qzz.io/KAMINARI-FUJI-Homelab`
- `kaminari-docker-events` → `http://192.168.0.104:8201/KAMINARI-FUJI-Homelab`

Enabling default-deny auth without updating all three would break production alerts. No auth change was made.

## Logs

The container logs only aggregate stats and errors; no message bodies or tokens were printed. The server is stable under the current low traffic. No restart or policy change is recommended before the owner decides whether to enable auth.

## Backup

Before any future auth change:

1. `docker inspect ntfy` to capture the current command/mounts.
2. Back up the `utilities_ntfy_data` volume and `/opt/stack/utilities/docker-compose.ops.yml`.
3. Record the producer URLs above.
4. Test every producer after the change.

## Limitations

- No complete topic registry exists; ntfy does not maintain one in this configuration.
- Cache persistence beyond the current container/volume behavior is not proven.
- The exact Cloudflare tunnel routing config was not modified or audited beyond the observed 200/502 behavior.
