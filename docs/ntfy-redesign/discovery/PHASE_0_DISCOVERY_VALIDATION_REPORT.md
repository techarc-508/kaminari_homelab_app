# Phase 0 Discovery Validation Report

**Phase:** 0 — discovery only  
**Date:** 2026-11-11  
**Change status:** read-only; no production service, account, token, topic, ACL, proxy, firewall or notification changed.

---

## 1. Validation checklist

| # | Check | Result | Evidence / limitation |
|---|---|---|---|
| 1 | ntfy host identified | **PASS** | Existing Docker LXC (CT104), container `ntfy` |
| 2 | ntfy version verified | **PASS** | 2.28.0 |
| 3 | Public URL verified | **PASS** | `https://ntfy.rplab.qzz.io` health 200 |
| 4 | Deprecated URL tested | **PASS** | `nfty.rplab.qzz.io` → 502 |
| 5 | Ports/interfaces identified | **PASS** | container 80 → host 8201; public through tunnel |
| 6 | Configuration location identified | **PASS** | no local `server.yml`; default `serve`; config API read |
| 7 | Auth mode identified | **PASS** | disabled; 0 users; no auth DB |
| 8 | User roles/tokens listed | **PASS** | none exist |
| 9 | Topic inventory | **PASS WITH LIMITS** | 3 confirmed producers; ntfy has no complete registry |
| 10 | ACL audit | **PASS** | none; anonymous read/write |
| 11 | Message cache/persistence | **PASS** | empty `/var/lib/ntfy`; memory cache |
| 12 | Attachments | **PASS** | none configured |
| 13 | Health endpoint | **PASS** | 200 |
| 14 | Metrics | **PASS** | none |
| 15 | Reverse proxy/TLS | **PASS WITH LIMITS** | Cloudflare tunnel; remote ingress rules not locally enumerable |
| 16 | DNS | **PASS** | Cloudflare; no DNSSEC audit |
| 17 | Service logs/restarts | **PASS** | running/unless-stopped; no recent errors |
| 18 | PVE node/guests | **PASS** | single node, 5 CT + 1 VM |
| 19 | Storage | **PASS** | LVM thin, ZFS, backup dir |
| 20 | SMART | **PASS** | sda uncorrectable counter elevated; overall PASSED |
| 21 | Backup jobs | **PASS** | CT104 and VM200 failing |
| 22 | Backup notifications | **PASS** | PVE webhook HTTP 400 |
| 23 | Website architecture | **PASS** | Express + SQLite + tunnel; health 200 |
| 24 | Website TLS | **PASS** | valid to 2026-12-25 |
| 25 | Application inventory | **PASS WITH LIMITS** | 56 containers; health checks vary |
| 26 | Network/security | **PASS WITH LIMITS** | PVE firewall disabled; CrowdSec no bouncer; router/WAF unknown |
| 27 | Existing monitoring tools | **PASS** | Uptime Kuma, Beszel, Scrutiny, Healthchecks, CrowdSec, Dozzle, Apprise |
| 28 | Monitoring opportunity matrix | **PASS** | 60+ opportunities, tiered |
| 29 | Proposed topics | **PASS** | 13 core + 2 conditional |
| 30 | Proposed ACL model | **PASS** | 3 primary identities + optional per-producer |
| 31 | No changes made | **PASS** | no account/token/topic/ACL/service change |

---

## 2. Verified facts

- ntfy **2.28.0** in Docker on CT104, public via Cloudflare Tunnel.
- Public health 200; old hostname 502.
- **Authentication disabled**; `users=0`; no auth DB; anonymous read/write.
- Cache is memory-only; data directory empty.
- Three confirmed topics: `KAMINARI-FUJI-Homelab`, `kaminari-diary`, `kaminari-onboarding-test`.
- PVE 9.2.11; six guests; all running.
- `backup-hdd` 83.25% full; CT104 and VM200 backups failing; PVE ntfy webhook returns 400.
- `safedrive` is a single HDD; SMART overall PASSED but 11,773 uncorrectable events.
- Website `rplab.qzz.io` 200; `/healthz` and `/api/health` 200; TLS valid to 2026-12-25.
- Docker stack has 56 running containers; many lack health checks.
- Uptime Kuma, Beszel, Scrutiny, Healthchecks, CrowdSec, Dozzle, Apprise exist.
- PVE firewall disabled; CrowdSec has no bouncer; no NUT/UPS evidence.

