# Phase 4 Implementation Plan — Compose, Activity and Command Centre

> **For the implementing agent:** use `superpowers:executing-plans` for inline
> execution, or `superpowers:subagent-driven-development` only if the owner
> explicitly asks for per-task subagents. Steps use `- [ ]` checkboxes. Do not
> start until the owner approves this plan.

**Goal:** ship a durable publish path with an outbox, a Room-backed activity
timeline, and a typed command envelope with a mock executor, without regressing
Phase 3 delivery.

**Architecture:** extend the existing Room database to v2 with three additive
tables; add a documented ntfy JSON publish client behind a `PublishClient`
interface; put state transitions in an `OutboxRepository`; reuse the manual DI
container, Compose screens and `MainViewModel` navigation.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room 2.7, DataStore,
Coroutines/Flow, OkHttp 4.10, org.json, WorkManager (existing delivery only).

**Spec:** `PHASE_4_ARCHITECTURE.md` (design authority), `PLAN.md` §2–§7,
`AGENTS.md`, `PHASE_3_ACCEPTANCE.md`.

---

## Global Constraints

- Phase 4 only. No E-Ink profile, ntfy administration, real PVE gateway,
  binary attachment upload, FCM/UnifiedPush, notification actions or release
  signing.
- Preserve Phase 3 delivery ownership: one stream per mode; no second stream;
  no changes to `InstantForegroundService` / `BatterySaverWorker` behavior
  except the receive-activity insert inside `MessageRepository.store`.
- No destructive Room migration, no `fallbackToDestructiveMigration`, no data
  deletion. Migration v1→v2 is additive.
- No new production dependency. Test-only `androidx.room:room-testing` is
  allowed. Do not upgrade AGP/Kotlin/Room/OkHttp without a demonstrated need.
- Secrets stay in `KeystoreSecretStore`. Never put credentials, auth headers or
  message bodies in activity summaries, logs, command payloads or outbox error
  text.
- Do not claim delivery/read receipts. `ACCEPTED` means the server accepted the
  publish request.
- Every outbox transition is a conditional SQL update with expected state.
- Manual retry only. No automatic background publish retry.
- Commands are explicit UI actions, allowlisted, expiring, nonce-protected and
  executed by `MockCommandExecutor` only.
- Keep the local Gradle distribution working
  (`file:///home/pushpal/opt/gradle-9.3.1-bin.zip`).
- Run tests targeted during stages; run the full gate only at Stage F, each task
  separately (the host OOMs when test/build/lint run together).
- Commit per stage with a descriptive message. Never reset/clean.

## Review Focus

Five failure modes most likely to hurt a real user; each has a test in the
owning task:

1. **Double send after an uncertain timeout** — retry must require explicit
   duplicate-risk confirmation and must not happen automatically.
2. **“Accepted” shown as delivered/read** — UI copy and tests must keep the
   distinction.
3. **Secret/body leakage into activity, errors or command JSON** — summaries
   and errors are sanitized; tests assert absence.
4. **Command replay/expiry/confirmation bypass** — unique nonce, ±60 s issue
   window, 5 min expiry, policy-matched confirmation; tests for each rejection.
5. **Profile mix-up** — every query and send uses the entry's `profileId`;
   switching clears the UI window; tests prove isolation.

## File ownership

| Role | Owns | Does not |
|---|---|---|
| Architect / Coordinator (A) | schema, plan, integration, `PHASE_4_REPORT.md` | unapproved scope |
| Builder (B) | all stage code and tests | signing, gateway, admin |
| Security Specialist (C) | review of Stage B/E (publish + command), threat checklist | code edits |
| Independent QA (E) | Stage F audit of diff + evidence | rubber-stamping, code edits |
| Owner | gate approvals, destructive actions, release | — |

One primary implementation workflow. Do not run parallel agents on overlapping
files. Security review and final QA are separate, sequential passes.

## Stage 0 — Preconditions and schema freeze

### Task 4.0.1: Freeze the real v1 schema

**Files:**
- Modify: `app/src/main/java/com/rplab/kaminarinfty/data/local/KaminariDatabase.kt`
- Modify: `app/build.gradle.kts`
- Create: `app/schemas/com.rplab.kaminarinfty.data.local.KaminariDatabase/1.json`

**Interfaces:**
- Produces: committed v1 schema JSON used by `MigrationTestHelper`.

- [ ] Set `exportSchema = true` on `@Database` (version stays 1).
- [ ] Add `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`.
- [ ] Run `./gradlew :app:kspDebugKotlin --rerun-tasks`.
- [ ] Confirm `1.json` exists and contains the three v1 tables.
- [ ] Commit: `build: freeze Room v1 schema for migration tests`.

