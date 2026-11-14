# RPLab Website Monitoring Analysis

**Target:** `https://rplab.qzz.io`  
**Discovery date:** 2026-11-11  
**Status:** read-only; the website was not modified.

---

## 1. Verified architecture

| Layer | What was found |
|---|---|
| DNS | Public `rplab.qzz.io` resolves to Cloudflare addresses |
| Edge | Cloudflare; `server: cloudflare`; TLS certificate issued by Google Trust Services, valid **2026-09-26 → 2026-12-25** |
| Public path | Cloudflare Tunnel through the existing cloudflared LXC (CT103); no local ingress YAML, token-based tunnel |
| Origin service | `rplab-dashboard` container, image `kaminari-fuji-gate`, on the Docker LXC (CT104) |
| Origin port | 8484; container health `healthy`; `restart=unless-stopped` |
| Application | Node.js 22 / Express 4; static ES-module front end served by Express; React/Spline dependencies for the public experience |
| Persistence | SQLite ledger (`data/ledger.sqlite`) for history, events, audit and settings |
| Realtime | SSE hub for pulse/events/services with replay |
| Health | `/healthz` → 200; `/api/health` → 200; `/api/health` returns `ok`, node, gate status and uptime |
| Auth | Custom gate (`GATE_PIN`/`GATE_SECRET`) plus optional Authentik SSO (`auth.rplab.qzz.io`); admin token for protected operations |
| PVE integration | Read-only PVE API calls; guest/node views; power operations gated by explicit environment flags |
| Notification integration | Publishes to `KAMINARI-FUJI-Homelab` today; has `/api/ntfy/test` behind the unlock gate |
| External dependencies | `api.open-meteo.com`, geocoding API, Cloudflare Insights, Spline CDN, `auth.rplab.qzz.io`, `logs.rplab.qzz.io` |

**Not verified:** the exact Cloudflare ingress rule (remote-managed), the current Uptime Kuma monitor list, the Authentik application policy, and the exact Deploy/watchtower update path.

---

## 2. Component map

```text
Internet
  └─ Cloudflare edge (TLS, WAF/analytics possible)
       └─ Cloudflare Tunnel (CT103, token-based)
            └─ rplab-dashboard :8484 (CT104)
                 ├─ Express / static PWA
                 ├─ SQLite ledger (data/ledger.sqlite)
                 ├─ SSE realtime hub
                 ├─ Gate / Authentik auth
                 ├─ PVE API (read-only)
                 └─ ntfy publish (KAMINARI-FUJI-Homelab today)
```

The website is a **separate notification domain**. It must not share `rplab-pve-*`, `rplab-backups` or `rplab-services`; its events belong on **`rplab-web`**.

---

## 3. What can produce useful notifications

### Tier 1 — must have

| Event | Detection source | Proposed trigger | Topic |
|---|---|---|---|
| Public site unavailable | Uptime Kuma / external probe | 2 consecutive failures from outside | `rplab-web` |
| Health endpoint failing | `/healthz` or `/api/health` | non-200 or body `ok=false` for 2 checks | `rplab-web` |
| 5xx burst | Reverse proxy/access log or synthetic probe | >5 5xx in 5 minutes | `rplab-web` |
| TLS certificate expiring | External TLS probe | <21 days remaining | `rplab-web` |
| DNS failure | External DNS probe | NXDOMAIN/SERVFAIL or wrong A/AAAA | `rplab-network` |
| Tunnel down | cloudflared health/metrics or external probe | tunnel disconnected | `rplab-network` |
| Container unhealthy | Docker health status | `unhealthy` transition | `rplab-web` |

### Tier 2 — recommended

| Event | Detection source | Proposed trigger |
|---|---|---|
| Unexpected redirect | Synthetic probe | final URL/host differs from expected |
| Origin upstream failure | Cloudflare/tunnel logs or probe | 502/503/504 |
| Authentication outage | `/api/health` gate state; Authentik health | gate required but unavailable |
| Repeated failed unlock | application ledger/auth log | >10 failures from one source in 5 min |
| Ledger write failure | application log/sqlite check | write error or database locked |
| Deployment failure | deploy script/watchtower | non-zero exit or container restart loop |
| Dependency failure | synthetic checks | Open-Meteo/Spline/Authentik unavailable |
| Resource pressure | container stats | CPU >80% or memory >85% for 10 min |

