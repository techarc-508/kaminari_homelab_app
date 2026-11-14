# Kaminari Homelab — ntfy Redesign Discovery: Executive Summary

**Phase:** 0 — discovery and architecture proposal only  
**Discovery date:** 2026-11-11, 15:28 UTC  
**Scope:** existing PVE host, existing ntfy installation, RPLab website, storage, backups, network and application inventory  
**Change status:** **zero production changes.** No account, token, topic, ACL, `auth-default-access`, service, proxy, firewall or notification was created or modified.

---

## Purpose

This document is the entry point to the evidence-based proposal for a professional ntfy topic and monitoring architecture. It answers four questions:

1. What is actually running today?
2. What can be monitored without inventing data or duplicating the monitoring stack?
3. What topic and access-control model fits the real environment?
4. What must be implemented in controlled stages, and in what order?

Detailed evidence lives in the companion documents in this folder. Claims are marked **Verified**, **Inferred** or **Recommendation** where the distinction matters.

---

## What is running today

### ntfy

- **Verified:** ntfy **2.28.0** runs as Docker container `ntfy` (`binwiederhier/ntfy`) on the existing Docker LXC (CT104).
- **Verified:** container port `80` is published as host port **8201**; public access is `https://ntfy.rplab.qzz.io` through the existing Cloudflare tunnel (CT103).
- **Verified:** `https://ntfy.rplab.qzz.io/v1/health` returns **200**; the old spelling `https://nfty.rplab.qzz.io` returns **502** and must not be used.
- **Verified:** authentication is **disabled**: `users=0`, `enable_login=false`, `require_login=false`, no auth database.
- **Verified:** the data directory `/var/lib/ntfy` is empty; the cache is in memory only. A restart loses cached messages. There is no persistent message history.
- **Verified:** no attachments, no explicit cache/rate-limit configuration, no `/metrics` endpoint.
- **Verified:** server logs report 3 active topics, 37 cached messages and 634 messages published at the time of discovery.

### Topics

| Topic | Status | Evidence |
|---|---|---|
| `KAMINARI-FUJI-Homelab` | **Verified active production topic** | PVE webhook `kaminari-ntfy`; dashboard `NTFY_TOPIC`; Docker event watcher |
| `kaminari-diary` | **Verified active** | `kaminari-diary` container environment |
| `kaminari-onboarding-test` | **Verified active test topic** | Android onboarding test; not a production alert topic |
| `homelab-alerts`, `iot-telemetry`, `backups`, `build-server`, `security` | **Not verified** | No producer configuration found; do not assume they exist |

`ntfy` does not expose a complete historical topic registry. The three active topics are confirmed from producer configuration and server statistics, not from an enumerable server-side topic list.

### PVE and guests

- **Verified:** PVE 9.2.11, single node `pve`; six guests: CT100 AdGuard, CT101 Apache Guacamole, CT102 Immich, CT103 cloudflared, CT104 Docker stack, VM200 Linux Mint.
- **Verified:** all six guests are running and `onboot=1`.
- **Verified:** storage is local LVM thin (`local-lvm`, `fast-data`), ZFS (`safedrive`) and a directory backup store (`backup-hdd`).
- **Verified:** daily snapshot backups at 01:00, 01:30, 02:00 and 02:30 to `backup-hdd`, prune policy `keep-last=2`.

### Critical live findings

1. **CT104 and VM200 backups are failing.** The latest CT104 backup failed with `zstd: error 70 : Write error : cannot write block : Disk quota exceeded`. The latest VM200 backup failed with `vma_queue_write: write error - Broken pipe`. `backup-hdd` is **83.25% full** and `safedrive/backup` is **84% used**. This is a live data-protection failure, not a proposal item.
2. **PVE’s ntfy alert for the failed VM200 backup returned HTTP 400.** The backup task log records `could not notify via target kaminari-ntfy: http status: 400`. The alert path that should report failures is itself failing.
3. **The `safedrive` pool is a single 465 GB HDD** with **11,773 reported uncorrectable events**, **29,106 power-on hours**, and two UDMA CRC errors, although SMART overall health says PASSED and the last scrub is clean. This is a risk to investigate, not a confirmed imminent failure.
4. **ntfy is publicly writable without authentication.** Anyone who knows a topic name can read or publish. `KAMINARI-FUJI-Homelab` is a known production topic; `kaminari-diary` is personal.
5. **The PVE firewall is disabled** (`Status: disabled/running`), CrowdSec is running but has **no registered bouncers**, and there is no host-level fail2ban. Public exposure is mediated by Cloudflare Tunnel, but host-level enforcement is weak.

### Website

- **Verified:** `https://rplab.qzz.io` returns 200 and is served by `rplab-dashboard` (image `kaminari-fuji-gate`) on the Docker LXC; the container is healthy and exposes `/healthz` and `/api/health`.
- **Verified:** public TLS is Cloudflare/Google Trust Services, valid until **2026-12-25**.
- **Verified:** the site is a Node/Express application with a static front end, a SQLite ledger, SSE realtime, an auth gate and optional Authentik integration.
- **Verified:** it has a dedicated notification integration to `KAMINARI-FUJI-Homelab`, but website events are currently mixed into the unified homelab topic. The redesign gives the website its own `rplab-web` topic.

### Existing monitoring tools

