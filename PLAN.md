# Kaminari Nfty — V1 Product, Architecture & Delivery Plan

**Status:** Living plan — Phase 3 accepted (PASS WITH CONDITIONS); Phase 4 complete (Stages A–F; Stage F PASS WITH CONDITIONS); Phase 5 complete (PASS WITH CONDITIONS)
**Target:** First dependable daily-use Android model, not a feature-complete ntfy clone  
**Platform:** Native Android / Kotlin / Jetpack Compose  
**Default server URL found in source:** `https://nfty.rplab.qzz.io` (DNS/TLS verified; origin returned HTTP 502 on 2026-11-08 — re-check before live use)

**Document authority:** this repository-root `PLAN.md` is authoritative. The copy under `KaminariNfty_V1_Plan/` is a mirror and must not diverge. The Phase 1/2 reports are mirrored into the repository root for a self-contained record.

---

## 0. Executive decision

Do not add more UI on top of the current implementation yet. Preserve the useful Compose screens and the existing JSON-stream proof of concept, but first replace the prototype's state/network foundation with a small, testable architecture.

The first usable release must do five things reliably:

1. Connect to a configured ntfy server and clearly distinguish live data from demo data.
2. Persist configuration, subscriptions, messages, saved/read state, and delivery/command history locally.
3. Receive messages in the foreground, show Android notifications, and recover cleanly after network loss/process death.
4. Publish messages and send safe, auditable structured commands with explicit result correlation.
5. Provide a dedicated high-contrast E-Ink profile and a low-resource profile without sacrificing basic usability.

**The locked product scope includes both ntfy administration and homelab/PVE administration.** Implement ntfy account, user, access and topic administration only where a documented and verified API/CLI integration exists; mark undocumented/internal endpoints experimental and version-gated. Add a separately deployed, authenticated PVE command gateway for homelab operations. Server configuration, database maintenance, restart, backup/restore and other privileged operations must execute through that trusted gateway—not through arbitrary shell commands or root credentials embedded in the Android app.

## 0.1 Owner decisions — LOCKED

These decisions are authoritative and override any older open-question wording elsewhere in this plan:

| Decision | Locked choice | Engineering consequence |
|---|---|---|
| Delivery | **Both: Instant + Battery Saver** | Implement explicit user-selectable modes and explain latency/battery trade-offs. Instant mode uses a visible foreground connection when active; Battery Saver uses scheduled reconciliation/polling and is explicitly best-effort/delayed. Add optional push integration only as a separately configured capability. |
| Administrator scope | **Both: ntfy + PVE/homelab** | Build ntfy administration for verified capabilities and a separate PVE command gateway. Keep privileged PVE execution out of the Android client. Deliver core ntfy client first; gateway integration follows the secure command protocol and threat-model gate. |
| UI | **Dual complete profiles** | Standard Kaminari profile and a dedicated E-Ink profile, each with coherent layouts, typography, contrast, controls and interaction rules. E-Ink is not merely a grayscale theme toggle. |
| Onyx Boox model | **Unknown / device not yet received** | Keep generic E-Ink behavior model-agnostic. Do not assume refresh APIs, Android version, screen dimensions or firmware. Defer vendor-specific refresh tuning until the exact device/model and firmware are available. |

### Updated implementation priorities

1. **P0 — reliable core:** reproducible build, secure configuration, Room persistence, real ntfy receive/publish, deduplication, reconnect and honest connection state.
2. **P1 — two delivery modes:** implement Instant and Battery Saver as first-class modes with clear status, permission handling, battery/latency disclosure and tested mode switching. Optional FCM/UnifiedPush is not a substitute for validating both baseline modes.
3. **P2 — dual UI foundation:** implement shared design tokens and accessibility first, then complete Standard and generic E-Ink profiles in parallel with the core screens. Keep E-Ink static, high-contrast, low-redraw and usable without model-specific APIs.
4. **P3 — ntfy administration:** capability discovery and admin screens for verified server-supported operations; show unsupported/experimental status honestly.
5. **P4 — PVE gateway:** threat model, signed/authenticated command envelope, allowlisted operations, expiry/replay protection, explicit confirmation for destructive actions, executor audit log and correlated results. Do not ship arbitrary shell execution.
6. **P5 — device-specific validation:** once the Boox arrives, record model, Android version, firmware and panel capabilities; then tune refresh behavior and run real-device tests. Until then, use emulator/phone and generic E-Ink validation.

The first installable working model still prioritizes the reliable core and both delivery modes. The complete admin scope is a roadmap commitment, not permission to delay the first usable client or bypass security gates.

## 1. Repository audit — evidence and implications

### 1.1 What is already present