**Do not** change entities or version in this task. If `1.json` is not
generated, stop and report; do not hand-write a schema.

### Task 4.0.2: Add test-only Room testing

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] Add `androidx.room:room-testing` at the same version as
      `androidx.room.runtime`, `testImplementation` and
      `androidTestImplementation`.
- [ ] Add test assets:
      `sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")`.
- [ ] Run `./gradlew :app:dependencies --configuration debugUnitTestRuntimeClasspath`
      and confirm `room-testing` resolves.
- [ ] Commit with 4.0.1 if coherent, otherwise separately.

**Gate 0:** v1 schema frozen; no entity change; existing tests still green
(targeted `ExampleRobolectricTest` is enough to prove the build config).

## Stage A — Room v2 and migration

### Task 4.1: Entities

**Files:**
- Modify: `data/local/Entities.kt`
- Create: `model/OutboxEntry.kt`, `model/ActivityEvent.kt`, `model/CommandRequest.kt`

**Interfaces:**
- Produces: `OutboxEntryEntity`, `ActivityEventEntity`, `CommandRequestEntity`
  with the columns in `PHASE_4_ARCHITECTURE.md` §4.
- Produces: domain enums `OutboxState`, `ActivityType`, `ActivitySeverity`,
  `CommandStatus`.

- [ ] Write the three entities exactly as specified (FK CASCADE, indices,
      defaults). Use `Long` epoch millis; no `java.time`.
- [ ] Bump `KaminariDatabase` to `version = 2`, add the three entities, keep
      `exportSchema = true`.
- [ ] Run `./gradlew :app:kspDebugKotlin --rerun-tasks`; confirm `2.json`.
- [ ] Commit: `data: add outbox, activity and command tables (Room v2)`.

### Task 4.2: DAOs

**Files:**
- Modify: `data/local/Daos.kt`
- Create: `data/local/OutboxDao.kt`, `data/local/ActivityDao.kt`,
  `data/local/CommandDao.kt` if `Daos.kt` would exceed ~350 lines.

**Interfaces:**
- `OutboxDao`: `insert`, `observeRecent`, `getById`, `claim(id, now): Int`,
  `complete(...): Int`, `requeue(id, now): Int`, `recoverStuckSending(cutoff,
  reason): Int`, `deleteIfUnsent(id): Int`, `prune(profileId, cutoff): Int`,
  `count(profileId)`.
- `ActivityDao`: `insert`, `observeRecent(profileId, limit)`,
  `prune(profileId, cutoff, keepMax): Int`, `count(profileId)`.
- `CommandDao`: `insert`, `observeRecent`, `getById`, `markStatus(...)` with
  expected-state guard, `expireOverdue(profileId, now): Int`, `prune(...)`.
- `MessageDao`: add `search`, `observeSaved`, `unreadCounts`.
- `TopicSubscriptionDao`: add `count(profileId)`.

- [ ] Write DAO tests first (`OutboxDaoTest`, `ActivityDaoTest`,
      `CommandDaoTest`): insert/observe, conditional transition returns 1 then
      0, unique nonce rejects duplicate, prune keeps the newest N, search is
      profile-scoped.
- [ ] Run the three test classes; expect failure (DAOs absent).
- [ ] Implement the DAOs; re-run; expect pass.
- [ ] Commit: `data: add outbox/activity/command DAOs and message queries`.

### Task 4.3: Migration 1→2

**Files:**
- Modify: `KaminariDatabase.kt` (`MIGRATION_1_2`, `addMigrations`)
- Create: `app/src/test/.../data/local/Migration1To2Test.kt`
- Create (fallback): `app/src/androidTest/.../Migration1To2Test.kt`

**Interfaces:**
- Consumes: frozen `1.json`, generated `2.json`, `room-testing`.
- Produces: `KaminariDatabase.MIGRATION_1_2`.

