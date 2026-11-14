# LIVE ntfy Inventory — Freebuff Phase 0

**Re-probed:** 2026-11-12 12:35 UTC
**Mode:** read-only. No publish, subscribe, login, admin call, account, token, ACL, restart or configuration change.
**Scope note:** this file supersedes nothing. It re-verifies the live surface and separates what was observed today from what is carried forward from the 2026-11-11 audit.

---

## 1. Endpoint and identity

| Item | Value | Status |
|---|---|---|
| Canonical URL | `https://ntfy.rplab.qzz.io` | **Verified** today |
| Obsolete spelling | `https://nfty.rplab.qzz.io` | **Verified 502** today — not interchangeable |
| DNS | Cloudflare fronted; `ntfy` resolves to Cloudflare IPv6 addresses | Verified |
| TLS | subject `CN = rplab.qzz.io`; issuer Google Trust Services `WE1`; valid 2026-09-26 → **2026-12-25** | Verified today |
| Health | `GET /v1/health` → `200 {"healthy":true}` | Verified today |
| Server header | `server: cloudflare`; HTTP/2 | Verified today |
| `HEAD /v1/health` | `404` | Observed; use `GET` for probes |
| Version | **ntfy 2.28.0** | Carried forward from the 2026-11-11 container probe; **not** re-probed today (version is not exposed by `/v1/config`) |
| Host | Docker container `ntfy` (`binwiederhier/ntfy`) on CT104 Docker LXC | Carried forward; not re-inspected today |
| Port mapping | container `80` → host `8201` | Carried forward |
| Volume | `utilities_ntfy_data` → `/var/lib/ntfy` | Carried forward |
| Restart policy | `unless-stopped` | Carried forward |

---

## 2. Live configuration (observed today)

`GET /v1/config` returned:

```json
{
  "base_url": "",
  "app_root": "/",
  "enable_login": false,
  "require_login": false,
  "enable_signup": false,
  "enable_payments": false,
  "enable_calls": false,
  "enable_emails": false,
  "enable_reset_password": false,
  "enable_reservations": false,
  "enable_web_push": false,
  "billing_contact": "",
  "web_push_public_key": "",
  "disallowed_topics": ["docs","static","file","app","metrics","account","settings","signup","login","v1"],
  "config_hash": "9f6d36d8da7eb49b0815b6dfb03b64df5a4318d40d4f5dd1a9b0e5620db1915a"
}
```

Interpretation (verified, not inferred):

- **Authentication is disabled.** There is no login, no required auth and no signup.
- **No auth file is configured** (the field is absent, consistent with `auth_file: null` from the earlier audit).
- **No attachments, cache, metrics or web-push configuration** is exposed.
- The config hash is recorded so a later change can be detected.

---

## 3. Server counters (observed today)

`GET /v1/stats` returned:

```json
{"messages":688,"messages_rate":0.001851851851851852}
```

A second probe at **19:29 IST** returned `{"messages":689,"messages_rate":0}`.

The 2026-11-11 audit recorded **634** published messages. The delta (**+54**, then **+55**) confirms producers continued publishing between the probes. This is evidence of activity, not a claim about which topics or whether delivery succeeded.

The earlier detailed stats line (`users=0`, `topics_active=3`, `messages_cached`, `subscribers`) came from container logs, not this API response, and was **not** re-observed today.

---

## 4. Access-control state

| Control | State | Evidence |
|---|---|---|
| Authentication | Disabled | `/v1/config` |
| Users | 0 (carried forward; consistent with disabled login) | prior container stats `/v1/stats` did not expose `users` today |
| Admin account | None | no auth file / no users |
| Tokens | None | no auth database |
| Per-topic ACLs | None | no auth database |
| Anonymous read | Allowed for any valid, non-disallowed topic | configuration |
| Anonymous publish | Allowed for any valid, non-disallowed topic | configuration; prior verified publish |
| Sign-up / login / reset | Disabled | `/v1/config` |
| `auth-default-access` | Not configured | no `server.yml` / auth file |

**Consequence:** topic names are the only access control today. This is the root problem the redesign addresses. No test publish was performed to re-prove anonymous write, because a write would alter production state.

---

## 5. Verified / active topics

ntfy has **no complete topic registry**. The following is based on producer configuration and prior direct verification; it is the honest limit of what can be known without server-side metadata.

| Topic | Status | Producers | Notes |
|---|---|---|---|
| `KAMINARI-FUJI-Homelab` | **Active production** | PVE webhook `kaminari-ntfy`; `rplab-dashboard`; `kaminari-docker-events`; watchtower; MySpeed; request bot | Overloaded domain: host, CT/VM, backups, website, Docker, services |
| `kaminari-diary` | **Active** | `kaminari-diary` container | Personal; must stay outside the infrastructure namespace |
| `kaminari-onboarding-test` | **Active test** | Android onboarding / manual tests | Safe test topic; must never receive production alerts |
| `homelab-alerts` | **Not a live topic** | none found | App sample data only |
| `iot-telemetry` | **Not a live topic** | none found | App sample data only |
| `backups` | **Not a separate topic** | n/a | Backups publish into `KAMINARI-FUJI-Homelab` |
| `build-server` | **Not a live topic** | none found | App sample data only |
| `security` | **Not a live topic** | none found | App sample data only |

### 5.1 The 22 approved `kf-` topics

**No evidence that any of the 22 topics has ever carried a message.** ntfy has no topic registry, so existence cannot be enumerated from the API; this conclusion rests on the absence of any producer, client or configuration in the repository that publishes or subscribes to a `kf-` topic. A repository-wide search returns zero `kf-` references in `app/src` and `gateway/app`; outside the design documents they appear only in `PVE_NOTIFICATION_REPAIR_REPORT.md` as future intent.

