# Freebuff Phase 0 — Repository and Infrastructure Reconciliation

**Workstream:** B — Freebuff (ntfy, gateway, Android/web integration, pipeline notification architecture)
**Date:** 2026-11-12 12:35 UTC
**Phase:** 0 only. **No source, dependency, account, token, topic, ACL, guest, service or production configuration was changed.**
**Repository HEAD:** `5952ed0` (`docs: state the ntfy admin UI boundary honestly`)
**Working tree at audit start:** five modified trackers (`PROGRESS`, `TODO`, `DECISIONS`, `RISKS`, `CHANGELOG`) plus the uncommitted Stage 1 Freebuff handover documents. Nothing was reverted or overwritten.

---

## 1. Purpose and method

Phase 0 answers one question before implementation: **what is actually complete, what is only documented, what is tested, and what is deployed?**

Method:

1. Read the full repository document set, including the plan-folder mirror and `docs/ntfy-redesign/discovery/`.
2. Read the Android, gateway, Room, delivery and command source that the redesign depends on.
3. Re-probed the live ntfy endpoint read-only (`/v1/health`, `/v1/config`, `/v1/stats`, DNS, TLS). No publish, subscribe, login or admin call was made.
4. Re-ran the existing test suites to establish a current baseline.
5. Compared the owner-approved specification set against code and live state, and recorded every conflict rather than "fixing" it silently.

### 1.1 Evidence produced in this phase

| Check | Command | Result |
|---|---|---|
| Android unit tests | `./gradlew testDebugUnitTest --no-daemon`; re-run at final verification with `./gradlew :app:testDebugUnitTest --rerun-tasks` | **exit 0** — 361 tests, 0 failures, 0 errors, 2 skipped (46 result files, newest 19:29 IST) |
| Gateway tests | `PYTHONPATH=. python3 -m pytest -q` (in `gateway/`) | **exit 0** — 9 passed, 1 warning |
| ntfy health | `curl https://ntfy.rplab.qzz.io/v1/health` | **200** `{"healthy":true}` |
| ntfy config | `curl https://ntfy.rplab.qzz.io/v1/config` | **200**; `enable_login=false`, `require_login=false`, `enable_signup=false`, no `auth_file`, no metrics listener |
| ntfy stats | `curl https://ntfy.rplab.qzz.io/v1/stats` | **200** `{"messages":688,...}` at first probe; **689** at 19:29 IST |
| Legacy hostname | `curl https://nfty.rplab.qzz.io/v1/health` | **502** |
| TLS | `openssl s_client` + `x509` | subject `CN = rplab.qzz.io`, issuer Google Trust Services WE1, valid 2026-09-26 → **2026-12-25** |
| `kf-` implementation search | `grep -RIn 'kf-*'` over the repo | **No production code uses any `kf-` topic.** Only the design documents and `PVE_NOTIFICATION_REPAIR_REPORT.md` mention them. |

Limitations of this session, stated plainly:

- The ntfy container, its volume and `docker inspect` were **not** re-inspected from this VM. The prior audit's container evidence (ntfy 2.28.0, `utilities_ntfy_data`, UID 100000) is accepted as previously verified, not re-proven today.
- ntfy does not expose a topic registry. Topic existence cannot be enumerated from the public API; the live inventory is therefore producer-based, as documented in `LIVE_NTFY_INVENTORY.md`.
- No message body was read and no message was published.
- No physical Android device is attached; no device, Doze, OEM or E-Ink claim is made.

---

## 2. Verified state of the project

### 2.1 Application (Android)