- Kotlin Android application with Jetpack Compose and Material 3.
- `minSdk = 24`, `targetSdk = 36`, Java 11 bytecode settings.
- Screens for setup, inbox, notification detail, topics, saved messages, search and settings.
- `MainViewModel` with basic navigation, filters, save/read actions, topic toggles and simulation states.
- `NftyStreamClient` with HTTP JSON streaming, Basic/Bearer auth header construction, event parsing and a small set of HTTP/network error mappings.
- `NftyRepository` with a single streaming job, reconnect delay/backoff and in-memory ID deduplication.
- Sample data provider and a few example tests.
- Dependencies declared for Room and DataStore, but no actual database, DAO, migrations or settings DataStore implementation was found in the supplied source tree.

### 1.2 Blocking findings

| Finding | Evidence in supplied tree | Consequence | Required correction |
|---|---|---|---|
| Sample data is the initial app state | `MainViewModel` initializes from `SampleDataProvider` | Demo data can be mistaken for live notifications | Explicit demo mode; production starts empty and labelled |
| Stream starts from ViewModel initialization | `MainViewModel.init` calls `restartLiveStream()` | Network lifecycle is not tied to user intent/app lifecycle; default topics/config can start unexpectedly | Explicit connection controller; lifecycle-aware foreground/background policy |
| Config is in-memory | `updateServerConfig` updates StateFlow only | Server/auth/subscription preferences can disappear after process death | DataStore for preferences; Android Keystore-backed secret encryption |
| Message state is in-memory | lists are held in `MainUiState` | Lost history, no durable dedupe, no offline inbox | Room database and repository as single source of truth |
| No Android notification delivery implementation | Manifest only declares INTERNET and ACCESS_NETWORK_STATE | Receiving a stream event does not create a system notification | Notification channels, runtime permission, notification renderer/actions |
| No background execution strategy | No service, worker or push receiver found | Stream stops when process is killed; Android may restrict background work | Explicit delivery modes and tested Android lifecycle handling |
| No publish client | `NftyStreamClient` only contains connection/stream operations | User cannot send messages from app | Authenticated POST/PUT publisher with structured headers/body |
| No command protocol | No command model/dispatcher/result correlation found | No trustworthy end-to-end command workflow | Structured command envelope + trusted server-side executor |
| No real admin repository | Topics are local `NftyTopic` values | UI toggles can be confused with server-side ACL changes | Separate local subscriptions from remote admin state |
| Weak stream robustness | blocking `execute()` inside Flow, `readTimeout(0)`, in-memory dedupe, parse errors dropped | Cancellation, reconnect, memory growth and observability risks | Call cancellation bridge, bounded state, durable cursors/dedupe, metrics |
| Misleading connection status | auth state is inferred from selected auth mode after HTTP 200 | A successful request does not necessarily prove admin/superuser rights | Model transport, authentication, authorization and role separately |
| No build launcher in ZIP | no `gradlew` or `gradle/wrapper/gradle-wrapper.jar` | Reproducible local build is not immediately available | Add wrapper from approved Gradle distribution and commit wrapper files |
| Generic app identity/config | namespace `com.example`, app ID `com.aistudio...`, AI Studio build plugins | Package identity and release pipeline are not production-owned | Confirm final applicationId/signing before first public install |
| Unnecessary/unclear dependencies | Firebase AI/App Check/Google Services and unused camera/location/Coil declarations | More build complexity, binary size and configuration risk | Dependency usage audit; remove unused integrations in isolated commits |
| Minimal tests | example tests assert sample data and basic in-memory operations | Does not test real API, persistence, notification, lifecycle or failure cases | Contract, repository, database, UI and device tests |

### 1.3 Build verification limitation

The supplied ZIP does not contain `gradlew` or `gradle-wrapper.jar`; therefore a reproducible Gradle build/test could not be launched from this snapshot. Do not report the project as build-verified. The first engineering task is to establish the exact Android Studio/JDK/Gradle/AGP compatibility and add the Gradle wrapper. The repository declares Gradle 9.3.1, AGP 9.1.1 and Kotlin 2.2.10; verify those versions together on the development VM rather than blindly upgrading them.

## 2. Product definition and user experience

### 2.1 Core navigation

Use a simple four/five destination structure, with a clear hierarchy:

- **Inbox** — chronological feed, unread/priority/topic filters, search, bulk actions.
- **Topics** — local subscriptions, per-topic notification preferences, access visibility when server permits it.
- **Compose** — publish a message, choose topic, title/body/tags/priority/actions/attachment where supported.
- **Activity** — delivery attempts, sent messages, command runs, acknowledgements and diagnostics.
- **Settings / Admin** — device preferences, server profiles, account and permission information, diagnostics, and guarded admin operations.

Notification detail should be a drill-in screen, not a permanent bottom navigation item. Keep a compact layout and avoid decorative dashboard clutter. The visual language should remain Kaminari/RPLab: restrained, deliberate, legible, with a practical field-console feel rather than generic gradients, oversized cards or excessive motion.

