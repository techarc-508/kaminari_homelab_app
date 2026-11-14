# Monitoring Opportunity Matrix

**Purpose:** turn the discovered environment into a prioritized, implementable monitoring backlog.  
**Rule:** ntfy carries **state changes and threshold breaches**, not raw metrics. Time-series stay in Beszel/Scrutiny/InfluxDB/PVE RRD.

Legend: **Sev** = Critical / High / Warning / Info. **Freq** = expected event frequency. **Cx** = implementation complexity (Low / Medium / High).

---

## Tier 1 — must have

| ID | Component | What to monitor | Detection source | Trigger | Sev | Proposed topic | Freq | Cx |
|---|---|---|---|---|---|---|---|---|
| T1-01 | PVE node | Node unreachable | External Uptime Kuma/API probe | 2 consecutive failures | Critical | `rplab-pve-host` | Rare | Low |
| T1-02 | ZFS pool | Pool degraded/faulted | `zpool status`/Scrutiny | any non-ONLINE or errors>0 | Critical | `rplab-storage` | Rare | Low |
| T1-03 | SMART | Disk failure/pending sectors | Scrutiny/smartctl | FAILED or critical attribute threshold | Critical | `rplab-storage` | Rare | Low |
| T1-04 | Backup store | Capacity exhausted | PVE storage API/df | `backup-hdd` >92% or free <10 GB | Critical | `rplab-backups` | Daily | Low |
| T1-05 | Backups | Job failed | PVE task log/Healthchecks | any non-OK vzdump status | Critical | `rplab-backups` | Per job | Medium |
| T1-06 | Backups | No successful backup | Healthchecks dead-man | no success in 26 h per guest | Critical | `rplab-backups` | Daily | Medium |
| T1-07 | Website | Public site down | Uptime Kuma | 2 failures from outside | Critical | `rplab-web` | Rare | Low |
| T1-08 | Website | Health endpoint down | `/healthz`, `/api/health` | non-200 or `ok=false` ×2 | Critical | `rplab-web` | Rare | Low |
| T1-09 | ntfy | Service down | External health probe | 2 non-200 / timeouts | Critical | `rplab-ntfy` | Rare | Low |
| T1-10 | Network | WAN down | External multi-target probe | 2 consecutive failures | Critical | `rplab-network` | Rare | Low |
| T1-11 | Network | DNS failure | External DNS probe | NXDOMAIN/SERVFAIL/wrong address | Critical | `rplab-network` | Rare | Low |
| T1-12 | Network | Cloudflare tunnel down | cloudflared metrics/health or external probe | disconnected >2 min | Critical | `rplab-network` | Rare | Low |
| T1-13 | CT104 | Docker host down | PVE API/guest probe | stopped/unreachable | Critical | `rplab-pve-ct` | Rare | Low |
| T1-14 | CT103 | Cloudflared guest down | PVE API/guest probe | stopped/unreachable | Critical | `rplab-network` | Rare | Low |
| T1-15 | VM200 | VM down | PVE API | stopped/unexpected | High | `rplab-pve-vm` | Rare | Low |

---

## Tier 2 — recommended