| Area | State |
|---|---|
| Package / SDK | `com.rplab.kaminarinfty`, minSdk 24, targetSdk 36 |
| Architecture | Compose UI, ViewModels, repositories, Room v2, DataStore, Keystore-backed `SecretStore` |
| Destinations | **Seven** primary destinations: Inbox, Topics, Compose, Outbox, Activity, Command, Settings (plus Setup/Detail/Search drill-ins) |
| Delivery | Instant foreground SSE + Battery Saver WorkManager catch-up; `DeliveryStatusStore`; boot restore |
| Profiles | Standard Kaminari + dedicated E-Ink theme/profile selector |
| Persistence | Room v2 with explicit `MIGRATION_1_2`; outbox, activity, command tables; bounded retention |
| Publish | Documented `POST /{topic}` client, pre-network validation, typed accepted/failed/ambiguous outcomes |
| Command | Typed `rplab.command.v1` envelope, six-action catalogue, `MockCommandExecutor` wired in `AppContainer`; real `GatewayCommandExecutor` exists but is **not wired**; no gateway configuration UI or gateway-token storage |
| ntfy admin | `NtfyAdminClient` exists and is unit-tested, but is **not wired to any screen** |
| Tests | 361 pass, 2 skipped (hardware Keystore); no device tests |
| Topic model | Arbitrary user-entered topics; no `kf-` catalogue, no domain/category, no priority filter in the UI |

### 2.2 Gateway (FastAPI)

| Area | State |
|---|---|
| Code | `gateway/app/{main,auth,config,commands,models,proxmox}.py` |
| Auth | One shared static bearer token, constant-time compare; rate limit keyed by the first 8 characters of that token |
| Read surface | `healthz`, `readyz`, nodes, qemu list, lxc list, guest status, storage list, task list |
| Command surface | 8 read actions + 6 lifecycle mutations (`vm.*`, `container.*` start/stop/restart) |
| Mutations | Disabled by default (`GATEWAY_MUTATIONS_ENABLED=false`); "approval token" must equal the client-supplied nonce |
| PVE identity | Least-privilege read-only token exists (`KaminariRead`); no write privilege |
| ntfy integration | **None.** The gateway neither reads `kf-command-request` nor publishes `kf-command-result` / `kf-admin-audit`. Results are returned only in the HTTP response. |
| Persistence | Replay cache and rate limiter are in-memory only |
| Audit | Structured log line only; no durable append-only store, no ntfy audit topic |
| Deployment | **Undeployed.** No chosen always-on host; TLS/HTTPS intended via reverse proxy |
| Tests | 9 tests covering auth-required, read, replay, mutation approval, invalid vmid, rate limit |

### 2.3 Web

There is **no web frontend in this repository**, at any path. Level 4 "future web interface" is a specification only.

### 2.4 Documentation

The specification set is complete and internally coherent on topic names, purpose, publishers, readers, sensitivity and retention. The older discovery set (`docs/ntfy-redesign/discovery/`) is **superseded on namespace and identity names** by the owner-approved `kf-` catalogue and six identities. That supersession is recorded in §4 below; the discovery set remains valid for infrastructure findings, priority policy, monitoring opportunities and service inventory.

---

## 3. Implementation matrix

Legend: **Done** = implemented and tested; **Partial** = some implementation; **Doc only** = specified, no implementation; **Absent** = not implemented and not meaningfully specified.

| Capability | Documented | Code | Tested | Deployed | Verdict |
|---|---|---|---|---|---|
| 22 `kf-` topics | Yes | No | No | No | **Doc only** |
| `kfadmin` / `kaminari_app` / four producers | Yes | No | No | No | **Doc only** |
| ntfy authentication and per-topic ACLs | Yes | No | No | No (auth disabled) | **Doc only / live open** |
| Legacy topics preserved | Yes | n/a | No | Yes (3 topics) | **Live** |
| Typed notification payload models | Partly (contracts define JSON) | No | No | No | **Doc only** |
| Topic mapping / ACL tests | Yes (intent) | No | No | No | **Doc only** |
| Gateway read-only PVE | Yes | Yes | Yes | No | **Partial** |
| Gateway typed mutations | Yes | 6 of ~30 | Partial | No | **Partial** |
| Gateway approval + step-up | Yes | Weak (nonce echo) | Partial | No | **Partial / unsafe** |
| Durable gateway audit | Yes | No | No | No | **Doc only** |
| Gateway ↔ ntfy command topics | Yes | No | No | No | **Absent** |
| Music pipeline notification | Yes | No | No | No | **Doc only** |
| News card + reports | Yes | No | No | No | **Doc only** |
| Stickers contracts | Yes | No | No | No | **Doc only (reserved)** |
| Android topic discovery / `kf-` catalogue | Yes | No | No | No | **Absent** |
| Android News / Music / Stickers screens | Yes | No | No | No | **Absent** |
| Android owner dashboard / reports | Yes | No | No | No | **Absent** |
| Android real command execution | Yes | Class exists, unwired | Executor tests only | No | **Partial** |
| Android audit history (from `kf-admin-audit`) | Yes | No | No | No | **Absent** |
| Connection diagnostics | Yes (spec) | Partial status only | Partial | n/a | **Partial** |
| E-Ink profile | Yes | Yes (generic) | UI/theming only | No | **Partial** |
| Web control plane | Yes | No | No | No | **Absent** |
| Production migration | Yes | No | No | No | **Not started** |

