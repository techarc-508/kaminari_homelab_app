# Android and Web Gap Analysis — Freebuff Phase 0

**Android baseline:** `com.rplab.kaminarinfty`; 361 unit tests pass (0 failures, 2 skipped); no device/emulator attached.
**Web baseline:** **no web frontend exists in this repository.**
**Scope:** reconcile the existing app against the Workstream B requirements (§11 of the workstream and `KAMINARI_LEVEL4_CONTROL_PLANE_SPEC.md`). No app or web code was changed in Phase 0.

---

## 1. What the Android app actually has

| Area | Verified state |
|---|---|
| Navigation | Seven primary destinations: Inbox, Topics, Compose, Outbox, Activity, **Command**, Settings. Hand-rolled enum switch, not `NavHost`. Setup, Detail and Search are drill-ins. |
| Inbox | Room-backed, read/unread, saved filter, search, refresh, delete/clear confirmations, profile isolation |
| Topics | User-entered topic name/description, subscribe/pause, Add Topic, unsubscribe confirmation, empty/error states |
| Compose | Topic/title/body, priority 1–5, tags, click URL, attachment URL metadata, delay, validation, accepted/failed/ambiguous outcomes, draft preservation |
| Outbox | Durable queue, manual retry, ambiguous confirmation, cancel, bounded retention |
| Activity | Profile-scoped timeline with All/Publish/Inbox/Attention/Commands filters and sanitized summaries |
| Command | Typed `rplab.command.v1` envelope, six-action catalogue, preview, confirmation, history; **mock executor only** |
| Settings | Profiles (add/edit/select/delete), connection test, delivery mode, notification status, theme (Standard/E-Ink), density, local-data actions, privacy |
| Persistence | Room v2 (`server_profiles`, `topic_subscriptions`, `messages`, `outbox_entries`, `activity_events`, `command_requests`), explicit `MIGRATION_1_2`, exported schema |
| Secrets | Keystore-backed `SecretStore`; never in UI state or Room |
| Delivery | Instant (foreground stream) + Battery Saver (WorkManager catch-up); `DeliveryStatusStore`; boot restore; Android 15 timeout fallback |
| Profiles | Standard Kaminari + **E-Ink theme profile** (palette/tokens); no Boox-specific tuning |
| Publisher | Documented JSON/plain publish client; typed outcomes; no auto-retry |
| Gateway client | `GatewayCommandExecutor` exists and is MockWebServer-tested, but is **not constructed by `AppContainer`** and has no configuration surface |
| ntfy admin client | `NtfyAdminClient` exists and is tested, but is **not wired to UI** and is not constructed at runtime |

### 1.1 Default state correction

A first run does **not** subscribe to `KAMINARI-FUJI-Homelab` or any production topic. It seeds no topics; the user adds one. The sample topic names (`homelab-alerts`, `backups`, `build-server`, `iot-telemetry`, `security`) are demo data and are **not** live server topics. The redesign must explicitly seed the approved `kf-` catalogue or the app will start empty.

---

## 2. Gap summary against Workstream B §11

| Requirement | Current | Gap |
|---|---|---|
| Preserve Standard Kaminari profile | Yes | None; must not regress |
| Preserve E-Ink profile | Yes (generic) | None; Boox hardware validation still pending |
| Preserve Instant and Battery Saver | Yes | None; device validation still pending |
| Low-RAM operation | Bounded lists/retention | Must be re-measured after new screens |
| Existing notification channels | Yes | None |
| Offline-friendly behaviour | Room is source of truth | Partial; pipeline/report views must tolerate no network |
| Existing local database/state | Room v2 | New models need migration v3 |
| Topic discovery and filtering | Manual entry only | **Missing**: no `kf-` catalogue, no category, no domain grouping, no priority filter in UI |
| Notification delivery reliability | Tested locally only | **Unverified on device**; no new work in Phase 0 |
| Pipeline status visualization | None | **Missing**: music/news/stickers |
| Backup and infrastructure reports | None | **Missing** |
| Command preview and approval | Preview exists; approval is client-side | **Missing**: real gateway wiring, server-side step-up, exact-target UX |
| Audit history | Local activity only | **Missing**: `kf-admin-audit` history and gateway audit reference |
| Connection diagnostics | Status badge + connection test | **Missing**: bounded diagnostic log/export, auth vs reachability distinction is only partly surfaced |
| Error states | Present on main screens | Must be added for new screens |
| E-Ink readability | Generic high-contrast theme | New screens must be designed E-Ink-first, not tinted |
| Web access | **No web app** | **Entire capability missing** |

