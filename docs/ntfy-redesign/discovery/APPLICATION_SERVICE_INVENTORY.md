# Application and Service Inventory

**Discovery date:** 2026-11-11  
**Method:** Docker container list on CT104, PVE guest list, selected health status, and application configuration references. No application database or user content was read.

---

## 1. Summary

- **CT104** runs the Docker stack: **56 running containers** (75 images present, 58 containers total, 63 volumes, 267 build-cache entries).
- **CT100/101/102** run AdGuard, Guacamole and Immich outside Docker.
- **VM200** is the Linux Mint development VM.
- Public exposure is limited (verified) to the RPLab website and ntfy through Cloudflare Tunnel; other services were not verified as internet-facing.
- Many services have no health check; “running” does not mean “healthy”.

---

## 2. Key services and monitoring value

| Service | Host | Role | Importance | Health at discovery | Monitoring | Backup dependency |
|---|---|---|---|---|---|---|
| `rplab-dashboard` (Kaminari-Fuji Gate) | CT104 | Public website/dashboard | **Critical** | healthy | `/healthz`, `/api/health`, Docker health, 5xx, TLS | SQLite ledger + config |
| `rplab-dashboard` website data | CT104 | SQLite ledger | **Critical** | — | DB writable, backup | yes |
| `ntfy` | CT104 | Notification transport | **Critical** | running; `/v1/health` 200 | health, container, tunnel, storage | auth DB if enabled; cache is volatile |
| `vaultwarden` | CT104 | Password vault | **Critical** | running | HTTP health, login failure rate, DB | yes |
| `authentik-server` / `authentik-worker` | CT104 | SSO | **Critical** | running | `/healthz` (if available), worker queue, DB/Redis | yes |
| `postgresql` (Authentik) | CT104 | Auth database | **Critical** | running | connections, disk, backup | yes |
| `redis` (Authentik) | CT104 | Auth cache/queue | **High** | running | memory, restart | no (rebuildable) |
| `immich` | CT102 | Photo library | **Critical** | running | HTTP, upload path, DB, queue | yes (DB + uploads) |
| `adguard` | CT100 | LAN DNS | **Critical** | running | DNS resolution, process, upstream | config |
| `apache-guacamole` | CT101 | Remote access | **High** | running | HTTP, auth failures | config/DB |
| `cloudflared` | CT103 | Public ingress | **Critical** | running | tunnel status, restarts | token/config |
| `uptime-kuma` | CT104 | Availability monitoring | **High** | healthy | own health, DB, disk | yes |
| `beszel` | CT104 | Metrics | **Medium** | running (no health) | agent availability, disk | yes |
| `scrutiny-web` + `influxdb` | CT104 | SMART/monitoring | **High** | healthy | SMART failures, DB, disk | yes |
| `healthchecks` | CT104 | Cron/dead-man | **High** | running (no health) | missed checks, DB | yes |
| `crowdsec` | CT104 | Security detection | **High** | running (no health) | decisions, bouncer status | config |
| `dozzle` | CT104 | Container logs | **Medium** | running | HTTP, Docker socket | no |
| `apprise` | CT104 | Notification fallback | **Medium** | running | HTTP, queue | config |
| `duplicati` | CT104 | Backup jobs | **High** | running | job status, destination, restore | yes |
| `watchtower` | CT104 | Image updates | **Medium** | running | update failures, restart loop | no |
| `kaminari-docker-events` | CT104 | Docker event watcher | **Medium** | running | restart, ntfy publish errors | no |
| `kaminari-diary` | CT104 | Personal diary | **High** | running | HTTP, DB, ntfy | yes |
| `n8n` | CT104 | Automation | **High** | running | HTTP, queue, DB | yes |
| `filebrowser` | CT104 | Files | **High** | running | HTTP, storage | data |
| `syncthing` | CT104 | File sync | **High** | running | HTTP, peer status | data |
| `searxng` | CT104 | Search | **Medium** | running | HTTP, upstream | config |
| `librechat` + `open-webui` | CT104 | AI chat | **Medium** | running | HTTP, API dependency | DB |
| `karakeep` | CT104 | Bookmarks | **Medium** | running | HTTP, DB | yes |
| `memos` | CT104 | Notes | **Medium** | running | HTTP, DB | yes |
| `onlyoffice` / `stirling-pdf` | CT104 | Documents | **Medium** | running | HTTP | data |
| `code-server` | CT104 | IDE | **Medium** | running | HTTP, auth | workspace |
| `navidrome` | CT104 | Music | **Medium** | running | HTTP, library | music data |
| `myspeed` | CT104 | Speedtest | **Low** | running | test failure, ntfy | DB |
| `portainer` | CT104 | Container UI | **Medium** | running | HTTP, auth | config |
| `music-pipeline` group | CT104 | Music automation | **Medium** | running | pipeline errors, disk | data |
| `sub-wave` group | CT104 | Subtitle automation | **Medium** | running | errors, disk | data |
| `yt-dlp` / `ytzero` | CT104 | Download automation | **Low** | running | failure, disk | downloads |
| `qbittorrent` | CT104 | Downloads | **Medium** | running | health, disk, VPN | downloads |
| `omniroute` | CT104 | Routing | **Medium** | running | HTTP, upstream | config |
| `telegram` / misc bots | CT104 | Notifications/automation | **Low** | running | restart, errors | tokens/config |

