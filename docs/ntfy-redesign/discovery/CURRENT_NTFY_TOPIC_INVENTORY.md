# Current ntfy Topic Inventory

**Discovery date:** 2026-11-11  
**Method:** producer configuration, container environments, PVE notification configuration, server statistics, and a read-only API query. No message body was opened.

---

## 1. What can and cannot be known

ntfy does **not** provide a complete historical registry of every topic ever used. `/v1/topics` and `/v1/messages` return 404 on this server. The only aggregate endpoint is `/v1/stats`, which reports counts, not names.

Therefore this inventory distinguishes:

- **Confirmed active** — a producer configuration or a known onboarding action names the topic and the server confirms active topics.
- **Confirmed historical** — a configuration or report references the topic, but no current producer/consumer evidence.
- **Inferred** — a candidate that might exist but was not found in current configuration.
- **Unknown** — cannot be determined without server-side instrumentation or authenticated admin metadata.

At discovery, server logs reported **3 active topics**. Three confirmed producers account for those topics.

---

## 2. Confirmed topics

### 2.1 `KAMINARI-FUJI-Homelab`

| Field | Value |
|---|---|
| Status | **Confirmed active production topic** |
| Publishing sources | PVE webhook `kaminari-ntfy`; `rplab-dashboard` (`kaminari-fuji-gate`) ledger notifications; `kaminari-docker-events`; PVE backup webhook; `myspeed` integration; request bot replies |
| Subscribers / consumers | RPLab dashboard, Android app, request bot, likely owner devices |
| Associated services | PVE, RPLab website, Docker stack, MySpeed, Docker events watcher |
| Current effective permission | Anonymous read/write (auth disabled) |
| Sensitivity | **High** — includes host health, backup failures, container lifecycle, website events and potentially personal diary-adjacent notifications |
| Frequency | Continuous; server reported 634 messages in the container lifetime |
| Active? | Yes |
| Retain? | **Retain during migration** as a compatibility topic; plan a clean cutover to `rplab-*` topics, then stop publishing after all consumers move |
| Conflict / risk | Overloaded domain: website, PVE, Docker and service events all share one topic; impossible to subscribe selectively; any producer can flood all consumers |

### 2.2 `kaminari-diary`

| Field | Value |
|---|---|
| Status | **Confirmed active** |
| Publishing source | `kaminari-diary` container (`NTFY_TOPIC=kaminari-diary`) |
| Subscribers / consumers | Not confirmed from accessible configuration; likely the diary owner |
| Associated service | Kaminari Diary |
| Current effective permission | Anonymous read/write |
| Sensitivity | **Personal / high** |
| Frequency | Unknown; not measured |
| Active? | Yes (server active-topic count) |
| Retain? | **Leave outside the infrastructure alert namespace**; do not reuse for monitoring |
| Conflict / risk | Personal data mixed with the public unauthenticated server; topic name is the only access control |

### 2.3 `kaminari-onboarding-test`

| Field | Value |
|---|---|
| Status | **Confirmed active test topic** |
| Publishing source | Android onboarding test; prior documented safe test |
| Subscribers / consumers | Android app test flow |
| Associated service | Kaminari Nfty Android |
| Current effective permission | Anonymous read/write |
| Sensitivity | Low (synthetic test content only) |
| Frequency | On demand |
| Active? | Yes |
| Retain? | Keep only during onboarding; retire or ACL-restrict after the app is configured |
| Conflict / risk | Must never receive production alerts; a test message must not enter a production topic |

---

## 3. Candidate topics investigated and not confirmed

| Candidate | Finding | Classification |
|---|---|---|
| `homelab-alerts` | No producer or consumer found in PVE, Docker, compose, systemd or app configuration | **Inferred / not present** |
| `iot-telemetry` | No IoT producer found | **Inferred / not present** |
| `backups` | Backups currently use the unified `KAMINARI-FUJI-Homelab` topic via PVE webhook; no dedicated `backups` topic found | **Not present as a separate topic** |
| `build-server` | No build server or CI producer found | **Inferred / not present** |
| `security` | No security producer found; CrowdSec is running but has no bouncer and no notification integration found | **Inferred / not present** |