### 2.2 First-run experience

1. Welcome screen explains that this is a client for an existing ntfy server.
2. Add server URL; normalize scheme and trailing slash; validate host and TLS.
3. Select Guest, username/password or access token. Never prefill a real password.
4. Test health endpoint and perform an authenticated read/write capability probe only when the user asks.
5. Add topics manually or import a text list; never auto-subscribe to sample topics.
6. Explain Android notification permission and delivery modes.
7. Offer a safe test message with a clearly named test topic.
8. Enter Inbox with an empty-state prompt if there are no live messages.

Demo data must be opt-in, visibly marked `DEMO`, isolated from production data, and easy to clear. Do not load demo content by default.

## 3. Architecture target

Adopt a small layered architecture; do not over-engineer into dozens of modules for V1.

```text
Compose UI
  └── ViewModels (screen state + user intent only)
       └── Domain use cases (connect, subscribe, publish, mark, command, admin)
            └── Repositories (interfaces)
                 ├── ntfy HTTP API client (OkHttp)
                 ├── Room database (messages, topics, outbox, audit events)
                 ├── DataStore (non-secret preferences)
                 ├── Keystore-backed credential store
                 └── Android delivery adapters (NotificationManager, Service/Worker)
```

Suggested package layout:

```text
com.rplab.kaminarinity/
  app/                 Application, MainActivity, navigation, app startup
  core/model/          Shared domain models and result/error types
  core/network/        OkHttp config, URL builder, auth interceptor, API DTOs
  data/local/          Room database, entities, DAOs, migrations
  data/preferences/    DataStore preferences and encrypted credential facade
  data/remote/         NtfyApi, stream parser, admin compatibility API
  data/repository/     Repository implementations
  domain/              Use cases and validation
  delivery/            Notification channels, renderer, foreground service, workers
  commands/            Command envelope, allowlist, outbox, result correlation
  ui/common/           Reusable Compose controls
  ui/inbox/ topics/ compose/ activity/ settings/ admin/
  diagnostics/          Health checks, event log and export
```

Keep dependency injection simple. Constructor injection and a small application container are sufficient initially; use Hilt only if the dependency graph genuinely justifies it. Do not add Firebase AI, camera, location, or unrelated libraries to a notification client.

## 4. Low-end device and RAM strategy

Target an experience that remains responsive on Android 7/API 24+ class devices, while validating on at least one real low-memory Android device. Define a minimum test profile of 2 GB RAM, constrained CPU, 720p display and intermittent mobile data. API 24 support must be checked against current dependency minSdk requirements; if an essential library no longer supports it, make a deliberate minSdk decision and document the trade-off.

### Required engineering rules

- Lazy lists for inbox/topic/activity content; stable keys; no eager rendering of all history.
- Room paging or bounded queries; do not keep the entire notification history in `MainUiState`.
- Bounded in-memory caches and bounded deduplication; no unbounded `MutableSet` of message IDs.
- Stream parser processes one line at a time; cap line/body sizes; handle malformed events with a diagnostic counter rather than silent swallowing.
- Avoid image loading by default; remote attachments load only after user action. Thumbnail cache must be bounded.
- Avoid large custom fonts/assets on the critical path; keep existing font assets only if they materially improve readability.
- No continuous animations, blur, parallax, shimmer, heavy shadows or auto-refreshing charts.
- Avoid polling while app is active if a stream is already connected. Avoid a permanent connection in battery-saver mode.
- Use structured concurrency, cancellation, timeouts and bounded retry with jitter.
- Use release builds, baseline profiles only after profiling, and R8/minification after reflection/serialization rules are tested.
- Record startup time, memory, CPU, battery/network use and APK size on the target devices.

### Delivery modes (be honest about Android constraints)

1. **Instant / foreground mode:** user explicitly enables it; use a user-visible foreground service and persistent notification for a live stream. This is the direct self-hosted SSE path but costs more battery and is subject to Android foreground-service policy and OEM process management.
2. **Battery saver mode:** no always-on socket; use scheduled WorkManager polling/reconciliation. This is lower power but not instant and WorkManager timing is inexact under Doze.
3. **Push mode (optional advanced):** FCM requires a Firebase app and corresponding ntfy server-side Firebase configuration/key; it is not automatically available merely because Firebase dependencies are in the APK. UnifiedPush is another possible ecosystem path, but requires explicit protocol/distributor integration and device testing.

The settings screen must explain these trade-offs. Never promise guaranteed instant delivery in battery-saver mode or guaranteed background execution on every Android OEM.

## 5. Full E-Ink mode — Onyx Boox profile

This is a full rendering and interaction mode, not just a monochrome theme toggle.

### Visual rules

