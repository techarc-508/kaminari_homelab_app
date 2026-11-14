# Topic Migration Matrix — Freebuff Phase 0

**Authority:** `APPROVED_KF_TOPIC_CATALOGUE.md`, `KF_TOPIC_ACL_MATRIX.md`, `KF_ADMIN_AND_GATEWAY_AUTHORIZATION_MODEL.md`, and the three pipeline contracts.
**Status:** design reconciliation only. **No topic, user, token or ACL was created.**
**Live state:** no `kf-` topic has an identified producer, client or configuration; three legacy topics are active. See `LIVE_NTFY_INVENTORY.md` for the evidence limit (ntfy has no topic registry).

Legend — **S** = sensitivity. **Ret.** = required retention (a design requirement, not a live server setting). **Phase** = the phase that may first create/use it, per the workstream plan.

---

## 1. Approved topic matrix (22)

| # | Topic | Purpose | Publisher | Subscribers (read) | Write permission | S | Ret. | State | Phase |
|---:|---|---|---|---|---|---|---|---|---|
| 1 | `kf-web` | Website, TLS, tunnel, deploy, auth-outage events | website monitor (identity unresolved, C-18) | `kaminari_app`, `kfadmin` | website/monitor identity only | Normal | 90 d | Not created | 4 |
| 2 | `kf-pve-host` | Node health, CPU/RAM pressure, node state, updates | `kf_monitor` | `kaminari_app`, `kfadmin` | `kf_monitor` | Normal | 90 d | Not created | 4 |
| 3 | `kf-pve-ct` | LXC lifecycle, resource pressure, guest-agent state | `kf_monitor` | `kaminari_app`, `kfadmin` | `kf_monitor` | Normal | 90 d | Not created | 4 |
| 4 | `kf-pve-vm` | QEMU lifecycle, resource pressure, guest-agent state | `kf_monitor` | `kaminari_app`, `kfadmin` | `kf_monitor` | Normal | 90 d | Not created | 4 |
| 5 | `kf-storage` | ZFS/SMART, capacity, scrub, pool degradation | `kf_monitor` | `kaminari_app`, `kfadmin` | `kf_monitor` | Normal | 90 d; P5 critical | Not created | 4 |
| 6 | `kf-backups` | Backup start/result/verification/retention, restore drills | `kf_monitor` (Workstream A owns scheduling/storage) | `kaminari_app`, `kfadmin` | `kf_monitor` | Normal | 90 d; no full logs | Not created | 4 |
| 7 | `kf-network` | WAN/LAN, DNS, tunnel, reverse proxy, firewall, VPN | `kf_monitor` | `kaminari_app`, `kfadmin` | `kf_monitor` | Normal | 90 d | Not created | 4 |
| 8 | `kf-services` | Docker/application health, restart loops, endpoints | `kf_monitor` | `kaminari_app`, `kfadmin` | `kf_monitor` | Normal | 90 d | Not created | 4 |
| 9 | `kf-security` | CrowdSec, auth failures, SSH/PVE anomalies, firewall changes | `kf_monitor` | `kaminari_app`, `kfadmin` | `kf_monitor` | **High** | 90 d; no secrets/raw sessions | Not created | 4 |
| 10 | `kf-ntfy` | ntfy health, restart, publish/auth failures, metrics alerts | `kf_monitor` (external watchdog) | `kaminari_app`, `kfadmin` | `kf_monitor` | Normal | 90 d | Not created | 4 |
| 11 | `kf-digest` | Daily summary and quiet-hours rollup | `kf_monitor` / digest job | `kaminari_app`, `kfadmin` | digest identity | Normal | 30 d | Not created | 4 |
| 12 | `kf-music-pipeline` | Memos → download → metadata → library → Navidrome stages | `kf_music` | `kaminari_app`, `kfadmin` | `kf_music` | Normal | 30 d | Not created | 4 |
| 13 | `kf-music-reports` | Per-run/batch final report | `kf_music` | `kaminari_app`, `kfadmin` | `kf_music` | Normal | 90 d | Not created | 4 |
| 14 | `kf-stickers-pipeline` | Future Diary sticker stages | `kf_stickers` (future) | `kaminari_app`, `kfadmin` | `kf_stickers` | Normal | 30 d | **Reserved** | 4+ |
| 15 | `kf-stickers-reports` | Future sticker run summary | `kf_stickers` (future) | `kaminari_app`, `kfadmin` | `kf_stickers` | Normal | 90 d | **Reserved** | 4+ |
| 16 | `kf-news` | News card only: headline, source, category, summary, URL | `kf_news` | `kaminari_app`, `kfadmin` | `kf_news` | Normal | 30 d | Not created | 4 |
| 17 | `kf-news-reports` | Scraper run, counts, source failures, processing | `kf_news` | `kaminari_app`, `kfadmin` | `kf_news` | Normal | 90 d | Not created | 4 |
| 18 | `kf-command-request` | Typed command envelope from Android/owner | `kaminari_app` (Android) | gateway (see P-02) | `kaminari_app` | **High** | 30 d; never shell text | Not created | 3/4 |
| 19 | `kf-command-result` | Correlated execution result, status, audit reference | gateway | `kaminari_app`, `kfadmin` | gateway identity (see P-02) | **High** | 90 d; no secrets | Not created | 3/4 |
| 20 | `kf-admin-audit` | Durable admin/command audit events | gateway | `kaminari_app`, `kfadmin` | gateway identity | **High** | 1 year min | Not created | 3/4 |
| 21 | `kf-diary` | Diary events and sticker sync | Diary app | `kaminari_app`, `kfadmin` | diary publisher | Normal | 90 d | Not created | 4+ |
| 22 | `kf-iot` | Home automation/IoT state and alerts | IoT bridge (none exists) | `kaminari_app`, `kfadmin` | none until a producer is approved | Normal | 90 d | **Reserved** | future |