| ID | Component | What to monitor | Detection source | Trigger | Sev | Topic | Freq | Cx |
|---|---|---|---|---|---|---|---|---|
| T2-01 | Backup store | Capacity warning | PVE storage API/df | >85% used | Warning | `rplab-backups` | Daily | Low |
| T2-02 | Backup store | Backup write/quota error | PVE task log | `Disk quota exceeded`/`Broken pipe`/`No space` | High | `rplab-backups` | Per job | Medium |
| T2-03 | PVE notification | ntfy webhook 400 | PVE task log | `could not notify via target` | High | `rplab-ntfy` | Per failure | Medium |
| T2-04 | ZFS | Dataset capacity | PVE API/df | `safedrive/backup` >80% | Warning | `rplab-storage` | Daily | Low |
| T2-05 | Thin pool | Free space | PVE/LVM | VG free <15% | Warning | `rplab-storage` | Daily | Medium |
| T2-06 | ZFS | Scrub errors/overdue | `zpool status` | errors>0 or scrub >35 days | Warning | `rplab-storage` | Weekly | Low |
| T2-07 | SMART | Temperature | Scrutiny | >50 °C sustained 10 min | Warning | `rplab-storage` | Occasional | Low |
| T2-08 | PVE host | CPU pressure | PVE RRD/Beszel | >85% for 10 min | Warning | `rplab-pve-host` | Occasional | Low |
| T2-09 | PVE host | RAM pressure | PVE RRD/Beszel | >90% for 10 min | Warning | `rplab-pve-host` | Occasional | Low |
| T2-10 | PVE host | Root disk | df | >80% warn; >90% critical | Warning | `rplab-pve-host` | Daily | Low |
| T2-11 | CT104 | Disk | df inside CT | >80% warn; >90% critical | High | `rplab-pve-ct` | Daily | Low |
| T2-12 | CT102 | Immich upload path | df/mount | >80% | High | `rplab-services` | Daily | Low |
| T2-13 | CT100 | AdGuard DNS down | DNS query/process | resolution failure | High | `rplab-services` | Occasional | Medium |
| T2-14 | CT101 | Guacamole down | HTTP/auth | non-200 or auth failure spike | High | `rplab-services` | Occasional | Medium |
| T2-15 | CT102 | Immich down | HTTP/DB/queue | non-200 or queue stuck | High | `rplab-services` | Occasional | Medium |
| T2-16 | Docker | Container unhealthy | Docker health/events | `unhealthy` transition | High | `rplab-services` | Occasional | Low |
| T2-17 | Docker | Restart loop | Docker events | >3 restarts/10 min | High | `rplab-services` | Occasional | Low |
| T2-18 | Docker | OOM kill | Docker events | `oom` event | High | `rplab-services` | Occasional | Low |
| T2-19 | Website | 5xx burst | reverse proxy/log/probe | >5 5xx/5 min | High | `rplab-web` | Occasional | Medium |
| T2-20 | Website | TLS expiry | external TLS probe | <21 d warn; <7 d critical | High | `rplab-web` | Daily | Low |
| T2-21 | Website | Auth/gate outage | `/api/health`, Authentik | gate unavailable / SSO down | High | `rplab-web` | Occasional | Medium |
| T2-22 | Website | Failed unlock threshold | app ledger/auth log | >10 failures/5 min | High | `rplab-security` | Occasional | Medium |
| T2-23 | Website | Deployment failed | deploy script/watchtower | non-zero exit/restart loop | High | `rplab-web` | Per deploy | Medium |
| T2-24 | Website | Ledger DB failure | app log/sqlite check | write error/db locked | High | `rplab-web` | Occasional | Medium |
| T2-25 | Authentik | Service/queue down | HTTP/DB/Redis | non-200/queue depth | High | `rplab-services` | Occasional | Medium |
| T2-26 | Vaultwarden | Service/login failure | HTTP/auth log | down or failure spike | High | `rplab-services` | Occasional | Medium |
| T2-27 | n8n | Queue/DB failure | HTTP/DB | down/error spike | High | `rplab-services` | Occasional | Medium |
| T2-28 | SSH | Failed logins | auth log/fail2ban | >10/min per source | High | `rplab-security` | Occasional | Medium |
| T2-29 | PVE auth | Failed logins | PVE auth log | threshold exceeded | High | `rplab-security` | Occasional | Medium |
| T2-30 | CrowdSec | Decisions/bouncer | cscli/metrics | decisions >0 after enablement; bouncer absent | High | `rplab-security` | Occasional | Medium |
| T2-31 | cloudflared | Tunnel restarts | systemd/journal | >2 restarts/hour | Warning | `rplab-network` | Occasional | Low |
| T2-32 | ntfy | Restart/cache loss | container events/log | restart or cache loss | Warning | `rplab-ntfy` | Occasional | Low |
| T2-33 | ntfy | Publish/auth errors | logs/probe | 4xx/5xx burst | High | `rplab-ntfy` | Occasional | Medium |