- Strict white/off-white background, black text, grayscale borders and fills only; no color-only status signals.
- Use strong text contrast, clear hierarchy, generous line spacing and readable touch targets.
- Remove gradients, transparency, shadows, blur, animated transitions, infinite motion, pulsing indicators and shimmer.
- Replace animated progress with static labelled values and a manual refresh action.
- Prefer thin solid borders, simple icons, text labels and stable layout geometry.
- Avoid large photographic backgrounds and decorative textures in E-Ink mode.
- All icons must have accessible text or labels; never rely on color.
- Support portrait and landscape, larger text, keyboard/navigation and stylus/touch interaction.

### Interaction and power rules

- Provide a global E-Ink toggle and a separate refresh policy: manual, foreground-only, or conservative interval.
- Do not continuously animate a connection indicator. Show a timestamp and a static state.
- Batch UI updates when possible; avoid unnecessary recomposition and frequent timer ticks.
- Add explicit refresh controls to Inbox, Topics and Activity.
- Use predictable navigation and stable screen regions to reduce ghosting.
- Provide an optional high-refresh action before/after scrolling if supported by a verified Boox SDK/device API. Do not call undocumented vendor APIs or claim app-wide refresh control until tested on the target Boox model.
- Respect system refresh behavior by default; app-level UI cannot guarantee panel waveform/refresh behavior on all E-Ink hardware.

### E-Ink acceptance

Test on the actual Onyx Boox model: all screens legible, no colored-only meaning, no animation, usable touch targets, no clipped content in portrait/landscape, and no accidental high-frequency polling. Test ghosting and refresh behavior manually. Add screenshot review at the device's native resolution.

## 6. ntfy integration scope

### 6.1 Core documented endpoints

Implement a typed `NtfyApi` around documented HTTP behavior. The precise API contract must be verified against the ntfy server version running at `nfty.rplab.qzz.io` before enabling server-specific features.

- Health: `GET /v1/health` (check both HTTP status and `healthy` JSON field).
- Read stream: `GET /{topic-or-comma-separated-topics}/json?since=...` (NDJSON line stream).
- Poll/replay: JSON subscription with `poll=1` and `since=` for bounded catch-up.
- Publish: `POST`/`PUT /{topic}` with body and supported headers; optionally JSON publish to server root with a topic field.
- Auth: Basic authentication for username/password; Bearer token for access token. Never put credentials in query strings unless a server-specific constraint requires it and the user explicitly accepts the risk.
- Access/account: implement only documented endpoints confirmed for the server version. Treat admin/user management endpoints described as undocumented/internal as experimental compatibility features, not guaranteed public API.

Official reference pages:
- Subscribe API: https://docs.ntfy.sh/subscribe/api/
- Publish API: https://docs.ntfy.sh/publish/
- Server configuration/access control: https://docs.ntfy.sh/config/
- Release notes: https://docs.ntfy.sh/releases/

### 6.2 Incoming notification pipeline

```text
ntfy stream/poll
  → parse + validate event
  → durable dedupe (server message ID)
  → Room transaction (message + received event)
  → notification policy (topic/priority/mute/DND)
  → Android NotificationManager
  → user action (open / mark read locally / reply / publish ack)
  → audit event
```

- Handle `open`, `keepalive`, `message`, `poll_request`, `message_delete`, `message_clear` and unknown event types without crashing.
- Preserve server message ID, timestamp, topic, title, message, priority, tags, click URL, attachment metadata, actions, expiry and raw-safe metadata needed for future parsing.
- Parse timestamp units correctly and handle absent optional fields.
- Deduplicate durably by `(server_profile_id, message_id)`.
- Keep local read/saved state distinct from ntfy server state; do not imply ntfy has read receipts.
- Notification channel per app/topic category; Android 13+ runtime notification permission; deep links into exact message detail.
- Respect priority, mute, quiet hours, per-topic notification settings and duplicate notification policy.
- Never automatically open arbitrary action URLs or execute arbitrary HTTP actions without confirmation and a strict URL policy.

### 6.3 Publish pipeline

Compose screen supports topic, title, message, priority, tags, click URL, actions and attachment metadata as supported by server/version. Add input validation, payload size feedback, retry-safe outbox and send history.

A publish HTTP success means the server accepted the request; it does **not** prove a phone displayed or a human read the notification. Show states precisely: queued locally, request sent, server accepted, receiver acknowledged (only if a receiver app/gateway actually sent an acknowledgement), failed.

### 6.4 User commands and end-to-end reporting

Do not execute arbitrary shell commands from an incoming ntfy message inside the Android app. For homelab actions, define a small, explicit command protocol and run a trusted executor on the PVE/home server.

Example envelope:

```json
{
  "schema": "rplab.command.v1",
  "command_id": "uuid",
  "issued_at": "ISO-8601",
  "expires_at": "ISO-8601",
  "requested_by": "android-user",
  "target": "pve-node",
  "action": "service.status",
  "args": { "service": "navidrome" },
  "reply_topic": "rplab-command-results",
  "requires_confirmation": false
}
```