- Uptime Kuma (availability checks; monitor list not audited)
- Beszel (lightweight metrics; no health check)
- Scrutiny + InfluxDB (SMART/disk health)
- Healthchecks (cron/dead-man checks)
- CrowdSec (detection only; no bouncer)
- Dozzle (container logs)
- Apprise (notification abstraction)
- PVE native status, RRD and task history
- `smartmontools` on the PVE host

No Prometheus, Grafana, Netdata, Gatus or Zabbix was found. There is no need to add a second stack before using the tools already present.

---

## Recommended architecture in one page

**Namespace:** `rplab-` for all new topics.

**Core topics:**

- `rplab-web` — RPLab website and gate
- `rplab-pve-host` — PVE node health
- `rplab-pve-ct` — LXC lifecycle and guest-level health
- `rplab-pve-vm` — VM lifecycle and guest-level health
- `rplab-storage` — ZFS, SMART, capacity, thin-pool pressure
- `rplab-backups` — job success/failure, verification, capacity
- `rplab-network` — WAN, DNS, tunnel, TLS, router
- `rplab-services` — application health and Docker lifecycle
- `rplab-security` — authentication anomalies, firewall, CrowdSec
- `rplab-ntfy` — the notification service monitoring itself
- `rplab-command-request` — typed commands from the app
- `rplab-command-result` — correlated executor results
- `rplab-digest` — daily/quiet summary
- `rplab-iot` and `rplab-builds` — only when real producers exist

**Access model (proposed, not created):**

- `kfadmin` — ntfy administrator; web UI only; no token in Android.
- `kaminari_app` — normal restricted user for the Android app; read alert topics, write only command-request and the temporary onboarding test topic.
- `rplab_monitor` — normal restricted user for infrastructure producers; write alert topics only.
- Optionally split producers into `pve_notify`, `web_notify`, `backup_notify` later.
- Enable `auth-default-access=deny-all` only after every producer has a token and has been tested. Keep temporary anonymous access until the migration is proven.
- ntfy access tokens inherit the user’s ACLs; a permanent app token is acceptable only because the app user is non-admin and topic-restricted.

**Notification discipline:**

- Priority 5 only for genuine critical failure.
- Priority 4 for urgent operational problems.
- Priority 3 for normal state changes.
- Priority 2 for informational events.
- Priority 1 for digest/quiet notifications.
- Deduplicate by component + event + resource; use cooldowns, hysteresis and recovery messages.
- No destructive action buttons in notifications.

---

## Staged roadmap

| Stage | Goal | Gate |
|---|---|---|
| **1. Protect and repair** | Free backup space; fix CT104/VM200 backups; fix PVE ntfy 400; back up current ntfy/auth state before any auth change | Backups succeed twice; test alert reaches ntfy |
| **2. Topic and ACL foundation** | Create `kfadmin`, `kaminari_app`, `rplab_monitor`; create core topics; issue tokens; leave `auth-default-access` unchanged | All producers and app read/write tests pass with auth enabled |
| **3. External monitoring** | Uptime Kuma/Healthchecks for website, ntfy, DNS, TLS, tunnel, WAN; route to `rplab-*` topics | Alert and recovery demonstrated for each Tier 1 check |
| **4. PVE and storage** | PVE notification matchers, SMART/ZFS capacity, backup result verification, guest lifecycle | Backup success and failure both notify; no alert storm |
| **5. Application layer** | Docker health, key service endpoints, Authentik, Vaultwarden, Immich, AdGuard, Guacamole | One `rplab-services` stream with dedupe |
| **6. Security and digest** | CrowdSec bouncer, auth-failure thresholds, daily digest, quiet hours | Security event test and digest review |
| **7. Command topics** | Separate request/result topics and gateway contract | Replay/expiry/allowlist tests; no arbitrary shell |

Stages 1 and 2 must not be merged: repairing backups and understanding the current notification path comes before enabling authentication.

---

## Decisions requested from the owner

1. Approve the `rplab-` namespace and the core topic list, including separate `rplab-pve-ct` and `rplab-pve-vm`.
2. Approve merging website notifications into `rplab-web` only; no website events in `rplab-pve-*` or `rplab-services`.
3. Approve the three-account model (`kfadmin`, `kaminari_app`, `rplab_monitor`) or request per-producer accounts.
4. Approve the staged auth migration: keep anonymous access until every producer is migrated, then set default-deny.
5. Confirm whether the existing `KAMINARI-FUJI-Homelab` topic should remain as a legacy compatibility topic during migration or be retired after all consumers move.
6. Confirm whether `kaminari-diary` remains a personal topic outside the `rplab-` alert namespace.

No implementation begins until this proposal is approved.

---

## Evidence and limitations

**Verified:** host, container, version, ports, health, config API, auth state, data directory, topic producers, PVE topology, storage status, backup failures, SMART counters, website endpoints, TLS dates, existing monitoring containers.

**Not verified:** Uptime Kuma monitor definitions, Duplicati job configuration, Cloudflare remote ingress rules (token-based tunnel; no local ingress YAML), router/WAN configuration, UPS status, physical Android delivery, Boox behavior.

**Not claimed:** that ntfy delivery equals handset delivery; that a publish accepted by ntfy means a person read it; that SMART PASSED means the disk is healthy; that the current backup store is adequate; that every topic name has been discovered (ntfy has no complete topic registry).