These names must not be treated as live topics. A future implementation may create equivalent names in the `rplab-` namespace only after the producer exists.

---

## 4. Producer map

| Producer | Current destination | Trigger | Evidence |
|---|---|---|---|
| PVE notification webhook `kaminari-ntfy` | `KAMINARI-FUJI-Homelab` | PVE notification matcher `all` | `/etc/pve/notifications.cfg` |
| PVE backup task notification | `KAMINARI-FUJI-Homelab` | Backup task completion/failure | PVE task log shows HTTP 400 |
| RPLab dashboard (`rplab-dashboard`) | `KAMINARI-FUJI-Homelab` | Ledger events, tests, alerts, backups, updates | container env `NTFY_TOPIC`; `server/notify.js` |
| Docker event watcher | `KAMINARI-FUJI-Homelab` | container start/stop/restart/OOM/unhealthy | `tools/kaminari-events/events.sh` |
| MySpeed integration | `KAMINARI-FUJI-Homelab` (configured per integration) | speedtest finished/failed | `tools/myspeed/ntfy.js`; runtime integration setting not audited |
| Request bot | `KAMINARI-FUJI-Homelab` | `/help`, `/status`, `/search`, etc. | `server/requests.js` |
| `kaminari-diary` | `kaminari-diary` | diary activity | container env |
| Android onboarding | `kaminari-onboarding-test` | manual test | prior onboarding evidence |

**Important:** the PVE webhook body and headers are PVE-side configuration. The task log proves the request currently returns 400; the exact cause must be reproduced with a safe test only after approval. Do not publish a test message to a production topic to diagnose it.

---

## 5. Subscription map

| Consumer | Topics | Authentication | Notes |
|---|---|---|---|
| RPLab dashboard | `KAMINARI-FUJI-Homelab` | Anonymous (server has no auth) | Dashboard also publishes |
| Android app | `KAMINARI-FUJI-Homelab`, `kaminari-onboarding-test`, user-added topics | Guest / anonymous today | No auth token |
| Request bot | `KAMINARI-FUJI-Homelab` | Anonymous | Off by default unless `BOT_ENABLED=1` |
| Other owner devices | Unknown | Anonymous | Not enumerated |

No consumer has a distinct identity today. This is why the current environment cannot audit who read what.

---

## 6. Topic lifecycle recommendation

| Topic | Recommendation | Reason |
|---|---|---|
| `KAMINARI-FUJI-Homelab` | Keep as legacy during migration; stop new website/PVE/Docker producers after `rplab-*` is live; retire after all consumers move | Avoid breaking existing alerts; allow a controlled cutover |
| `kaminari-diary` | Leave untouched and outside the alert taxonomy | Personal, not infrastructure; do not repurpose |
| `kaminari-onboarding-test` | Keep for onboarding only; restrict or retire after migration | Test traffic must not mix with production |
| `rplab-*` | Create only in implementation stages, with explicit ACLs | New architecture |

Do not delete or rename any existing topic in Phase 0. Topic names are not access controls, but deleting a topic can break consumers; the owner must approve any retirement.

---

## 7. Gaps and unknowns

- The server cannot prove which topics exist historically.
- The exact active-topic names are inferred from producers plus `topics_active=3`; they are consistent with the three confirmed sources, but no server-side topic listing exists.
- Uptime Kuma and Duplicati may subscribe to topics not visible in configuration; their monitor/job configuration was not audited.
- The request bot and MySpeed runtime settings were not exercised.
- No message content was read; sensitivity ratings are based on producer purpose, not payload inspection.

---

## 8. Evidence commands

```text
grep -RIn 'NTFY_TOPIC|KAMINARI-FUJI-Homelab|kaminari-onboarding-test' /opt/stack /etc/pve
docker inspect <container> --format '{{range .Config.Env}}{{println .}}{{end}}'
curl -sS https://ntfy.rplab.qzz.io/v1/stats
cat /etc/pve/notifications.cfg
```

Sensitive values and private addresses are redacted in this report.