Rules:
- Allowlisted action identifiers only; no free-form shell string.
- Validate schema, target, expiry, nonce/replay protection, user identity and permission on the executor.
- Destructive actions require explicit in-app confirmation and server-side authorization.
- Executor publishes a result with the same `command_id`, status, timestamps, duration and sanitized output summary.
- Android stores request and result events in Room and correlates by ID.
- Show timeouts and unknown outcomes as `No acknowledgement received`, never as successful execution.
- Use separate request/result topics with least-privilege ACLs.
- Keep root passwords, SSH private keys and PVE API secrets out of the Android app and out of notification payloads.

Suggested first safe commands: `service.status`, `host.health`, `storage.summary`, `backup.latest_status`, `container.status`, `service.restart` only after explicit confirmation and executor-side policy. Do not include arbitrary terminal access in V1.

### 6.5 Admin capability matrix

| Capability | V1 handling | Notes |
|---|---|---|
| Server health/readiness | Implement | Health endpoint; readiness if applicable to server version |
| Current authenticated identity/role | Implement only where supported | Never infer admin from Bearer token presence |
| Current user's access/permissions | Implement if documented endpoint/version confirms it | Show read/write/deny accurately |
| Topic subscription add/remove/pause | Implement locally | Does not create/delete server topics; topics are generally implicit |
| Publish test / read test | Implement | Confirm target topic and show exact result |
| User list/create/delete/role changes | Experimental adapter only after endpoint/version contract is confirmed | ntfy docs describe CLI commands; some HTTP admin APIs are undocumented/internal |
| ACL view/edit | Experimental adapter only after server contract is verified | Do not imply local topic permission enum is authoritative |
| Access token create/revoke | Only if supported by server API and safely tested | Otherwise link to server web UI/CLI guidance |
| Server config edit/reload | Not a generic client feature | Requires server-side configuration access and restart/reload mechanism |
| Database/cache/attachment management | Not a generic client feature | Server-side operator capability |
| Backup/restore | Not a generic client feature | Use a separate homelab management agent with verified backup workflow |
| Server logs/metrics | Optional integration | Only if exposed by protected server/monitoring API |
| App diagnostics/export | Implement | Local connection log, event log and sanitized support bundle |

## 7. Persistence and data model

Use Room as the source of truth for notification and operational history. Use DataStore for non-secret settings. Use Android Keystore to protect encryption keys for credentials; do not store passwords/tokens in plain preferences, logs, backups or analytics.

Suggested Room tables:

- `ServerProfileEntity`: profile ID, display name, normalized URL, auth type, enabled flag, timestamps (no plaintext secret).
- `TopicSubscriptionEntity`: server profile, topic, local subscribe/pause/mute, priority threshold, notification preferences.
- `MessageEntity`: server profile, message ID, time, topic, title/body, priority, tags JSON, click, attachment metadata, expiry, received/read/saved flags, source.
- `OutboxEntity`: publish/command request, idempotency key, retry count, state, next attempt, created/updated times.
- `DeliveryEventEntity`: request ID, server response code, accepted/failed, timestamps, error category, sanitized detail.
- `CommandRunEntity`: command ID, action, target, status, request/result times, result summary, confirmation state.
- `DiagnosticEventEntity`: bounded structured events for troubleshooting.

Use explicit Room migrations; never destructive migration in production. Add retention controls (e.g. configurable history window) and storage usage reporting. Use paging for long histories.

## 8. Security and privacy baseline

- HTTPS required by default. Allow HTTP only for explicitly acknowledged trusted LAN/testing scenarios.
- Validate URLs, reject credentials in URL user-info, reject cleartext external hosts by default.
- Store secrets encrypted using Keystore-backed key material; redact authorization headers and bodies from logs.
- Never log message bodies by default; diagnostic export must be opt-in and redact secrets/URLs/tokens.
- Do not use remote analytics/AI services for message content.
- Protect destructive commands with confirmation and executor-side ACL.
- Validate notification click/action URLs; use Android safe browsing/intent restrictions and user confirmation for external navigation.
- Provide lock-screen privacy settings (hide message body, hide title, show only topic/priority).
- Include a clear local data deletion/export function.
- Request only required permissions; no location, camera or contacts permission for core V1.

## 9. Delivery phases and gates

Do work in one phase at a time. Each phase ends with tests, an audit report and user approval before the next phase. Do not combine phases to make apparent progress.

### Phase 0 — Reproducible baseline and full code audit

Deliver:
- Restore Gradle wrapper and verify Android Studio/JDK/Gradle/AGP/Kotlin compatibility.
- Record build, lint and current tests baseline.
- Inventory all dependencies and references.
- Capture screen map, state flows, API requests, app startup path and permission manifest.
- Confirm actual ntfy server version/configuration and whether `nfty.rplab.qzz.io` resolves and presents valid TLS.