- [ ] Test: create v1 with a profile, topic and message; run migration; assert
      the rows survive and the three new tables exist with their indices.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests
      "com.rplab.kaminarinfty.data.local.Migration1To2Test"`.
      Expected: red before the migration exists.
- [ ] Implement `MIGRATION_1_2` with SQL copied from `2.json` (table DDL and
      indices in the same order Room generates). Register it in the builder.
- [ ] Re-run; expect pass.
- [ ] If `MigrationTestHelper` cannot run under Robolectric on this host, move
      the test to `androidTest`, leave it unrun, and record that in the stage
      notes. Do not mark it passed.
- [ ] Commit: `data: add explicit Migration 1→2 with schema test`.

**Gate A:** migration test green (or honestly blocked); all existing DAO tests
green; no destructive fallback.

## Stage B — Publish client

### Task 4.4: Share auth header construction

**Files:**
- Create: `data/api/NtfyAuth.kt`
- Modify: `data/api/NftyStreamClient.kt`
- Modify/Create: `test/.../data/api/NtfyAuthTest.kt`

**Interfaces:**
- Produces: `object NtfyAuth { fun apply(builder: Request.Builder, config: ServerConfig); fun sanitizeHeaderValue(raw: String): String }`.
- Consumes: existing `AuthMethod` behavior.

- [ ] Test: guest adds no header; Basic uses `Credentials.basic`; Bearer adds
      `Bearer`; CR/LF is stripped.
- [ ] Implement by moving the private helpers; replace the stream client calls.
- [ ] Run `NtfyAuthTest`, `NtfyStreamClientTest`; expect pass with no behavior
      change.
- [ ] Commit: `net: share ntfy auth header construction`.

### Task 4.5: Publish URL and validation

**Files:**
- Modify: `data/api/NtfyEndpoint.kt`
- Create: `data/api/PublishValidator.kt`
- Create: `test/.../data/api/PublishValidatorTest.kt`

**Interfaces:**
- `NtfyEndpoint.publishJsonUrl(rawBaseUrl, requireHttps): Result<String>`.
- `PublishValidator.validate(request: PublishRequest): PublishValidation`
  where `PublishValidation` is `Valid` or `Invalid(errors: List<String>)`.

- [ ] Tests: valid request; empty/oversized body (4 KiB UTF-8); long title;
      >10 tags; invalid priority; `javascript:` click; user-info URL; >3
      actions; non-`view` action; invalid delay; JSON >32 KiB.
- [ ] Implement; re-run; pass.
- [ ] Commit: `publish: validate topic, payload, URLs and actions`.

### Task 4.6: Publish client and contract tests

**Files:**
- Create: `data/api/PublishClient.kt` (request/outcome/interface),
  `data/api/NtfyPublishClient.kt`
- Create: `test/.../data/api/NtfyPublishClientTest.kt`

**Interfaces:**
- `interface PublishClient { suspend fun publish(config, request): PublishOutcome }`.
- `NtfyPublishClient(client: OkHttpClient = publishClient())`.

- [ ] MockWebServer tests: 200 with `id`; 200 malformed body; 400; 401; 403;
      404; 413; 429; 500; `SocketTimeoutException` → `Ambiguous`;
      `ConnectException` → `Rejected(retryable=true)`; cancellation propagates;
      redirect is not followed; request body contains JSON fields and no auth
      secret in the body; `Authorization` header is correct.
- [ ] Run; red. Implement JSON publish; run; green.
- [ ] Commit: `net: add documented ntfy JSON publish client`.

**Security review C:** before Gate B, review URL validation, redirect/retry
policy, header handling, body caps and error sanitization. Record findings in
the stage notes.

**Gate B:** contract tests green; Phase 2/3 stream tests unchanged.

## Stage C — Outbox and activity

### Task 4.7: Activity repository and receive wiring

**Files:**
- Create: `data/repository/ActivityRepository.kt`
- Modify: `data/repository/MessageRepository.kt`
- Modify: `data/local/MessageMapper.kt` if a mapper is needed
- Create: `test/.../data/repository/ActivityRepositoryTest.kt`
- Modify: `test/.../data/repository/MessageRepositoryTest.kt`

**Interfaces:**
- `ActivityRepository.observe`, `.record`, `.prune` as in architecture §6.
- `MessageRepository.store(profileId, message)` now inserts the message and one
  `MESSAGE_RECEIVED` event in one transaction; returns `false` without an event
  on duplicate.

- [ ] Tests: first store writes one event; duplicate store writes none; summary
      contains the topic but not the body/title; retention keeps 5,000 and drops
      older than 90 days; profile isolation.
- [ ] Implement; re-run; pass. Confirm `InstantForegroundService`,
      `BatterySaverWorker` and `MainViewModel` call sites compile without
      signature changes.
- [ ] Commit: `activity: record receive events transactionally`.

### Task 4.8: Outbox repository and state machine

**Files:**
- Create: `data/repository/OutboxRepository.kt`
- Create: `test/.../data/repository/OutboxRepositoryTest.kt`
- Modify: `AppContainer.kt` (construct repository/client)

**Interfaces:**
- `OutboxRepository` methods and `SendOutcome` from architecture §5.4.
- Constructor:
  `RoomOutboxRepository(database, profileRepository, activityRepository, publishClient, now: () -> Long = System::currentTimeMillis)`.

- [ ] Tests:
      - enqueue persists `QUEUED` and records `PUBLISH_QUEUED`;
      - send claims `QUEUED`, calls client once, transitions `ACCEPTED` and
        records `PUBLISH_ACCEPTED` with the server ID;
      - definitive rejection → `FAILED(retryable=false)`, no retry;
      - retryable rejection → `FAILED(retryable=true)`;
      - ambiguous → `AMBIGUOUS`; retry without `allowAmbiguous` refuses;
      - retry with confirmation requeues and sends exactly once;
      - double `send` on the same row → second returns `Busy` and client is
        called once;
      - stuck `SENDING` older than 2 min → `AMBIGUOUS` on recovery;
      - cancel only allowed for unsent states;
      - prune never deletes `QUEUED`/`SENDING`;
      - a send for profile A never reads profile B's config.
- [ ] Implement; run; green.
- [ ] Commit: `outbox: add durable publish state machine`.

**Gate C:** activity + outbox tests green; one client call per send; no automatic
retry path exists.

## Stage D — ViewModels, screens and navigation

### Task 4.9: ViewModels and factories

**Files:**
- Create: `PublishViewModel.kt`, `ActivityViewModel.kt`,
  `CommandViewModel.kt` (or under `ui/` following the existing root style)
- Modify: `MainViewModel.kt` (destinations, filters, unread counts, visible
  limit, switch/profile preview)
- Modify: `AppContainer.kt`
- Modify: `MainActivity.kt` factories
- Create: `test/.../PublishViewModelTest.kt`, `ActivityViewModelTest.kt`,
  `CommandViewModelTest.kt`
- Modify: `test/.../MainViewModelTest.kt`

**Interfaces:**
- `PublishViewModel(container)` factory; state and intents from architecture §10.
- `ActivityViewModel(container)`; `CommandViewModel(container)`.
- `MainViewModel` additions: `AppDestination.COMPOSE`, `.ACTIVITY`;
  `InboxFilter.SAVED`; `unreadCounts: Map<String,Int>`;
  `visibleLimit: Int`; `loadMore()`; `switchProfile(id)`;
  `prepareProfileChange(newUrl)`.

- [ ] ViewModel tests first: form validation; double-tap send calls one send;
      retry ambiguous requires confirmation; timeline filter; command
      destructive confirmation required; profile switch clears the window and
      reloads the new profile; URL preview returns correct counts.
- [ ] Implement; run targeted; green.
- [ ] Commit: `ui: add publish/activity/command view models`.

### Task 4.10: Screens and navigation

**Files:**
- Create: `ui/screens/PublishScreen.kt`, `ui/screens/ActivityScreen.kt`,
  `ui/screens/CommandPanel.kt`
- Modify: `ui/screens/InboxScreen.kt`, `TopicsScreen.kt`, `SettingsScreen.kt`,
  `MainActivity.kt`, `ui/navigation/NftyBottomNavigationBar.kt`
- Create: `test/.../ui/screens/PublishScreenTest.kt`, `ActivityScreenTest.kt`,
  `CommandPanelTest.kt`

- [ ] Compose tests first: publish empty/loading/offline/accepted/failed/
      ambiguous; mock banner visible; command confirmation gate; timeline empty;
      accessibility labels for icon-only controls; no color-only status.
- [ ] Implement the five-destination bar (`Inbox, Topics, Compose, Activity,
      Settings`), Saved filter, profile chip, Compose form + outbox list,
      Activity tabs, CommandPanel.
- [ ] Run targeted Compose tests; green.
- [ ] Commit: `ui: wire publish, activity and mock command screens`.

### Task 4.11: Profile switching and URL-change preview

**Files:**
- Modify: `MainViewModel.kt`, `SettingsScreen.kt`,
  `data/repository/ProfileRepository.kt`
- Modify: `MainViewModelTest.kt`, `ProfileRepositoryTest.kt`

- [ ] Tests: switch stops Instant, cancels Battery Saver, clears the window,
      loads the new profile, re-applies the mode, never deletes old data; URL
      change preview counts are profile-scoped; confirmation is required before
      an in-place URL change.
- [ ] Implement; run; green.
- [ ] Commit: `profiles: safe switching and URL-change preview`.

**Gate D:** all Compose/VM tests green; every new button has an action; no
sample data; Saved still reachable.

## Stage E — Command envelope and mock executor

### Task 4.12: Envelope, policy and mock executor

**Files:**
- Create: `commands/CommandEnvelope.kt`, `commands/CommandPolicy.kt`,
  `commands/MockCommandExecutor.kt`
- Create: `test/.../commands/CommandPolicyTest.kt`,
  `test/.../commands/MockCommandExecutorTest.kt`

**Interfaces:**
- `CommandEnvelope` fields per architecture §7.1; `toJson()`/`fromJson()` using
  `org.json`.
- `CommandPolicy.validate(envelope, nowMillis, expectedTarget): CommandValidation`.
- `interface CommandExecutor { suspend fun execute(envelope): CommandExecutionResult }`.
- `MockCommandExecutor(now: () -> Long = System::currentTimeMillis)`.

- [ ] Tests: valid envelope; unknown schema/action/arg; wrong arg type;
      non-allowlisted target; issued in the future/past beyond 60 s; expiry
      >5 min; expired; `requires_confirmation` mismatch; missing confirmation
      for `service.restart`; duplicate nonce; JSON round-trip; mock result
      deterministic and marked `mock: true`; no body/token in result.
- [ ] Implement; run; green.
- [ ] Commit: `commands: add v1 envelope, policy and mock executor`.

### Task 4.13: Command repository and panel wiring

**Files:**
- Create: `data/repository/CommandRepository.kt`
- Modify: `CommandViewModel.kt`, `ActivityScreen.kt`, `CommandPanel.kt`
- Modify: `AppContainer.kt`
- Create: `test/.../data/repository/CommandRepositoryTest.kt`

- [ ] Tests: submit valid → `COMPLETED` + result activity; expired → `EXPIRED`;
      duplicate nonce → rejected; destructive without confirmation → rejected;
      submit never calls a real network/client (fake executor counter).
- [ ] Implement; run; green.
- [ ] Commit: `commands: persist, execute (mock) and correlate results`.

**Security review C:** command injection/replay/expiry/confirmation, no shell or
network, no credentials, mock labeling. Record findings.

**Gate E:** command tests green; UI cannot submit without confirmation when
required; no real executor exists.

## Stage F — Full gate and audit

### Task 4.14: Verification and Phase 4 report

**Files:**
- Create: `PHASE_4_REPORT.md`
- Update: `PROGRESS.md`, `DECISIONS.md`, `RISKS.md`, `TODO.md`, `CHANGELOG.md`

- [ ] Run separately, each with `--rerun-tasks`:
      ```text
      ./gradlew testDebugUnitTest
      ./gradlew assembleDebug
      ./gradlew lint
      ```
      Record exact exits, test counts, APK bytes and lint counts. Do not claim
      a pass that was not run.
- [ ] Run the migration test explicitly if it is in `androidTest` and a device
      exists; otherwise record it as unrun.
- [ ] Inspect `git diff` and `git status`; no unrelated files.
- [ ] Security review C and independent QA E audit the diff and evidence.
- [ ] Write `PHASE_4_REPORT.md` with the AGENTS.md §13 template; recommendation
      PASS / PASS WITH CONDITIONS / BLOCKED.
- [ ] Commit: `phase-4: verification report and tracking update`.
- [ ] **STOP.** Do not start Phase 5.

## Dependencies and order

```text
0 schema freeze ─► A entities/DAOs/migration ─► C outbox/activity
                         │                         │
                         └──► B publish client ────┘
                                                   ▼
                                      D view models/screens/nav
                                                   ▼
                                      E commands/mock executor
                                                   ▼
                                      F gate + independent audit
