# Current ntfy Server Audit

**Discovery date:** 2026-11-11 15:28 UTC  
**Status:** read-only; no service, container, configuration or database was changed.

---

## 1. Identity and hosting

| Item | Verified value |
|---|---|
| Public URL | `https://ntfy.rplab.qzz.io` |
| Legacy URL | `https://nfty.rplab.qzz.io` — returns HTTP **502**; do not use |
| Host | Existing Docker LXC, CT104 (`docker-stack`) on PVE node `pve` |
| Container | `ntfy` |
| Image | `binwiederhier/ntfy` (untagged) |
| Version | **ntfy 2.28.0** |
| Process | `ntfy serve` inside the container |
| Port | Container 80 → host **8201** (0.0.0.0 and IPv6) |
| Restart policy | `unless-stopped` |
| Uptime at discovery | 10 days |
| Data volume | `utilities_ntfy_data` → `/var/lib/ntfy` |
| Public TLS | Cloudflare edge; origin is HTTP through the existing tunnel |
| Health | `GET /v1/health` → **200**, `{"healthy":true}` |

The public host is reached through the existing Cloudflare Tunnel client (CT103). The tunnel is token-based and has no local `config.yml` ingress file; remote ingress rules therefore could not be enumerated from the host. This is recorded as an unknown, not as a failure.

---

## 2. Version and configuration

`GET https://ntfy.rplab.qzz.io/v1/config` returned these non-secret values at discovery:

| Key | Value |
|---|---|
| `base_url` | empty |
| `app_root` | `/` |
| `enable_login` | `false` |
| `enable_signup` | `false` |
| `require_login` | `false` |
| `enable_web_push` | `false` |
| `enable_emails` | `false` |
| `enable_calls` | `false` |
| `enable_reservations` | `false` |
| `disallowed_topics` | `docs`, `static`, `file`, `app`, `metrics`, `account`, `settings`, `signup`, `login`, `v1` |
| `cache_duration` | not set in config |
| `auth_file` | `null` |
| `metrics_listen_url` | `null` |
| `attachment_cache_dir` | `null` |

**Verified:** no explicit authentication, attachment, cache or metrics configuration is present. The container runs with only `PATH` in its environment and the default `serve` command. No `/etc/ntfy/server.yml` exists; configuration is effectively server defaults.

**Inferred:** because the data directory contains no files, message caching is memory-backed and is lost when the container restarts. The server statistics showing cached messages are consistent with the in-memory cache, not a persistent store.

---

## 3. Authentication and users

| Item | Verified state |
|---|---|
| Auth database | none found (`/var/lib/ntfy` empty) |
| Users | **0** (`users=0` in server stats) |
| Admin user | none |
| Tokens | none |
| ACLs | none |
| Anonymous read | allowed for any topic |
| Anonymous publish | allowed for any topic |
| Sign-up | disabled |
| Login | disabled |

This is the critical access-control finding: **topic names are the only access control**. A known topic such as `KAMINARI-FUJI-Homelab` or `kaminari-diary` can be read or written by anyone who knows or guesses the name. Do not treat topic names as secrets.

---

## 4. Message cache, persistence and attachments

| Item | Verified state |
|---|---|
| Persistent cache file | none |
| Data directory | `/var/lib/ntfy`, empty |
| Cache type | in-memory (default) |
| Attachments | no attachment cache directory configured |
| Message size/rate limits | no explicit override |
| Metrics | no `/metrics`; `metrics_listen_url` null |
| History API | no complete historical topic registry; `/v1/stats` only |

**Consequence:** a restart loses cached notifications. The Android app must not rely on ntfy as its durable history store; Room remains the source of truth. Historical synchronization after a restart is limited to whatever the server still has in memory and to the app's own database.

**Do not enable** `/metrics` on the public listener. If metrics are added later, bind them to a private address and scrape only from the LAN/Tailscale path.

---

## 5. Health, logs and service behavior

- `GET /v1/health` → 200.
- Server logs show one stats line per minute: `users=0`, `messages_cached=37`, `messages_published=634`, `topics_active=3`, `subscribers=1`, `visitors=4` at 15:21 UTC.
- Container restart policy is `unless-stopped`; no recent restart observed.
- No ntfy error lines were found in the log sample. A PVE task log separately shows an HTTP **400** from the ntfy webhook path; this is a producer-side/compatibility failure, not an ntfy health failure.

---

## 6. Network and TLS

| Item | Verified / inferred |
|---|---|
| DNS | `ntfy.rplab.qzz.io` resolves to Cloudflare addresses |
| TLS | Cloudflare/Google Trust Services; valid certificate observed |
| Origin | HTTP inside the private LAN; TLS terminates at Cloudflare |
| Public path | Cloudflare Tunnel from CT103 to the Docker LXC |
| LAN path | HTTP origin exists; Android app enforces HTTPS and Android blocks cleartext, so the app should use the public HTTPS URL |
| Old hostname | `nfty.rplab.qzz.io` → **502** |

---

## 7. Backup and recovery of ntfy itself

There is no separate ntfy database to back up today because authentication is disabled and the cache is memory-only. The Docker volume is empty. If authentication or a persistent cache is enabled later:

- back up the auth database and any cache/attachment directory before changes;
- store the backup outside the same `safedrive` disk;
- test restore on a non-production port;
- record the exact restore procedure in the private owner file.

**Current backup gap:** the PVE backup storage is already under pressure (`backup-hdd` 83.25% full) and CT104/VM200 backups are failing. Do not add a persistent ntfy database without first fixing backup capacity.

---

## 8. Findings and severity

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| NTFY-01 | **Critical** | Public unauthenticated read/write to all topics | `/v1/config`, `users=0` |
| NTFY-02 | **High** | No persistent cache; restart loses messages | empty `/var/lib/ntfy`; container config |
| NTFY-03 | **High** | PVE backup notification returns HTTP 400 | PVE task log |
| NTFY-04 | **High** | No explicit rate limits/metrics; public anonymous publish can be abused | config; no metrics listener |
| NTFY-05 | **Medium** | No auth database backup exists because no auth exists; future migration has no rollback data | current state |
| NTFY-06 | **Medium** | Public origin is HTTP; security depends on Cloudflare and tunnel | service mapping |
| NTFY-07 | **Low** | Deprecated hostname still referenced in old documentation | 502 probe |

---

## 9. Evidence commands

```text
docker inspect ntfy
docker exec ntfy ntfy --version
docker logs --tail 50 ntfy
curl -sS https://ntfy.rplab.qzz.io/v1/config
curl -sS https://ntfy.rplab.qzz.io/v1/health
curl -sS https://ntfy.rplab.qzz.io/v1/stats
pvesh get /nodes/pve/tasks/<UPID>/log
```

No message bodies, token values, passwords or private keys were read or included.

---

## 10. Recommendation

Do not change the service in Phase 0. In implementation:

1. Repair backup capacity and confirm a successful CT104/VM200 backup before touching ntfy persistence.
2. Create the admin account `kfadmin` only after a safe auth backup and a tested producer-token migration plan.
3. Keep the existing anonymous behavior during the migration; switch to default-deny only when every producer and the Android app has been verified.
4. Keep the public health endpoint; do not expose metrics publicly.
5. Fix the PVE webhook 400 before relying on it for failure alerts.