| # | Topic | Live state |
|---:|---|---|
| 1–11 | `kf-web`, `kf-pve-host`, `kf-pve-ct`, `kf-pve-vm`, `kf-storage`, `kf-backups`, `kf-network`, `kf-services`, `kf-security`, `kf-ntfy`, `kf-digest` | **Not created** |
| 12–13 | `kf-music-pipeline`, `kf-music-reports` | **Not created** |
| 14–15 | `kf-stickers-pipeline`, `kf-stickers-reports` | **Not created** (reserved) |
| 16–17 | `kf-news`, `kf-news-reports` | **Not created** |
| 18–20 | `kf-command-request`, `kf-command-result`, `kf-admin-audit` | **Not created** |
| 21–22 | `kf-diary`, `kf-iot` | **Not created** |

---

## 6. Producer map (current, from configuration evidence)

| Producer | Destination today | Future destination | Identity today | Future identity |
|---|---|---|---|---|
| PVE notification webhook `kaminari-ntfy` | `KAMINARI-FUJI-Homelab` | `kf-pve-host` / `kf-pve-ct` / `kf-pve-vm` / `kf-backups` by matcher | anonymous | `kf_monitor` |
| PVE backup task notification | `KAMINARI-FUJI-Homelab` | `kf-backups` | anonymous | `kf_monitor` |
| `rplab-dashboard` website | `KAMINARI-FUJI-Homelab` | `kf-web` | anonymous | unresolved (C-18) |
| `kaminari-docker-events` | `KAMINARI-FUJI-Homelab` | `kf-services` (and `kf-pve-ct` for lifecycle if opted) | anonymous | `kf_monitor` |
| watchtower | `KAMINARI-FUJI-Homelab` | `kf-services` | anonymous | `kf_monitor` |
| MySpeed | `KAMINARI-FUJI-Homelab` | `kf-network` | anonymous | `kf_monitor` |
| request bot | `KAMINARI-FUJI-Homelab` | `kf-services` or `kf-web` (owner choice) | anonymous | `kf_monitor` |
| `kaminari-diary` | `kaminari-diary` | `kf-diary` | anonymous | `kf_stickers` is **not** the diary publisher; unresolved (C-06) |
| Music pipeline (existing) | none / app notifications | `kf-music-pipeline`, `kf-music-reports` | none | `kf_music` |
| News pipeline | none | `kf-news`, `kf-news-reports` | none | `kf_news` |
| Stickers pipeline | none (does not exist) | `kf-stickers-*` reserved | none | `kf_stickers` (future) |

The producer list is carried forward from the 2026-11-11 configuration audit; the live containers were not re-read today.

---

## 7. Consumer map

| Consumer | Topics | Auth today | Target |
|---|---|---|---|
| Android app | user-added only; no default seeded topic; sample names are not live | Guest | `kaminari_app` token; read alert topics + command results/audit |
| RPLab dashboard | `KAMINARI-FUJI-Homelab` | anonymous | `kaminari_app` or dedicated read identity |
| Request bot | `KAMINARI-FUJI-Homelab` | anonymous | `kaminari_app` or `kf_monitor` (read) |
| Owner devices | unknown | anonymous | `kaminari_app` |
| Gateway | none today | none | reads `kf-command-request`; writes result/audit (P-02) |

**Correction to a common assumption:** the checked-in Android app does **not** subscribe to `KAMINARI-FUJI-Homelab` by default. It seeds no topics; the sample topic names (`homelab-alerts`, `backups`, `build-server`, `iot-telemetry`, `security`) are not live. A first run is empty until the user adds a topic. This matters for migration: the app will not silently receive the legacy stream.

---

## 8. Persistence, retention and limits

| Item | State |
|---|---|
| Message cache | in-memory; no persistent cache file observed in the earlier container audit |
| Restart behaviour | cached messages are expected to be lost on restart; not re-tested today |
| Attachments | no attachment cache configured; not used by the app |
| Message size limit | default (4 KB class); PVE's full-log body already caused HTTP 400 and was reduced to `{{ title }}` |
| Retention | server default cache window only; the per-topic retention values in the catalogue are **requirements**, not live server settings |
| Metrics | not exposed (`/metrics` is a disallowed topic; no metrics listener) |
| Rate limits | no explicit override observed |

---

## 9. Unverified / unknown

1. Exact live user count today (login disabled; stats did not expose it).
2. Container identity, volume contents and version on this date (carried forward).
3. Any topic not named by a known producer or consumer.
4. Cloudflare-side Access/WAF rules for the ntfy hostname.
5. Uptime Kuma / Duplicati / other consumers that may hold hidden subscriptions.
6. Whether anonymous write still succeeds today (deliberately not tested, to avoid a production write).
7. `auth-default-access` behaviour on 2.28.0 and wildcard precedence — must be tested in a controlled stage, not assumed.

---

## 10. Commands actually used

```text
getent hosts ntfy.rplab.qzz.io nfty.rplab.qzz.io
curl -sS -m 15 https://ntfy.rplab.qzz.io/v1/health
curl -sS -m 15 https://ntfy.rplab.qzz.io/v1/config
curl -sS -m 15 https://ntfy.rplab.qzz.io/v1/stats
curl -sS -m 15 -o /dev/null -w '%{http_code}' https://nfty.rplab.qzz.io/v1/health
curl -sSI -m 15 https://ntfy.rplab.qzz.io/v1/health
openssl s_client -servername ntfy.rplab.qzz.io -connect ntfy.rplab.qzz.io:443
grep -RIn 'kf-*' .            # repository-wide implementation check
```

No message body was read, no message was published and no credential was used.