---

## 4. Conflicts and discrepancies (reconciliation findings)

Each finding is recorded, not silently resolved. Items that need an owner decision are referenced in §6.

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| **C-01** | High | **Namespace conflict.** The approved catalogue/ACL/contracts use `kf-`; the earlier discovery documents and every producer still expect `rplab-*`. Implementing from the wrong set would create topics no producer writes to. | `APPROVED_KF_TOPIC_CATALOGUE.md` vs `docs/ntfy-redesign/discovery/PROPOSED_TOPIC_ARCHITECTURE.md` |
| **C-02** | **Critical** | **Token-level separation is impossible under one user.** ntfy ACLs are per **user**, not per token. `kaminari_app` therefore cannot have an Android token (write `kf-command-request`) and a gateway token (read requests, write results/audit) with *different* permissions. Both tokens inherit the same user ACL. | `KF_TOPIC_ACL_MATRIX.md` §2 and §4; `KF_ADMIN_AND_GATEWAY_AUTHORIZATION_MODEL.md` §1 |
| **C-03** | High | **Gateway identity not implemented.** The gateway uses an unrelated static `GATEWAY_GATEWAY_TOKEN`, not a `kaminari_app`-scoped ntfy token, and never touches ntfy. The approved model assumes the gateway is an ntfy participant. | `gateway/app/auth.py`, `gateway/.env.example` |
| **C-04** | High | **App reply topic is stale:** `COMMAND_REPLY_TOPIC = "rplab-command-results"`. It must be `kf-command-result` before any integration. | `app/.../commands/CommandEnvelope.kt:14` |
| **C-05** | High | **Command catalogues do not match.** Android actions: `host.health`, `service.status`, `storage.summary`, `backup.latest_status`, `container.status`, `service.restart`. Gateway actions: `host.health`, `node.list`, `vm.list`, `container.list`, `vm.status`, `container.status`, `storage.summary`, `task.history`, `vm.*`, `container.*`. Only three IDs overlap (`host.health`, `storage.summary`, `container.status`); the app cannot invoke most gateway operations, and the gateway cannot execute `service.status`, `backup.latest_status` or `service.restart`. | `CommandPolicy.kt` vs `gateway/app/commands.py` |
| **C-06** | Medium | **`kf-diary` ACL contradiction.** The matrix grants `kf_stickers` write on `kf-diary`; the stickers contract explicitly says the stickers pipeline must not publish to `kf-diary`. | `KF_TOPIC_ACL_MATRIX.md` row 21 vs `STICKERS_PIPELINE_NOTIFICATION_CONTRACT.md` §5 |
| **C-07** | High | **"Immutable" audit is not achievable in ntfy.** ntfy's message cache is in-memory, no persistent store is configured, and a user with write access can publish. `kf-admin-audit` alone cannot be the audit system of record. | live `/v1/config`; prior audit: empty `/var/lib/ntfy` |
| **C-08** | Medium | **Approval is not step-up.** `approval_token` must equal the nonce the client itself generated. Any holder of the shared gateway bearer token can produce both. There is no reauthentication, no server-side session, and no independent approver. | `gateway/app/commands.py` mutation branch |
| **C-09** | High | **No durable replay/idempotency.** Replay cache and rate limiter reset on process restart; no persisted idempotency key. A restart re-opens replay within the 5-minute window. | `ReplayCache.__init__` |
| **C-10** | Medium | **Mutation guard is global, not per-operation.** `GATEWAY_MUTATIONS_ENABLED` is one boolean; the L1/L2/L3 risk classes in the Level 4 spec are not represented in code. | `gateway/app/config.py` |
| **C-11** | Medium | **No target-existence validation.** Node/vmid are regex-validated only; the spec requires validation against live inventory. | `validate_envelope` |
| **C-12** | High | **`NtfyAdminClient` is an admin-capable client inside the APK.** It is not wired to UI today, but it ships in the app and accepts an admin credential at runtime. That contradicts "no superadmin credential in Android" as an architectural boundary, even with no stored credential. | `data/api/NtfyAdminClient.kt`; `AppContainer` does not construct it |
| **C-13** | Medium | **`kf-iot` has a publisher grant but no producer exists.** The catalogue marks it reserved; ACL should not grant write until a real bridge is approved, or the grant is dormant-but-present risk. | Catalogue row 22 vs ACL matrix |
| **C-14** | Medium | **Legacy test topic is absent from the ACL matrix.** The migration needs `kaminari_app` read/write on `kaminari-onboarding-test` (or an equivalent) to prove ACL migration safely. | `KF_TOPIC_ACL_MATRIX.md` has no legacy-topic row |
| **C-15** | Medium | **Discovery identity names differ:** `rplab_monitor` (discovery) vs `kf_monitor` (approved). Documentation must not be followed blindly. | `PROPOSED_NTFY_ACCESS_CONTROL_MODEL.md` |
| **C-16** | Low | **Documentation drift on UI scope.** `FREEBUFF_IMPLEMENTATION_MASTER_PLAN.md` says "five destinations"; the app has seven (Command added in Phase 4 Stage E). | `MainViewModel.kt:923-929` |
| **C-17** | Medium | **`CURRENT_ARCHITECTURE.md` is stale.** It describes the prototype (no Room, sample data, `nfty` URL). It must not be used for the redesign without correction. | `CURRENT_ARCHITECTURE.md` §5–§9 |
| **C-18** | Medium | **Website producer identity is unresolved.** `kf-web` is assigned to `kf_monitor`, but the website is a distinct long-lived application with its own event pipeline. One shared monitor identity gives the website write access to every infrastructure topic. | Catalogue row 1; ACL matrix |
| **C-19** | Low | **`kf-news` topic and `kf_news` identity share a name stem.** Workable, but scripts and ACL commands must not confuse the two. | Catalogue / ACL matrix |
| **C-20** | Medium | **Priority standard still names `rplab-*` topics.** The policy (5/4/3/2/1, dedupe, recovery) is authoritative; its topic table is not. | `NOTIFICATION_PRIORITY_AND_FORMAT_STANDARD.md` §8 |
| **C-21** | Low | **App topic permission badges are local fiction.** `TopicPermission` is an app enum; it is not read from the server and must not be presented as server-authoritative ACL state. | `NftyTopic.kt`, `TopicsScreen.kt` |
| **C-22** | Medium | **No gateway deployment host.** Mint VM200 is not always-on; the master plan lists this as an open blocker. The gateway cannot be relied on for production notifications yet. | Master plan §8 |