---

## 3. Inferred / not verified

| Item | Status |
|---|---|
| BGP/Cloudflare ingress rules | Not locally enumerable; remote-managed |
| Router/WAN failover | Not inspected |
| Uptime Kuma monitor list | Not audited |
| Duplicati jobs/destination | Not audited |
| Authentik policy/health details | Not fully audited |
| Immich DB/queue health | Not audited |
| Guest-agent liveness on VM200 | Not confirmed |
| UPS/NUT | No evidence |
| Android physical delivery | Not applicable to this phase; no device |
| Every topic ever used | ntfy has no complete registry |

---

## 4. Blockers to implementation

| Blocker | Impact | Required decision/action |
|---|---|---|
| Backup store nearly full and two backups failing | Cannot safely add data or persistence | Free space, add capacity, verify backups |
| ntfy webhook 400 | Backup failure alerts are unreliable | Diagnose/fix webhook before relying on it |
| No auth migration tested | Enabling auth can break all producers | Staged migration and rollback plan |
| No admin/app accounts | Cannot test ACLs | Owner approval to create |
| Cloudflare ingress not audited | Public exposure may be broader/narrower than assumed | Review remote rules |
| No external monitor outside CT104 | A CT104 outage can silence monitoring | Add external vantage point |
| No device | No physical notification validation | Owner device testing later |

---

## 5. Top risks

| # | Severity | Risk |
|---|---|---|
| 1 | **Critical** | Public unauthenticated ntfy read/write |
| 2 | **Critical** | CT104/VM200 backups failing; backup store 83% full |
| 3 | **Critical** | Single-disk ZFS pool; no off-site copy verified |
| 4 | **High** | PVE backup notification returns HTTP 400 |
| 5 | **High** | SMART uncorrectable counter 11,773 on aging HDD |
| 6 | **High** | PVE firewall disabled; CrowdSec has no bouncer |
| 7 | **High** | CT104 single host for 56 containers; 77% disk |
| 8 | **High** | CT103 single public ingress path |
| 9 | **High** | No external monitor outside CT104 |
| 10 | **Medium** | No ntfy persistence; restart loses cache |
| 11 | **Medium** | No admin account or revocation path |
| 12 | **Medium** | Topic names treated as secrets |
| 13 | **Medium** | Auth migration can break existing producers |
| 14 | **Medium** | No UPS/power monitoring evidence |
| 15 | **Low** | Deprecated `nfty` hostname still referenced |

---

## 6. What must be approved before implementation

1. Topic architecture and namespace.
2. Whether to create `kfadmin`, `kaminari_app`, `rplab_monitor`.
3. Whether to use one monitor identity or per-producer identities.
4. Whether to retire `KAMINARI-FUJI-Homelab` after migration or keep it.
5. Whether `kaminari-diary` stays outside the `rplab-` namespace.
6. Staged auth migration and rollback acceptance.
7. Backup remediation approach (free space/add disk/off-site) — this is a separate destructive-risk decision.

No implementation begins until these are approved.

---

## 7. Evidence limitations

- No message body was read.
- No token, password or private key was printed or stored.
- Private LAN addresses and host identifiers are redacted from the reports.
- Some configuration (Cloudflare ingress, router, Uptime Kuma monitors, Duplicati jobs) is not locally inspectable and is marked unknown.
- No test notification was published.
- No backup, restore, prune, scrub, firewall, DNS, proxy, ntfy, PVE or account change was executed.

---

## 8. Conclusion

**Discovery: PASS WITH LIMITATIONS.** The environment is sufficiently understood to propose a topic architecture and monitoring program. The proposal is safe to review but **not yet safe to implement blindly**: backup capacity, the PVE webhook failure, and the absence of ntfy authentication must be addressed in the order described in the executive summary. The next step is owner review, not deployment.