Gate: clean baseline build or exact blocker report; no production code redesign yet.

### Phase 1 — Architecture and production data foundation

Deliver:
- Introduce packages/interfaces, repository boundaries and typed result/error models.
- Add Room, DAOs, migrations, DataStore and encrypted credential storage.
- Remove sample data from production startup; add isolated demo mode.
- Persist profiles, topics, messages, read/saved state.

Gate: restart app/process and verify all local state survives; migration tests pass.

### Phase 2 — ntfy API correctness

Deliver:
- Typed URL builder and API client for health, stream, poll/catch-up and publish.
- Authentication and permission probes; separate connected/authenticated/authorized/admin states.
- Correct event parser, cancellation, reconnect, bounded dedupe, stream diagnostics.
- MockWebServer contract tests plus a real self-hosted smoke test.

Gate: verified receive/publish/catch-up behavior against the user's server; errors correctly classified.

### Phase 3 — Notification and background delivery

Deliver:
- Android notification channels and runtime permission flow.
- Deep links, notification actions and privacy controls.
- Both owner-selected modes: Instant foreground connection and Battery Saver scheduled polling/reconciliation, with explicit switching and visible trade-offs.
- Lifecycle, Doze, process death, reboot and network transition handling.
- Optional FCM/UnifiedPush feasibility spike; do not block core release on it.

Gate: documented delivery guarantees and device test matrix; no claim of guaranteed instant background delivery unless proven. **Authorized by owner review**

Status (2026-11-09, remediated): implemented and independently re-audited — see `PHASE_3_REPORT.md`, `PHASE_3_AUDIT_REPORT.md`, `PHASE_3_REMEDIATION_REPORT.md`, `PHASE_3_FINAL_AUDIT.md`.

**Owner acceptance (2026-11-09): ACCEPTED — PASS WITH CONDITIONS.** See `PHASE_3_ACCEPTANCE.md`. The original audit FAIL and the remediation are preserved, not rewritten. Delivery modes are exactly Instant and Battery Saver (legacy `SAVED` migrates to Battery Saver). Android 15 `onTimeout` stops the service before the fatal deadline and falls back to Battery Saver; terminal stream failures stop the service with an honest notification; Instant status is service-owned and visible to the UI; cold-start deep links load by ID from Room. `testDebugUnitTest` 173 tests / 0 failures / 2 pre-existing skips, `assembleDebug` SUCCESS (19,557,690 B), `lint` 0 errors / 56 warnings.

**Acceptance conditions (open):** no device or emulator validation; Doze, reboot, OEM battery managers, notification rendering and the real Android 15 `dataSync` timeout are unverified; the real ntfy server smoke test is blocked by HTTP 502; Android Keystore round-trip is unverified on hardware. Do not claim hardware validation.

### Phase 4 — Compose, activity and command centre

**Status: Stages A–F complete (2026-11-10). Stage F gate: PASS WITH CONDITIONS.** See `PHASE_4_STAGE_F_REPORT.md`.

Deliver:
- Inbox/topic UX wired to Room, including unread counts, search and the Saved filter.
- Compose/publish screen with title, topic, priority, tags, URL/metadata attachments, view actions and server-supported scheduling.
- Durable Room-backed outbox with `QUEUED` / `SENDING` / `ACCEPTED` / `FAILED` / `AMBIGUOUS`, explicit retry controls and honest delivery labels.
- Activity timeline for receive, publish, retry, server acceptance and command request/result, with bounded retention.
- Safe typed command envelope (`rplab.command.v1`), allowlist, expiry, single-use nonce, destructive-action confirmation and a **mock** executor only.
- Multiple server profiles with clear active identity, safe switching and a URL-change preview.

Gate: full publish round trip demonstrated against MockWebServer with a correlation ID and honest state labels; a real-server round trip is blocked by the current HTTP 502 and must be recorded as a condition, never claimed; migration test passes or is recorded as device-blocked; independent QA audit.

**Stage A evidence (2026-11-09):** Room v2 adds `outbox_entries`, `activity_events` and `command_requests` with an explicit additive `MIGRATION_1_2`; exported schemas `1.json`/`2.json`; `Migration1To2Test` builds a real v1 database from `1.json` and verifies preservation plus cascade. Full suite 194 tests / 0 failures / 2 pre-existing skips; `assembleDebug` SUCCESS (19,648,407 B); `lint` 0 errors / 56 warnings. No Stage B publish client, UI, activity screen or command execution was started.

**Phase scope correction:** E-Ink belongs to Phase 5 (low-end/E-Ink). ntfy administration and the PVE command gateway belong to Phase 6. Phase 4 does not implement binary attachment upload, automatic background publish retries, real PVE execution, ntfy administration, E-Ink tuning or release signing. This section supersedes the earlier handoff wording.