---

## Tier 3 — advanced

| ID | Component | What to monitor | Detection source | Trigger | Sev | Topic | Freq | Cx |
|---|---|---|---|---|---|---|---|---|
| T3-01 | Daily digest | Infra summary | scheduled job | daily at quiet hour | Info | `rplab-digest` | Daily | Medium |
| T3-02 | ZFS | Capacity forecast | trend analysis | projected full <30 d | Warning | `rplab-storage` | Weekly | High |
| T3-03 | SMART | Replacement forecast | trend | attribute trend | Info | `rplab-storage` | Weekly | High |
| T3-04 | PVE | Update/reboot available | apt/PVE API | update available | Info | `rplab-pve-host` | Weekly | Medium |
| T3-05 | Docker | Image updates | watchtower | update applied/failed | Info | `rplab-services` | Per update | Low |
| T3-06 | Website | Traffic/error trend | Cloudflare/ledger | week-over-week anomaly | Info | `rplab-web` | Weekly | High |
| T3-07 | Network | Latency/jitter trend | Uptime Kuma/Beszel | sustained degradation | Warning | `rplab-network` | Weekly | High |
| T3-08 | Backup | Duration trend | task log | >150% baseline | Info | `rplab-backups` | Weekly | Medium |
| T3-09 | Backup | Restore drill overdue | schedule | >90 days | Warning | `rplab-backups` | Monthly | High |
| T3-10 | IoT | Device offline/online | IoT bridge | state change | Info | `rplab-iot` | Only if exists | High |
| T3-11 | Builds | Build/deploy failure | CI | failure | High | `rplab-builds` | Only if exists | High |
| T3-12 | Security | New listening port | host/guest scan | unexpected port | High | `rplab-security` | Weekly | Medium |
| T3-13 | Security | Firewall drift | config diff | change | High | `rplab-security` | On change | Medium |
| T3-14 | UPS | On battery/low battery | NUT | event | Critical | `rplab-pve-host` | Rare | High |
| T3-15 | DNS | Query anomaly | AdGuard metrics | unusual volume/domain | Warning | `rplab-security` | Weekly | High |

---

## Tier ranking rationale

- **Tier 1** items are the ones that cause silent data loss, total loss of observability, or total loss of public service. They must be implemented first.
- **Tier 2** items catch degradation before it becomes an outage. They need dedupe and cooldowns to avoid noise.
- **Tier 3** items are useful only after the basics are reliable. Do not build a digest before backup alerts work.

---

## Alert budget

| Topic | Expected messages/day (steady state) |
|---|---|
| `rplab-web` | 0–2 |
| `rplab-pve-host` | 0–3 |
| `rplab-pve-ct` | 0–3 |
| `rplab-pve-vm` | 0–2 |
| `rplab-storage` | 0–2 |
| `rplab-backups` | 1–4 (one per job, ideally only failures + daily success summary) |
| `rplab-network` | 0–2 |
| `rplab-services` | 0–5 (deduped) |
| `rplab-security` | 0–3 |
| `rplab-ntfy` | 0–1 |
| `rplab-command-*` | on demand |
| `rplab-digest` | 1 |

If a topic consistently exceeds these counts, fix the dedupe/threshold rather than muting the topic.

---

## Implementation order

1. T1-01, T1-02, T1-03, T1-04, T1-05, T1-06 (protect data and know when it fails).
2. T1-07, T1-08, T1-09, T1-10, T1-11, T1-12 (know when public services disappear).
3. T2-03 (fix the PVE alert path) before relying on T1-05.
4. T2-15…T2-33 (application/service coverage).
5. Tier 3 after 30 days of stable Tier 1/2 operation.

---

## Out of scope for this matrix

- Raw per-second metrics.
- Per-page-view or per-login notifications.
- Message content monitoring.
- Intrusion detection beyond CrowdSec/auth thresholds.
- Proxmox HA/failover (single node; no HA).
