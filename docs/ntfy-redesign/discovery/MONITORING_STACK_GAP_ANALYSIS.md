# Monitoring Stack Gap Analysis

**Question:** what can be reused, what is missing, and what must not be duplicated?

---

## 1. Existing tools

| Tool | What it provides | State at discovery | Reuse for |
|---|---|---|---|
| PVE native | Node/guest status, RRD, task history, storage, backup jobs | Running | Node, guest, storage and backup signals |
| Uptime Kuma | External HTTP/TCP/DNS/TLS checks | Container healthy | Website, ntfy, tunnel, WAN, TLS |
| Beszel | Lightweight host/container metrics | Running (no health check) | CPU/RAM/disk trends |
| Scrutiny + InfluxDB | SMART/disk health history | Web healthy | Disk failure, temperature, attributes |
| Healthchecks | Cron/dead-man checks | Running | Backup success, scheduled jobs |
| CrowdSec | Security detection | Running; **no bouncer** | Auth/abuse detection, later enforcement |
| Dozzle | Container logs | Running | Human troubleshooting, not alerting |
| Apprise | Notification abstraction | Running | Fallback/email routing if needed |
| `smartmontools` | SMART CLI | Running on host | Direct disk checks |
| Docker health/events | Container liveness/lifecycle | Running | Service alerting |
| Website `/healthz`, `/api/health` | Application health | 200 | Website monitoring |

**Not found:** Prometheus, Grafana, Netdata, Gatus, Zabbix, node_exporter, Telegraf, NUT/UPS, a second monitoring stack.

---

## 2. Gaps that matter

| Gap | Impact | Best fix | Do not do |
|---|---|---|---|
| **No authenticated ntfy** | Anyone can read/write known topics | Staged auth + ACLs | Do not enable default-deny before migrating producers |
| **No backup capacity alert** | Silent failures until quota | PVE storage/task monitor → `rplab-backups` | Do not add another backup tool |
| **No backup success dead-man** | A missing job looks like “no news” | Healthchecks ping on success | Do not rely only on failure logs |
| **No external TLS/DNS check** | Certificate/DNS failure invisible until users complain | Uptime Kuma TLS/DNS monitors | Do not scrape certs only from origin |
| **No PVE webhook health** | The alert path itself returns 400 | Parse task log; self-monitor webhook | Do not assume PVE notification works |
| **No CrowdSec enforcement** | Detection without blocking | Register bouncer after testing | Do not enable a bouncer blindly and lock out SSH |
| **No host firewall** | Flat LAN exposure | Documented firewall rollout with allowlist | Do not enable without console access/rollback |
| **No UPS monitoring** | Power loss is silent | Add NUT only if UPS exists | Do not invent a UPS |
| **No off-site backup verified** | Single-disk loss destroys source + backup | External/NAS/off-site target | Do not claim Duplicati covers it without evidence |
| **No restore drill** | Backup success ≠ restore success | Quarterly restore test | Do not run destructive restore on production |
| **Many containers lack health checks** | “Running” hides failure | Add health checks where supported | Do not add an agent per container first |
| **No centralized log correlation** | Slow root-cause analysis | Dozzle + structured logs; later Loki only if needed | Do not deploy a full ELK stack now |
| **No per-producer ntfy identity** | No least privilege/revocation | `rplab_monitor` and optional per-producer users | Do not give everyone the admin token |

---

## 3. What to reuse first

### Website

- Uptime Kuma: `/`, `/healthz`, TLS expiry, DNS.
- Docker health: `rplab-dashboard`.
- Website ledger: deployment/auth events (server-side only).
- Cloudflare: tunnel/edge signals where available.

### PVE and guests

- PVE task log: backup and guest events.
- PVE RRD/API: CPU/RAM/disk/network.
- `pct`/`qm` state: guest lifecycle.
- Healthchecks: dead-man for backup success.

### Storage

- Scrutiny/InfluxDB: SMART history.
- `zpool status`/`zfs list`: pool/dataset state.
- `df`/PVE storage API: capacity.
- `smartctl`: direct SMART check.

### Services