---

## 5. What is genuinely complete vs only documented

### Genuinely implemented and locally tested

1. Android client foundation: Room v2, explicit migration, profiles, outbox, activity, publish, delivery modes, E-Ink theme, mock command centre, seven destinations.
2. Gateway read-only PVE surface, six lifecycle mutations behind a disabled flag, request validation, replay cache, rate limiting, size limit, 9 tests.
3. Android `GatewayCommandExecutor` HTTP boundary and its MockWebServer tests.
4. Legacy ntfy production path: `KAMINARI-FUJI-Homelab` receives PVE, dashboard, watchtower and Docker events; `kaminari-onboarding-test` is a working test topic; `kaminari-diary` is active.

### Implemented but **not safe to enable**

1. Gateway mutations: approval is self-asserted (C-08), replay is memory-only (C-09), risk classes are absent (C-10), and there is no durable audit (C-07).
2. `kaminari_app` token plan: cannot express the intended Android/gateway split (C-02).
3. Level 4 destructive operations: specified, **not implemented**, and must not be inferred from the current start/stop/restart surface.

### Documented only

1. All 22 `kf-` topics and all six identities.
2. Every ACL, including the effective permission matrix.
3. Level 4 destructive catalogue (destroy, disk removal, backup restore/delete, volume delete, firewall change, user deletion).
4. Music, news and stickers notification contracts and payload schemas.
5. Owner dashboard, News screen, pipeline status UI, audit history UI, connection diagnostics, web console.