---

## 3. Concrete code-level gaps

### 3.1 Topic model

| Item | Finding |
|---|---|
| `NftyTopic` | `name`, `description`, `messageCount`, `isSubscribed`, `isPaused`, `lastActivityTime`, `permission` |
| `TopicPermission` | `GUEST_READ`, `AUTH_REQUIRED`, `RESTRICTED` — a **local enum**, not server state |
| `TopicSubscriptionEntity` | Has `priorityThreshold` column, but `ProfileRepository.observeTopics` does not map it; the UI hard-codes "Priority filter: all" |
| Catalogue | No `kf-` constant, no catalogue seeding, no category/domain field, no publisher/retention metadata |

Required: a typed `KfTopic` catalogue (name, domain, purpose, sensitivity, active/reserved, default subscription), a Room v3 migration adding category/priority, and removal of misleading permission badges or replacement with server-derived state.

### 3.2 Command integration

| Item | Finding |
|---|---|
| `COMMAND_REPLY_TOPIC` | `"rplab-command-results"` — must become `kf-command-result` |
| Action catalogue | Six app actions do not match the gateway's actions (see `FREEBUFF_PHASE0_RECONCILIATION.md` C-05) |
| Executor wiring | `AppContainer` constructs `RoomCommandRepository(executor = MockCommandExecutor())`; the real gateway executor is never used |
| Gateway configuration | No URL/token fields in Settings, no Keystore entry for a gateway token |
| Approval | The app generates the nonce and sends it as the approval token; there is no step-up reauthentication |
| Result correlation | The app does not read `kf-command-result`; it only reads the HTTP response |

### 3.3 Missing screens / models

- News: card list, category/source filter, saved, pagination, open original URL, offline/empty states.
- Music: pipeline stage timeline and final report summary (requested/downloaded/skipped/failed/indexed).
- Stickers: reserved screens clearly labelled "not implemented".
- Owner dashboard: PVE host/CT/VM, storage, backup, Docker, network, security, monitoring, ntfy and pipeline health.
- Audit history: gateway audit events with actor/operation/target/risk/result.
- Connection diagnostics: bounded, sanitized event log and export.
- Web console: none.

### 3.4 Contract mismatches

| Contract | Current app | Required |
|---|---|---|
| Music | none | `rplab.music.v1`, `rplab.music.report.v1` |
| News | none | `rplab.news.v1`, `rplab.news.report.v1` |
| Stickers | none | `rplab.stickers.v1`, `rplab.stickers.report.v1` (reserved) |
| Command | `rplab.command.v1` + six mock actions | same schema, gateway-aligned action IDs, `kf-command-result` reply topic |
| Topic names | user-entered / sample | 22 `kf-` topics, exactly |

---

## 4. Web gap

There is no web client, no API client, no build system and no authentication UI for a web control plane.

The Level 4 spec requires the web console to use the **same API and authorization model** as Android. That means the web work is blocked on the gateway work, not merely on frontend effort:

1. Gateway identity, sessions, CSRF and step-up must exist first.
2. The read operation catalogue must be stable and typed.
3. Only then can a thin web console be built against the same contract.
4. It must not receive `kfadmin` or PVE credentials.