- Docker health/events: liveness/lifecycle.
- HTTP probes: functional health.
- Beszel: resource trends.
- Dozzle: human logs.
- Apprise: fallback routing if ntfy is down.

---

## 4. What not to add yet

| Proposed tool | Verdict | Reason |
|---|---|---|
| Prometheus + Grafana | **Defer** | Beszel/Scrutiny/PVE already cover current needs; adds RAM/disk/ops burden |
| Netdata | **Defer** | Duplicates Beszel; no clear gap |
| Gatus | **Defer** | Uptime Kuma already provides synthetic checks |
| ELK/Loki | **Defer** | Dozzle is enough for current scale; revisit with log volume |
| New ntfy server | **Reject** | Existing server is healthy; redesign is configuration/monitoring, not migration |
| New LXC/VM | **Reject** | Reuse CT104/CT103 and existing services |
| Per-container ntfy topics | **Reject** | Topic explosion; use `rplab-services` + fields |
| Agent per guest | **Defer** | PVE API covers lifecycle; add only for a specific missing metric |

---

## 5. Recommended minimal implementation stack

```text
Availability:
  Uptime Kuma → rplab-web, rplab-network, rplab-ntfy

Dead-man / scheduled jobs:
  Healthchecks → rplab-backups, rplab-storage (scrub), rplab-digest

Host/guest:
  PVE API + small script/timer → rplab-pve-host, rplab-pve-ct, rplab-pve-vm

Storage:
  Scrutiny webhook or small poller → rplab-storage
  PVE storage API → rplab-backups/storage capacity

Services:
  Docker events/health + HTTP probes → rplab-services

Security:
  CrowdSec + auth log parser → rplab-security

Website:
  Uptime Kuma + website deploy/ledger events → rplab-web

Digest:
  scheduled script → rplab-digest
```

Each producer publishes only its domain topic. No producer publishes to a generic catch-all.

---

## 6. Alert-path reliability

The monitoring stack must not depend on the thing it monitors:

- If **ntfy** is down, the external monitor must alert through email/Apprise/another channel or a second ntfy instance. Do not rely on ntfy to report its own outage.
- If **Cloudflare/tunnel** is down, the external Uptime Kuma must be outside the tunnel path.
- If **CT104** is down, the monitor must run outside CT104 (Uptime Kuma inside CT104 cannot report its own host outage).
- If **PVE** is down, an external probe must detect it.
- If the **backup store** is full, the alert must not require writing a backup.

**Current risk:** Uptime Kuma runs inside CT104, the same host as ntfy and most services. A CT104 outage can silence both the services and the monitor. Add at least one external check (phone/another host/cloud) for `rplab-web`, `rplab-ntfy`, and the tunnel.

---

## 7. Metrics vs notifications

| Data | Where it belongs |
|---|---|
| CPU/RAM/disk history | Beszel/PVE RRD/InfluxDB |
| SMART attributes | Scrutiny/InfluxDB |
| Request counts/latency | Uptime Kuma/Cloudflare/website ledger |
| Container logs | Dozzle |
| State transitions | ntfy |
| Threshold breaches | ntfy |
| Daily summary | ntfy digest |
| Raw metrics | **not ntfy** |

---

## 8. Gap-closing roadmap

| Stage | Add | Gate |
|---|---|---|
| 1 | Fix backup capacity; repair CT104/VM200; fix PVE webhook 400 | Two successful backups; test alert delivered |
| 2 | ntfy accounts/ACLs/topics; producer tokens | Each producer tested; unauthorized access denied |
| 3 | External Uptime Kuma checks + TLS/DNS; Healthchecks dead-man | Alert + recovery demonstrated |
| 4 | PVE guest/storage scripts; backup task parser | Failure and success both notify |
| 5 | Docker/service health + HTTP probes | Dedupe and cooldown proven |
| 6 | CrowdSec bouncer + auth thresholds | Lockout recovery tested |
| 7 | Digest + quiet hours | Owner review |

---

## 9. Decision

The environment is already capable of a professional monitoring baseline **without a new stack**. The real gaps are configuration, identity, backup health and external vantage points—not missing software. Approve reuse-first, and add new tools only when a measured gap remains after Stages 1–5.