### Deployed

1. ntfy 2.28.0 behind Cloudflare Tunnel, **unauthenticated**, memory-only cache.
2. Three legacy topics with real producers.
3. Read-only PVE gateway token (not the gateway service itself).

---

## 6. Owner decisions required before Phase 1 / Phase 5

Phase 1 is safe application/contract work and can proceed with **P-01 to P-03** answered. Phase 5 must not start until **P-04 to P-06** are answered.

| ID | Decision | Why it blocks work |
|---|---|---|
| **P-01** | Confirm `kf-` supersedes `rplab-` everywhere, including the discovery documents and the priority standard's topic table. | Prevents building against two namespaces (C-01, C-15, C-20). |
| **P-02** | Resolve the Android/gateway token split. Options: (a) accept one `kaminari_app` user with combined read/write on command topics and rely on gateway-side authorization; (b) add a seventh `kf_gateway` identity despite the "six identities" lock — the superseded discovery model already proposed exactly this as `gateway_exec`; (c) have the gateway own `kf-command-result`/`kf-admin-audit` under its own user. | Without it the ACL matrix cannot be applied as written (C-02, C-03). |
| **P-03** | Decide whether the Android APK may ship an admin-capable ntfy client that is not wired to UI (`NtfyAdminClient`). Recommend removing it from the release variant or keeping ntfy administration CLI-only. | Security boundary (C-12). |
| **P-04** | Choose the gateway deployment host and TLS path, or explicitly defer gateway deployment and keep the command centre mock-only. | Master-plan blocker (C-22). |
| **P-05** | Approve the durable audit store (local append-only file/database) in addition to `kf-admin-audit`, because ntfy cannot be the system of record. | Audit integrity (C-07). |
| **P-06** | Approve true step-up reauthentication design (separate credential/TOTP, server session, 5-minute window) and reject the current nonce-echo approval. | Destructive-operation safety (C-08). |
| **P-07** | Resolve `kf-diary` write ownership: remove `kf_stickers` write or amend the stickers contract. | Contract contradiction (C-06). |
| **P-08** | Decide `kf-iot` ACL now or leave it created-but-denied until a real producer exists. | Least privilege (C-13). |
| **P-09** | Confirm legacy test topic access for `kaminari_app` during migration. | Safe ACL migration test (C-14). |

---

## 7. Phase 0 verdict

**PASS WITH CONDITIONS.**

- The requested reconciliation is complete and evidence-backed.
- No production state was changed.
- Phase 1 (documentation/schemas/tests, no production infrastructure) can begin after P-01…P-03.
- Phase 5 (controlled production migration) remains blocked by P-02, P-04…P-09 and by the Workstream A backup/storage blockers recorded in `BACKUP_BASELINE_PLAN.md`.
- Nothing in this phase authorises creating topics, accounts, tokens or ACLs.

**Next action:** owner review of this report and the four companion deliverables. Per the operating rule, do not continue into implementation without explicit approval.