`kf-builds` is **excluded** and must not be created.

---

## 2. Legacy topic matrix (must remain available)

| Legacy topic | Producer today | Migration target | Deletion rule | ACL during migration |
|---|---|---|---|---|
| `KAMINARI-FUJI-Homelab` | PVE webhook, dashboard, Docker events, watchtower, MySpeed, request bot | Split by event type into `kf-pve-*`, `kf-backups`, `kf-web`, `kf-services`, `kf-network` | **No deletion without explicit owner approval.** Retire only after every producer is migrated and read-back is verified. | Keep current anonymous access until all producers have tokens; then explicit read for `kaminari_app` |
| `kaminari-diary` | `kaminari-diary` container | `kf-diary` | Owner approval required | Keep working; add `kaminari_app` read + diary producer write |
| `kaminari-onboarding-test` | Android onboarding / manual tests | none (test-only) | Retire/restrict only after onboarding migration | `kaminari_app` read/write; **must be added to the ACL matrix** (C-14) |

---

## 3. Identity and effective permission matrix

| Identity | Role | ntfy admin? | Intended permissions |
|---|---|---|---|
| `kfadmin` | ntfy superadministrator | **Yes** | All; CLI/web only; never in Android or gateway |
| `kaminari_app` | Application/gateway owner | **No** | Read `kf-*`; write `kf-command-request`; (gateway needs more — see C-02) |
| `kf_monitor` | Infrastructure publisher | **No** | Write exact infrastructure topics only; no command/admin topics |
| `kf_music` | Music publisher | **No** | Write `kf-music-pipeline`, `kf-music-reports` |
| `kf_stickers` | Sticker publisher (future) | **No** | Write `kf-stickers-pipeline`, `kf-stickers-reports` only |
| `kf_news` | News publisher | **No** | Write `kf-news`, `kf-news-reports` |
| `anonymous` | Public default | — | **Denied** after migration |

### 3.1 Effective ACL intent (from the approved matrix)

```text
kaminari_app  kf-*                 read-only
kaminari_app  kf-command-request   write-only
kaminari_app  kf-admin-audit       write-only (optional per matrix)
kf_monitor    kf-web               write-only
kf_monitor    kf-pve-host          write-only
... one exact write grant per infrastructure topic ...
kf_music      kf-music-pipeline    write-only
kf_music      kf-music-reports     write-only
kf_stickers   kf-stickers-pipeline write-only
kf_stickers   kf-stickers-reports  write-only
kf_news       kf-news              write-only
kf_news       kf-news-reports      write-only
```

### 3.2 Findings that must be resolved before applying it

1. **C-02 (critical):** ntfy ACLs are per **user**, not per token. The Android token and gateway token are both `kaminari_app`, so they inherit the same grants. As written, the gateway cannot read `kf-command-request` while writing `kf-command-result` unless the user can also write requests, and the Android token would then also be able to write results/audit. Decision P-02 is required.
2. **C-06:** `kf-diary` grants `kf_stickers` write, but the stickers contract forbids publishing to `kf-diary`. Decision P-07.
3. **C-13:** `kf-iot` has no producer. Create the topic only as reserved-and-denied, or defer creation. Decision P-08.
4. **C-14:** the legacy test topic is missing from the matrix and is needed to prove a token works before switching a production producer.
5. **Wildcard precedence is unverified on 2.28.0.** Do not assume `kf-*` read-only plus a narrower write-only grant behaves as intended; test both an allowed and a denied topic per token in a controlled stage.
6. **`kf-admin-audit` is not immutable** in ntfy. A local append-only audit store is required (P-05).