### Phase 5 — E-Ink and low-resource profiles

Deliver:
- E-Ink design tokens and global mode.
- Static refresh UI, monochrome icon/status system, no animations.
- Low-memory list/paging/image policies.
- Profile-based performance measurements and Boox screenshots.

Gate: actual low-end phone and actual Boox hardware tests; no visual regressions in standard mode.

### Phase 6 — Admin, diagnostics and hardening

Deliver:
- Capability discovery and admin UI for verified server APIs.
- Clearly labelled unsupported/experimental features.
- Optional adapter for documented server-side command gateway.
- Exportable sanitized diagnostics, support bundle, connection timeline.
- Security review, permission audit, dependency cleanup, release build/signing.

Gate: threat model reviewed; admin controls cannot silently exceed the authenticated account's actual permissions.

### Phase 7 — V1 release candidate

Deliver:
- Full regression, accessibility, offline, lifecycle, migration, API and UI tests.
- Low-end phone and Boox test reports.
- APK/AAB, checksum, release notes, rollback and known-limitations document.
- Final operator guide and server-side setup guide.

Gate: no unresolved P0/P1 issues; all critical acceptance tests pass.

## 10. First Working Model (FWM) — scope

The first working model should be useful immediately and deliberately smaller than the final product.

**Included:**
- Add/edit one server profile.
- Securely save credentials.
- Test `/v1/health` and connection/auth status.
- Subscribe to one or more manually entered topics.
- Receive live JSON stream while app is foregrounded.
- Persist received messages in Room and deduplicate by message ID.
- Show local Android notifications while app is active/foreground delivery is enabled.
- Browse, search, mark read, save and delete local history.
- Publish a text message with topic/title/priority/tags.
- Manual reconnect and clear diagnostics.
- Standard theme + functional E-Ink monochrome profile.
- No sample data unless explicitly enabled.

**Deferred from FWM:**
- Always-on background instant delivery until service/push strategy is proven.
- Full user/ACL/token administration through undocumented endpoints.
- Arbitrary shell/PVE commands.
- Attachment uploads, rich actions, file handling, multi-account sync and advanced analytics.

This gives a real, testable application rather than a polished but unreliable demo.

## 11. Acceptance test checklist

### Core connection and data
- [ ] Fresh install shows no fake messages and no unexpected subscription.
- [ ] Invalid URL, DNS failure, TLS failure, 401, 403, 404 and server 5xx produce distinct useful states.
- [ ] Guest, Basic and Bearer auth paths tested against server policy.
- [ ] Stream receives a message, keepalive, reconnect and catch-up without duplicates.
- [ ] Malformed/unknown JSON does not crash the app.
- [ ] App process restart preserves config, messages, topics, saved/read state.
- [ ] Changing topics cancels old stream and opens exactly one new subscription.

### Publish and reporting
- [ ] Publish request uses correct topic, body and headers.
- [ ] HTTP accepted is not shown as handset delivered/read.
- [ ] Retry does not duplicate sends without idempotency safeguards.
- [ ] Command request/result correlation works; expired/replayed/unauthorized commands are rejected by executor.

### Android delivery
- [ ] Android 13+ notification permission denial is handled without crash.
- [ ] Channels, priority, mute and lock-screen privacy work.
- [ ] Foreground service is user-visible and can be stopped.
- [ ] Battery Saver mode respects inexact scheduling and clearly explains latency.
- [ ] Network change, airplane mode, reboot and process death are tested.

### Performance and E-Ink
- [ ] Inbox uses paging/lazy rendering and bounded caches.
- [ ] No continuous animation or auto-poll loop in E-Ink mode.
- [ ] All status meanings work in grayscale without color cues.
- [ ] Actual Boox portrait/landscape screenshots reviewed.
- [ ] Low-end device startup, memory, scroll and battery measurements recorded.

### Security and release
- [ ] No secrets in source, APK resources, logs or diagnostics.
- [ ] HTTPS enforced by default.
- [ ] No arbitrary command execution from notifications.
- [ ] Admin actions require actual server authorization and confirmation.
- [ ] Release build, signing, backup/restore of local DB and migration tested.

## 12. Metrics and definition of “smooth and flawless”

Do not use subjective claims alone. Record a baseline and target on named devices:

- Cold start to usable inbox: target < 2.5 seconds on reference low-end test device (measure; do not assume).
- UI scroll: no sustained jank on a 500-message dataset with paging.
- Memory: measure idle and 500/5,000-message browsing; no unbounded growth across reconnects.
- Reconnect: bounded exponential backoff with jitter; user-visible current state and last successful event time.
- Duplicate handling: zero duplicate rows/notifications for repeated server message IDs.
- Persistence: no lost committed messages/config after force-stop/reboot.
- Battery: compare instant mode and battery-saver mode over a controlled 8–24 hour test.
- E-Ink: no animation, color-only states or automatic high-frequency refresh.

