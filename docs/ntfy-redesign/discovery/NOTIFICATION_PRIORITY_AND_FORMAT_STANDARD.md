# Notification Priority and Format Standard

**Purpose:** make every ntfy message predictable, actionable and quiet enough to trust.

---

## 1. Severity and priority mapping

| Severity | ntfy priority | Use for | Examples | Response expectation |
|---|---|---|---|---|
| **Critical** | **5** | Genuine failure with immediate impact or data-loss risk | PVE node down, ZFS degraded, public site down, backup failed, ntfy down | Act now |
| **High** | **4** | Urgent operational problem, degraded but not total loss | CT104 disk >90%, TLS <7 days, backup store >92%, auth outage | Act soon |
| **Warning** | **3** | Normal state change or threshold worth knowing | container restart, backup success, certificate 21-day warning, recovered | Review when convenient |
| **Info** | **2** | Informational, no action required | deployment succeeded, new image applied, guest started | No action |
| **Digest** | **1** | Summary/quiet notification | daily infra summary, weekly trend | Read at leisure |

**Never use priority 5 for a test message, a routine restart or a successful backup.** Priority inflation destroys trust.

---

## 2. Standard message format

### Title

`[COMPONENT] State · resource`

Examples:

- `[BACKUP] FAILED · CT104`
- `[WEB] DOWN · rplab.qzz.io`
- `[STORAGE] DEGRADED · safedrive`
- `[PVE] RECOVERED · pve`
- `[NTFY] RESTARTED · ntfy`

### Body

```text
Severity: Critical
Component: backup
Resource: CT104 (docker-stack)
Event: backup.failed
State: open
Time: 2026-11-11T02:00:31Z
Detail: zstd write error: Disk quota exceeded
Action: Check backup-hdd capacity and the CT104 job.
Link: <dashboard/PVE URL>
```

Fields:

| Field | Required | Notes |
|---|---|---|
| Severity | yes | Critical/High/Warning/Info |
| Component | yes | backup, storage, web, pve, network, services, security, ntfy |
| Resource | yes | CT/VM/guest/topic/service |
| Event | yes | stable dotted ID |
| State | yes | open, recovered, acknowledged |
| Time | yes | ISO-8601 UTC |
| Detail | yes if actionable | short; no secret, no full log |
| Action | recommended | one sentence |
| Link | optional | safe dashboard/PVE link |
| Correlation | for commands | request ID/nonce, never a secret |

---

## 3. Tags

Use a small consistent set:

| Tag | Meaning |
|---|---|
| `rotating_light` | critical |
| `warning` | high/warning |
| `white_check_mark` | recovered/OK |
| `floppy_disk` | storage |
| `minidisc` | backup |
| `globe_with_meridians` | website/network |
| `package` | Docker/service |
| `lock` | security |
| `bell` | ntfy |
| `bar_chart` | digest |

Do not use tags as the only severity signal; the title and body must carry meaning.

---

## 4. Deduplication and cooldowns

Every event should have a stable `dedupe_key`:

```text
backup:CT104
storage:safedrive
web:rplab.qzz.io:down
service:immich:down
pve:CT104:stopped
```

Rules:

1. **Open event** sends once.
2. Repeated identical open events are suppressed for a cooldown (default 30 min; critical 5 min).
3. **Recovery** sends once when the condition clears; include duration.
4. A new severity (e.g. warning → critical) may send immediately.
5. A resolved condition that reappears after the cooldown sends again.
6. Digest summarizes suppressed counts; it does not replay every message.

**Hysteresis:** use separate warn/clear thresholds. Example: disk warn at 80%, clear at 75%; do not flap at the boundary.

---

## 5. Recovery messages

A recovery must answer:

- What recovered?
- How long was it down?
- Is action still required?

Example:

```text
[BACKUP] RECOVERED · CT104
Severity: Info
State: recovered
Duration: 2 h 14 min
Detail: Last backup completed successfully at 04:14 UTC.
```