### Tier 3 — advanced

| Event | Detection source |
|---|---|
| Daily website digest | aggregated request/error counts |
| Config change | deploy/ledger audit event |
| Certificate renewal success | ACME/Cloudflare log |
| Unusual traffic pattern | Cloudflare analytics or CrowdSec |
| New deployment | watchtower/deploy event |

Do **not** alert on every page view, normal login, or ordinary 404. Do not collect visitor IPs, session values, passwords, or form contents.

---

## 4. Website health checks already available

| Endpoint | Purpose | Verified |
|---|---|---|
| `GET /` | public landing page | 200 |
| `GET /healthz` | node/uptime liveness | 200 |
| `GET /api/health` | app health, gate state, node, uptime | 200 |
| Docker `HEALTHCHECK` | container-level liveness | healthy |
| `/api/config` | feature/gate configuration (not public? gated by route) | route exists; do not expose secrets |

The health endpoint is necessary but **not sufficient**: it does not prove DNS, TLS, Cloudflare, or a real browser session. Pair it with an external synthetic check from Uptime Kuma and a TLS-expiry probe.

---

## 5. Monitoring design

### External checks (independent of the homelab)

1. HTTPS GET `/` from an external monitor; assert status 200 and expected title/host.
2. HTTPS GET `/healthz`; assert 200 and JSON `ok=true`.
3. TLS expiry check; warn at 21 days, critical at 7 days.
4. DNS A/AAAA check; alert on failure or unexpected address family.
5. Optional authenticated flow check only if a dedicated test account exists; do not use the owner’s session.

### Internal checks (existing Docker LXC)

1. `docker inspect` health status for `rplab-dashboard`.
2. Container restart count and OOM state.
3. Express error log / 5xx counter.
4. SQLite ledger writable and not locked.
5. Disk space for the ledger volume.
6. Authentik/SSO dependency health.

### What to send to ntfy

- Transition alerts only: down, recovered, certificate warning, deployment failed.
- One recovery message when the state returns; include the outage duration.
- No per-request or per-login messages.
- A daily digest at low priority.

---

## 6. Proposed topic: `rplab-web`

| Property | Value |
|---|---|
| Purpose | Everything about the RPLab website/gate |
| Publishers | Site monitor, deployment job, website app (server-side only) |
| Subscribers | Owner Android app, dashboard, optional email/Apprise |
| Read | `kaminari_app`, `kfadmin`, `rplab_monitor` (if it needs read) |
| Write | `rplab_monitor` / site deployment identity only |
| Priority | P5 down/TLS critical; P4 5xx burst/auth outage; P3 recovery/deploy; P2 info; P1 digest |
| Retention | 90 days in the app; server cache is volatile |
| Expected frequency | Low: transitions and deployments, not page views |
| Separate from | PVE host, backups, generic services |

---

## 7. Risks and unknowns

| ID | Severity | Risk |
|---|---|---|
| WEB-01 | **High** | The site is a single container; an unhealthy container or bad deploy can take the public site down |
| WEB-02 | **High** | TLS expiry is 2026-12-25; renewal depends on Cloudflare, not visible from the host |
| WEB-03 | **Medium** | External dependencies (Open-Meteo, Spline, Authentik) can degrade the experience without the origin being down |
| WEB-04 | **Medium** | The current notification path mixes website events into the homelab topic |
| WEB-05 | **Medium** | The exact Cloudflare ingress and WAF rules are remote-managed and not audited |
| WEB-06 | **Low** | A failed login threshold must avoid locking out the owner; use a high threshold and a separate security topic |

---

## 8. Recommendations

1. Reserve `rplab-web` exclusively for website events.
2. Add an external Uptime Kuma check and a TLS-expiry check; do not rely only on `/healthz`.
3. Emit website deployment and audit events from the server, not from the browser.
4. Keep the website’s existing `/api/ntfy/test` behind the unlock gate; never expose it publicly.
5. Do not add a second reverse proxy or a new monitoring stack; reuse the existing tunnel and Uptime Kuma.
6. Before enabling ntfy auth, give the website its own non-admin token and verify publish/read behavior on a test topic.
