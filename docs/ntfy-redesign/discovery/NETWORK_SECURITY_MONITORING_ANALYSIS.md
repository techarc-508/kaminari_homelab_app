# Network and Security Monitoring Analysis

**Discovery date:** 2026-11-11  
**Status:** read-only. No firewall, DNS, tunnel, WAF, router or authentication setting was changed.

---

## 1. Verified network topology

```text
Internet
  ├─ Cloudflare DNS / TLS / WAF (public hostnames)
  │    └─ Cloudflare Tunnel (CT103, token file, no local ingress config)
  │         ├─ rplab.qzz.io        → rplab-dashboard :8484 (CT104)
  │         └─ ntfy.rplab.qzz.io   → ntfy :8201 → container :80 (CT104)
  │
  ├─ PVE host (single node)
  │    ├─ Tailscale
  │    ├─ PVE web/API on the private LAN
  │    └─ SSH
  │
  └─ LAN guests (CT100–CT104, VM200)
       ├─ AdGuard DNS
       ├─ Immich
       ├─ Guacamole
       └─ Docker stack
```

| Item | Verified state |
|---|---|
| Public DNS | Cloudflare |
| Public TLS | Cloudflare edge; certificate valid to 2026-12-25 |
| Tunnel | cloudflared 2026.x in CT103, token-based, `Restart=on-failure` |
| Origin HTTP | ntfy and website are HTTP behind the tunnel |
| LAN DNS | AdGuard LXC (single dependency) |
| VPN | Tailscale active on the PVE host |
| PVE firewall | **disabled/running** |
| Host fail2ban | not found |
| CrowdSec | container running; **no registered bouncers** |
| WAN router | not inspected; unknown model, port-forwarding and failover |
| UPS/NUT | no NUT service found |
| Public PVE API | not directly verified; PVE web on private LAN |

---

## 2. What is exposed

| Exposure | Path | Risk |
|---|---|---|
| Website | Cloudflare → tunnel → dashboard | Public by design; auth gate protects data routes |
| ntfy | Cloudflare → tunnel → ntfy | **Public and unauthenticated**; anyone with a topic name can read/write |
| PVE web/API | Private LAN / Tailscale | High-value admin surface; host firewall disabled |
| SSH | PVE host and guests | Key-based access observed; no host fail2ban |
| AdGuard DNS | LAN | Single point of failure; DNS outage looks like WAN outage |
| Docker API | Local socket only (inferred) | Dozzle and event watcher use it; do not expose remotely |

---

## 3. Current security findings

| ID | Severity | Finding |
|---|---|---|
| NET-01 | **Critical** | ntfy is publicly readable/writable without authentication |
| NET-02 | **High** | PVE firewall disabled; no host-level fail2ban |
| NET-03 | **High** | CrowdSec has no bouncer; detection does not enforce anything |
| NET-04 | **High** | PVE web/API is a high-value surface; access is LAN/Tailscale-only but not defense-in-depth |
| NET-05 | **Medium** | CT103 is a single ingress path; tunnel failure removes all public services |
| NET-06 | **Medium** | Cloudflare ingress/WAF rules are remote-managed and not audited |
| NET-07 | **Medium** | AdGuard is a single DNS dependency |
| NET-08 | **Medium** | No UPS/power monitoring evidence |
| NET-09 | **Low** | Old `nfty` hostname returns 502 and should be removed from docs/configs |

**Do not** enable the PVE firewall or add a CrowdSec bouncer during discovery. Both can lock out access if applied without a tested allowlist (SSH, PVE web/API, Tailscale, tunnel, DNS).

---

## 4. Monitoring opportunities

### Tier 1 — must have

| ID | Signal | Detection source | Trigger | Topic |
|---|---|---|---|---|
| NET-01 | WAN down | external probe / Uptime Kuma | 2 consecutive failures to a public target and to the site | `rplab-network` |
| NET-02 | Public DNS failure | external DNS probe | NXDOMAIN/SERVFAIL/wrong address | `rplab-network` |
| NET-03 | Cloudflare tunnel down | cloudflared metrics/health or external probe | tunnel disconnected >2 min | `rplab-network` |
| NET-04 | Website unreachable from outside | Uptime Kuma | 2 failures | `rplab-web` |
| NET-05 | ntfy unreachable | external health probe | 2 failures | `rplab-ntfy` |
| NET-06 | TLS expiry | external TLS probe | <21 days warn; <7 days critical | `rplab-network` |

### Tier 2 — recommended

| ID | Signal | Trigger | Topic |
|---|---|---|---|
| NET-07 | SSH failed logins | >10 failures/min from one source | `rplab-security` |
| NET-08 | PVE authentication failures | repeated failures in PVE auth log | `rplab-security` |
| NET-09 | CrowdSec decisions | any ban in production after bouncer enablement | `rplab-security` |
| NET-10 | Certificate renewal failure | renewal error | `rplab-network` |
| NET-11 | Tunnel restarts | >2 restarts/hour | `rplab-network` |
| NET-12 | AdGuard DNS failure | resolution failure for a known name | `rplab-services` |
| NET-13 | PVE firewall drift | config change vs baseline | `rplab-security` |
| NET-14 | Unexpected port exposure | new listening port on host/guest | `rplab-security` |

### Tier 3 — advanced

| ID | Signal | Trigger |
|---|---|---|
| NET-15 | WAN latency/jitter trend | sustained degradation |
| NET-16 | Cloudflare WAF spike | anomalous block rate |
| NET-17 | DNS query anomaly | unusual volume/domain pattern |
| NET-18 | VPN peer offline | Tailscale peer down |
| NET-19 | UPS on battery / low battery | NUT event, if UPS added |
| NET-20 | Router configuration change | syslog/API diff |

Do not alert on every blocked SSH attempt or every CrowdSec detection; aggregate and threshold.

---

## 5. Security baseline recommendations

1. Enable ntfy authentication and default-deny before exposing new topics.
2. Keep `kfadmin` out of the Android app and out of normal API clients.
3. Use HTTPS-only admin access; never send an admin token through a public topic.
4. Enable the PVE firewall only with an explicit allowlist (SSH, PVE web/API, Tailscale, tunnel, DNS) and a tested rollback.
5. Install/register a CrowdSec bouncer only after the detection stream is verified; otherwise it is just an expensive log viewer.
6. Add host-level SSH protection (fail2ban or CrowdSec) after verifying key-based access and lockout recovery.
7. Keep the public ntfy endpoint behind Cloudflare; do not expose the origin port directly.
8. Do not expose the Docker socket, PVE API or metrics endpoints publicly.
9. Back up the ntfy auth database before enabling auth; record token revocation steps.
10. Review Cloudflare Access/WAF rules for admin paths; do not assume the tunnel alone is authentication.

---

## 6. Unknowns

- Router model, WAN type, port-forwarding and failover.
- Cloudflare remote ingress rules and WAF configuration.
- Whether Cloudflare Access protects any hostname.
- Whether PVE is reachable from the internet other than through the tunnel.
- Whether Tailscale is used for all admin access or only the VM.
- Whether the host has out-of-band management.
- Whether the UPS exists; no NUT evidence.

---

## 7. Evidence commands

```text
pve-firewall status
ss -ltnp
systemctl list-units --type=service --state=running
pct exec 103 -- systemctl status cloudflared
pct exec 104 -- docker logs cloudflared
pct exec 104 -- docker exec crowdsec cscli bouncers list
dig +short rplab.qzz.io
curl -sSI https://rplab.qzz.io/
curl -sS https://ntfy.rplab.qzz.io/v1/health
```

No firewall, DNS, tunnel or WAF change was made.