```

B can start after 0/A because it does not touch the database. C depends on A and
B. D depends on A–C. E depends on 4.7/4.8 (activity + Room). F depends on all.

## Rollback

- Each stage is its own commit; roll back a stage with `git revert <hash>`.
- The Room v2 migration is additive and tested. A **downgrade** to a v1 build
  cannot open a v2 database (Room refuses; destructive fallback is disabled).
  Never downgrade on a user device. Before release, v2 must be final; if a
  schema defect is found, add v3 with a forward migration, never delete the DB.
- Debug installs may be uninstalled to reset local data **only with owner
  approval**; that deletes messages/outbox/activity and is not a rollback path.
- No server-side changes exist in Phase 4, so server rollback is not applicable.

## Complexity (relative, not calendar promises)

| Stage | Size | Main risk |
|---|---|---|
| 0 / A | M | Migration/schema identity mismatch |
| B | S/M | Error classification and no hidden retry |
| C | M | Race-safe transitions, ambiguous outcomes |
| D | L | UI scope, profile switching, accessibility |
| E | M | Envelope validation and replay protection |
| F | S/M | Host memory, honest evidence |

## Approval required before starting

Reply with:

`Approved: Phase 4 scope, stages A–F, JSON publish, view-only actions, manual retry, mock-only command centre, profile change preview.`

Any subset may be overridden explicitly. Until then, no Phase 4 code is written.