Do not send a recovery for a condition that was never alerted.

---

## 6. Digest and quiet hours

- **Daily digest:** one message at a quiet hour (e.g. 08:00 local) with counts: backups OK/failed, alerts open/recovered, capacity warnings, updates.
- **Quiet hours:** suppress Info/Warning notifications; never suppress Critical.
- **Maintenance windows:** explicit opt-in; suppress expected restarts and show the window in the digest.
- **No digest if nothing changed** (optional; avoid noise).

---

## 7. Click destinations

Safe links only:

- RPLab dashboard page for the component.
- PVE web UI for the guest/node.
- Documentation/troubleshooting page.
- Status page.

Do **not** use notification action buttons that execute shell commands, HTTP mutations, PVE power operations or token-bearing URLs. A click must be a human-opened page, not an action.

---

## 8. Topic-specific rules

| Topic | Rules |
|---|---|
| `rplab-web` | Never send page views or normal logins; only state/threshold/deploy events |
| `rplab-pve-host` | One message per state transition; no per-second metrics |
| `rplab-pve-ct` / `rplab-pve-vm` | Include guest ID and name; dedupe per guest |
| `rplab-storage` | Include pool/dataset/disk and exact threshold; no raw SMART dump |
| `rplab-backups` | One message per job result; daily success can be P2/digest, failure P4/P5 |
| `rplab-network` | Include target and failure type (DNS/TCP/TLS/tunnel) |
| `rplab-services` | Include container/service and health state; dedupe per service |
| `rplab-security` | Include source (redacted if needed), threshold and action; never include passwords/tokens |
| `rplab-ntfy` | Do not create a notification loop; if ntfy is down, the external monitor must alert through another channel |
| `rplab-command-request` | JSON envelope only, no prose; no secrets |
| `rplab-command-result` | Correlated ID, status, no secrets |
| `rplab-digest` | One message; counts and links, not a log dump |

---

## 9. Anti-patterns

| Anti-pattern | Problem |
|---|---|
| Priority 5 for every event | Alert fatigue; real failures ignored |
| One topic per container | Topic explosion; unusable subscriptions |
| Sending raw logs | Noise, possible secrets |
| No recovery message | Operators cannot tell whether it is fixed |
| No dedupe | Restart loops flood the phone |
| Destructive action buttons | Security risk; violates command boundary |
| Using ntfy as the only monitoring store | Cache is volatile; no history after restart |
| Using topic name as a password | Anyone who knows it can read/write today |

---

## 10. Example messages

### Critical: ZFS degraded

```text
Title: [STORAGE] DEGRADED · safedrive
Body:
Severity: Critical
Component: storage
Resource: safedrive (single HDD)
Event: zfs.degraded
State: open
Time: 2026-11-11T15:28:00Z
Detail: Pool state DEGRADED; errors: 12 read, 0 write, 0 checksum.
Action: Stop writes; inspect disk/cable; verify backup.
Tags: rotating_light, floppy_disk
```

### Warning: backup capacity

```text
Title: [BACKUP] WARNING · backup-hdd 84%
Body:
Severity: Warning
Component: backup
Resource: backup-hdd
Event: backup.capacity
State: open
Time: ...
Detail: 125 GB used of 150 GB; 25.1 GB free.
Action: Free space or add capacity before the next VM200 backup.
Tags: warning, minidisc
```

### Recovery

```text
Title: [WEB] RECOVERED · rplab.qzz.io
Body:
Severity: Info
Component: web
Resource: rplab.qzz.io
Event: web.down
State: recovered
Duration: 4 min
Detail: /healthz 200; TLS valid.
Tags: white_check_mark, globe_with_meridians
```

---

## 11. Approval

Approve this format and priority policy before implementation. It is intentionally conservative: fewer, better messages are more useful than a complete stream of metrics.