---

## 4. Migration sequence and gates

This is the Phase 5 sequence. It requires explicit owner approval and must follow, not precede, Workstream A's backup/storage decisions.

| Step | Action | Verification | Rollback |
|---|---|---|---|
| 0 | Back up `utilities_ntfy_data` and the compose file; capture `docker inspect ntfy` | Backup restores on a non-production port | Restore volume + compose |
| 1 | Create `kfadmin` with a random password in the owner's private store | Login only through HTTPS; no token in Git | Remove user |
| 2 | Create the five non-admin identities | `ntfy user list` shows roles; no extra admin | Remove users |
| 3 | Create the 22 topics (or the subset approved for Phase 5) | Explicit list; `kf-builds` absent | Topics are inert until written |
| 4 | Apply the ACL matrix | Per-identity allow **and** deny test | Restore previous ACLs from the backup |
| 5 | Issue separate tokens; store only in private stores | Token list by label, no secret printed | Remove token by label |
| 6 | Point the Android app at `kaminari_app` and verify read/deny | Read an alert topic; write request topic; denied on `kf-security` write | Revert app profile to guest (temporary) |
| 7 | Migrate producers one domain at a time, starting with a low-risk domain | Publish + read-back on the new topic; legacy topic still works | Revert the producer's URL to the legacy topic |
| 8 | Migrate the website producer | `kf-web` event received; no event in `kf-pve-*` | Revert dashboard config |
| 9 | Migrate PVE matchers and backup notification | `kf-backups` receives a real backup result; no HTTP 400 | Restore `/etc/pve/notifications.cfg` |
| 10 | Migrate Docker events, watchtower, MySpeed, request bot | `kf-services`/`kf-network` events observed; noise checked against the alert budget | Revert each producer |
| 11 | Only after all of the above: set `auth-default-access=deny-all` | Anonymous publish fails; every producer still publishes | Set back to the previous value immediately |
| 12 | Remove anonymous access; keep legacy topics | Legacy consumers still read; no deletion | Re-enable anonymous temporarily |
| 13 | Retire legacy topics | **Separate owner approval** | Topics were never deleted before approval |

**Hard stop:** if any producer cannot be migrated, do **not** enable default-deny. The current alert path (`KAMINARI-FUJI-Homelab`) is the only working production notification route.

---

## 5. Per-topic documentation requirements

For every topic the implementation must record, in one place:

- purpose
- publisher identity
- subscriber identities
- read permission
- write permission
- whether it carries sensitive data
- retention expectation
- message format (schema ID and required fields)
- active or reserved

The catalogue covers purpose, publishers, subscribers, sensitivity and retention. The implementation must add the explicit read/write grants and schema IDs per topic. That artefact does not exist yet.

---

## 6. Alert-noise budget (from the priority standard)

| Topic group | Expected steady-state volume |
|---|---|
| `kf-web` | 0–2/day |
| `kf-pve-host` / `kf-pve-ct` / `kf-pve-vm` | 0–3/day each |
| `kf-storage` | 0–2/day |
| `kf-backups` | 1–4/day (failures + one success summary) |
| `kf-network` | 0–2/day |
| `kf-services` | 0–5/day (deduped) |
| `kf-security` | 0–3/day |
| `kf-ntfy` | 0–1/day |
| `kf-digest` | 1/day |
| pipeline topics | per run, stage-level only |

Pipeline topics must aggregate noisy stages; `kf-music-pipeline` is stage-level, `kf-music-reports` is one message per run.

---

## 7. Acceptance criteria for the migration

- All 22 topics exist, or the reserved subset is explicitly documented as deferred.
- `kf-builds` does not exist.
- `kfadmin` is the only ntfy administrator.
- `kaminari_app` cannot administer ntfy.
- Every producer writes only its approved topics; a denied write returns 403.
- The Android token cannot write `kf-command-result` or `kf-admin-audit` (requires P-02 resolution).
- `kf-security` and `kf-admin-audit` never contain credentials or full logs.
- Legacy topics remain readable throughout.
- Anonymous access is denied only after every producer and subscriber is verified.
- Rollback has been exercised, not merely described.

**Status:** none of these criteria is met yet. This document is a plan.