Targets are engineering goals, not promises until measured on real hardware.

## 13. Risks and decisions requiring owner approval

1. **App identity — RESOLVED (D2′, 2026-11-08):** namespace and applicationId are `com.rplab.kaminarinfty`. Do not rename before signing; a later change creates a separate app install.
2. **Server host:** `nfty.rplab.qzz.io` DNS/TLS verified. The origin returned HTTP 502 on 2026-11-08; re-check when the host is up and pin `ntfy version` before enabling version-specific behavior.
3. **Background delivery is locked:** support both Instant foreground delivery and Battery Saver scheduled reconciliation; optional FCM/UnifiedPush may be evaluated as an additional transport. There is no free, guaranteed, zero-power always-on delivery path on modern Android.
4. **Admin scope is locked:** both ntfy administration (within verified API capabilities) and a separate PVE/homelab gateway are required; phase them after the core client.
5. **Command safety:** Phase 4 defines the typed envelope and a mock executor; the real allowlist, gateway deployment and executor trust boundary are Phase 6. No shell, SQL, PVE or arbitrary HTTP execution.
6. **E-Ink device is pending:** the Onyx Boox has not arrived and its model is unknown. Keep generic E-Ink implementation model-agnostic; record model, Android version and firmware before device-specific refresh work.
7. **Minimum Android:** validate API 24 support against dependency and target policy constraints.
8. **Server version:** pin a tested ntfy version/API capability set; do not assume undocumented admin endpoints are stable.

## 14. Phase status log (living)

| Phase | Status | Evidence |
|---|---|---|
| Phase 0 — reproducible baseline / audit | Complete | `AUDIT_REPORT.md`, `CURRENT_ARCHITECTURE.md`, `API_CAPABILITY_MATRIX.md`, `PHASE_0_EXECUTION_PLAN.md`, `PHASE_0_REPORT.md` |
| Phase 1 — architecture and data foundation | Complete, approved | `PHASE_1_REPORT.md`; Room/DataStore/Keystore, demo isolation |
| Phase 2 — ntfy API correctness | Complete, locally verified; real-server smoke test pending | `PHASE_2_REPORT.md`, `PHASE_2_GATE_REPORT.md`; commit `5d00827` |
| Phase 3 — notification and background delivery | **ACCEPTED — PASS WITH CONDITIONS** (2026-11-09) | `PHASE_3_REPORT.md`, `PHASE_3_AUDIT_REPORT.md`, `PHASE_3_REMEDIATION_REPORT.md`, `PHASE_3_FINAL_AUDIT.md`, `PHASE_3_ACCEPTANCE.md` |
| Phase 4 — Compose, activity and command centre | **Complete (PASS WITH CONDITIONS)** | `PHASE_4_ARCHITECTURE.md`, `PHASE_4_IMPLEMENTATION_PLAN.md`, `PHASE_4_STAGE_A_REPORT.md` … `PHASE_4_STAGE_F_REPORT.md` |
| Phase 5 — low-end/E-Ink | Not started | Owner decision `D-04`: generic E-Ink until the Boox arrives |
| Phase 6 — administration/security | Not started | ntfy admin + separate trusted PVE gateway |
| Phase 7 — release/QA | Not started | Signed `dist/` APK is the final gate |

**Final Phase 3 verification (2026-11-09, each task run separately with `--rerun-tasks`):**

```text
./gradlew testDebugUnitTest   # exit 0 — 173 tests, 0 failures, 0 errors, 2 pre-existing skips
./gradlew assembleDebug       # exit 0 — app-debug.apk, 19,557,690 bytes
./gradlew lint                # exit 0 — 0 errors, 56 warnings
```

The host OOMs when test/build/lint are combined (7.8 GiB RAM, swap exhausted); run them separately. No device or emulator is available, so hardware validation remains outstanding.

**Phase 3 conditions (open):** device matrix (permission grant/deny, screen-off Instant, Android 15 six-hour cutoff, terminal auth failure, Battery Saver cadence/Doze, duplicate-free 24 h, mode switching, reboot per mode, process death, network loss/recovery, cold/warm taps, OEM battery managers); real ntfy server smoke test (HTTP 502 blocker); Keystore round-trip on hardware.

**Historical note:** the original first-session instruction (run `START_PROMPT.md` read-only) was executed in Phase 0; its output is the audit set above.

## 15. Immediate next action

Owner review of `PHASE_3_ACCEPTANCE.md`, `PHASE_4_ARCHITECTURE.md` and `PHASE_4_IMPLEMENTATION_PLAN.md`. Approve, amend or reject the Phase 4 scope. Do not implement Phase 4 until explicit approval is recorded. The device matrix and the real-server smoke test remain owner/hardware-gated and are not Phase 4 code work.