**Recommendation:** do not start a web frontend in Phase 1. Treat it as Phase 3+ after the gateway contract is real. Building a web UI over the current shared-token API would duplicate an unsafe boundary.

---

## 5. Preservation constraints for every change

1. **Do not break** Inbox, Topics, Compose, Outbox, Activity, Settings, Setup, Search.
2. **Do not remove** Standard or E-Ink profiles, Instant or Battery Saver, profile isolation, Keystore secrets, Room source-of-truth, bounded retention.
3. **No destructive Room migration.** Add explicit `MIGRATION_2_3` and test it from the exported schema, as `Migration1To2Test` does.
4. **No admin credential in the app.** Resolve C-12/P-03 before shipping further builds.
5. **No PVE credential in the app.** The gateway token is a gateway credential, not a PVE credential, and must still be stored in the Keystore.
6. **No new dependency without a written reason**, minSdk/RAM/APK impact and maintenance note.
7. **E-Ink first:** new screens need monochrome, static, text-first states with no color-only meaning.
8. **Honest states:** an accepted publish is not delivery; a mock result must say MOCK; a reserved pipeline must say reserved.

---

## 6. Proposed work by phase

### Phase 1 — Contract and model work (no production infrastructure)

1. Add the typed `KfTopic` catalogue and payload models for music/report, news/card/report, stickers (reserved).
2. Add `MIGRATION_2_3` for topic category/priority and any new local tables; test from the exported v2 schema.
3. Add contract tests for the 22-topic mapping and the ACL expectations (pure Kotlin, no network).
4. Add fixtures/mocks for the three pipeline schemas.
5. Fix `COMMAND_REPLY_TOPIC` to `kf-command-result` **with a contract test**.
6. Align the Android action catalogue with the gateway catalogue or introduce a shared contract artefact. Do not wire real execution yet.
7. Remove or release-gate `NtfyAdminClient` per P-03.
8. Add an explicit "server-authoritative vs local" distinction for topic permission display.

### Phase 3 — Application integration

1. Seed/allow the approved topic catalogue and add domain filtering.
2. Add News, Music report, Stickers-reserved and Owner dashboard/report screens.
3. Wire `GatewayCommandExecutor` behind a validated gateway profile, still defaulting to mock until the gateway is deployed.
4. Add command preview, exact-target confirmation, step-up and audit-reference display against the Phase 2 contract.
5. Add connection diagnostics.
6. Run Standard and E-Ink regression tests for every new screen.
7. Do not start the web console until the gateway contract is implemented.

### Phase 4 — Pipeline integration

1. Music publisher integration with the **existing** Memos → downloader → Navidrome workflow (inspect it first; do not replace it).
2. News card publisher and report emitter.
3. Stickers: contracts and dormant topic preparation only; **do not claim implementation**.
4. End-to-end notification tests using a test topic, not production alerts.

---

## 7. Test status and what is not proven

| Test area | State |
|---|---|
| Unit/contract | 361 pass, 2 skipped (Keystore hardware) |
| Compose UI (Robolectric) | Present for main screens |
| Room migration | v1→v2 tested; v2→v3 does not exist yet |
| Real ntfy server | Health reachable; app publish/receive smoke test not repeated in this phase |
| Android device / Doze / OEM | **Not tested**; no device attached |
| Boox E-Ink | **Not tested**; model unknown |
| Gateway HTTP integration | MockWebServer only; gateway undeployed |
| Web | not applicable (no code) |

---

## 8. Verdict

The Android app is a solid notification client with a durable local model and an honest mock command centre. It is **not yet** the owner console described by Workstream B: it has no `kf-` catalogue, no pipeline views, no owner dashboard, no real gateway wiring and no web client.

Phase 1 can safely add models, tests and migrations without touching production. Phase 3 must not claim a working control plane until the gateway identity, step-up and audit gaps in `GATEWAY_GAP_ANALYSIS.md` are closed.