The full container list includes additional helper containers; not every helper needs a dedicated monitor.

---

## 3. Service monitoring model

Do not send a message for every container start/stop. Use three classes:

### A. Availability

- Docker health status (`healthy`/`unhealthy`) for containers that define a health check.
- HTTP status for services with a health endpoint.
- Port/process check for services without HTTP.
- Container restart count and OOM state.

### B. Functional health

- Database/queue connectivity for Authentik, Immich, n8n, Vaultwarden, Karakeep.
- Disk space for Immich uploads, filebrowser, downloads, ledger.
- Backup job result for data-bearing services.
- Authentication failure thresholds for Vaultwarden, Authentik, Guacamole, Portainer, code-server.

### C. Security

- Unexpected privileged container.
- Docker socket exposure.
- New public port or tunnel route.
- Image update failure/restart loop.
- CrowdSec decisions and bouncer status.

---

## 4. What can be monitored without new agents

| Source | Capability |
|---|---|
| Docker health status | Container-level liveness for images with `HEALTHCHECK` |
| Docker events | start/stop/die/restart/OOM/unhealthy |
| Docker stats | CPU/RAM/network per container |
| HTTP probes | availability/health endpoints |
| PVE API | guest status, CPU/RAM/disk/network |
| Host filesystem/df | capacity for bind mounts |
| Scrutiny/InfluxDB | SMART/disk history |
| Beszel | lightweight resource metrics |
| Healthchecks | cron/dead-man checks |
| Uptime Kuma | external HTTP/TLS/DNS checks |
| Application logs | errors, auth failures, queue failures |

No new agent is required to start. Add a guest-level agent only if a specific application metric cannot be obtained otherwise.

---

## 5. Recommended service topics

| Topic | Services |
|---|---|
| `rplab-services` | Docker health, key HTTP endpoints, Authentik, Vaultwarden, Immich, AdGuard, Guacamole, n8n, filebrowser, syncthing |
| `rplab-security` | Auth failure thresholds, CrowdSec, new exposure, privileged containers |
| `rplab-backups` | Duplicati, Immich DB/uploads, ledger, Vaultwarden DB |
| `rplab-web` | RPLab website only |
| `rplab-pve-ct` / `rplab-pve-vm` | Guest lifecycle, not application health |

Do not create one topic per application. If a service needs a separate stream later, split only after the volume justifies it.

---

## 6. Gaps and unknowns

- Uptime Kuma monitor definitions were not audited.
- Duplicati jobs and destinations were not audited.
- Many containers have no health check; their “running” state may hide application failure.
- Authentik health endpoint and worker queue depth were not verified.
- Immich database/queue health was not verified.
- Vaultwarden backup/restore was not verified.
- Public exposure of non-website services was not verified; assume private unless proven otherwise.
- No inventory of cron jobs inside guests was completed.

---

## 7. Findings

| ID | Severity | Finding |
|---|---|---|
| APP-01 | **High** | 56 containers on one Docker LXC; disk at 77%; a single host failure affects most services |
| APP-02 | **High** | Many services lack health checks; “running” is not “healthy” |
| APP-03 | **High** | Authentik/Vaultwarden/Immich are critical but their backup/restore status is unverified |
| APP-04 | **Medium** | Docker event notifications are noisy and currently mixed into the production homelab topic |
| APP-05 | **Medium** | No per-service identity for ntfy; all producers are anonymous |
| APP-06 | **Medium** | Update automation (watchtower) can restart services without a deployment alert |

---

## 8. Evidence commands

```text
pct exec 104 -- docker ps --format '{{.Names}} | {{.Image}} | {{.Status}}'
pct exec 104 -- docker inspect <name>
pct exec 104 -- docker stats --no-stream
pct exec 104 -- docker system df
```

No application database, user content or secret was read.
