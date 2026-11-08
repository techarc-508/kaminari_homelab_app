# PHASE_0_EXECUTION_PLAN.md — ordered task breakdown (Phase 0 + Phase 1)

Parent contract: `AGENTS.md`; product ladder: `PLAN.md` §9 (phases 0–7), FWM scope: `PLAN.md` §10. Evidence: `AUDIT_REPORT.md`, `CURRENT_ARCHITECTURE.md`, `API_CAPABILITY_MATRIX.md`.
**This document implements nothing yet.** Every task below needs the owner approvals listed in `AUDIT_REPORT.md §8 (D1–D6)` before it is run. Admission condition to start work: owner replies with explicit approval.

---

## 1. Phase 0 — Reproducible baseline (matches PLAN.md §9 Phase 0)

**Goal:** make `./gradlew :app:assembleDebug` reproducible on the Mint VM, record the true build/test/lint/dependency baseline, verify server version — and change nothing else.
**Rule:** if a task would touch app source/design, STOP and ask one focused question (AGENTS §2).

| Task | Owner (agent) | Work product | Deps |
|---|---|---|---|
| **T0.0 Approvals** | Owner | ACK of D1–D6 (toolchain install, applicationId kept, server-version probe, git init, sample-data demotion, deferrals) | – |
| **T0.1 Toolchain** | VM shell (owner-approved) | JDK 21 + SDK cmdline-tools + platform 36 + build-tools 36 + `local.properties` (all non-repo) | T0.0 |
| **T0.2 Wrapper restore** | VM shell | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` via `gradle wrapper --gradle-version 9.3.1`; record sha256 of the jar | T0.1 |
| **T0.3 Git init** | VM shell | `git init && git add -A && git commit -m "audit baseline" && git tag audit-baseline` (owner-approved D4; ZIP kept as pristine fallback) | T0.2 |
| **T0.4 Debug keystore** | VM shell | `debug.keystore` at repo root (build-command in AUDIT_REPORT §2 B-4; password `android`, alias `androiddebugkey`, matching `build.gradle.kts:35-40`) | T0.3 |
| **T0.5 Baseline runs** | C/D | Record in `PHASE_0_REPORT.md`: exact command + exit code + key output for `./gradlew :app:assembleDebug`, `testDebugUnitTest`, `lintDebug`, `:app:dependencies` (per AGENTS §4) | T0.4 |
| **T0.6 Compat-repair loop** | C, QA/E | ONLY if T0.5 fails: minimal edits to `gradle/libs.versions.toml`, root/`app/build.gradle.kts`, `gradle.properties` (per-item written justification, owner approved) — **whitelist ends there**; no Kotlin source | T0.5 |
| **T0.7 Server probe** | Owner (D3 SSH) | `ntfy version` output; `curl -sS https://nfty.rplab.qzz.io/v1/health` (documented open endpoint, no creds, no writes); record version/TLS in `API_CAPABILITY_MATRIX.md §7` | T0.0 |
| **T0.8 Report + gates** | A | `PHASE_0_REPORT.md` per AGENTS §13 template; explicit PASS / PASS WITH CONDITIONS / BLOCKED + next-phase approval request | T0.5–T0.7 |

**Phase 0 acceptance gate:** `assembleDebug` produces an installable debug APK **or** an exact blocker report; baseline values recorded; no production redesign. Rollback: `git reset --hard audit-baseline` (or re-extract the pristine ZIP).

## 2. Phase 1 — Architecture and production data foundation (matches PLAN.md §9 Phase 1)

**Goal:** durable state that survives process death; sample data decanted out of production; boundaries for later phases. **Out of scope:** publish, admin, background service, E-Ink (Phases 2–5).

| Task | Owner (agent) | Content | Files touched (whitelist) | Deps |
|---|---|---|---|---|
| **T1.1 Boundaries** | D/C | Repository interfaces + typed `Result`/error models; ViewModels keep constructor injection; `MainUiState` feed now sourced from persistence | `MainViewModel.kt`, new `domain/` + `data/repository/` files; Setup saves go through repository, not straight to Flow | T0.8 |
| **T1.2 Room schema v1** | D | `ServerProfileEntity`, `TopicEntity`, `MessageEntity`, DAOs, migration pattern + `MigrationTest`; indexes on `(profile_id, message_id)` unique | `data/storage/` new; `app/build.gradle.kts` only to enable Room KSP | T1.1 |
| **T1.3 Config + secrets** | D | DataStore (non-secret prefs incl. theme) + Android-Keystore-backed secret wrapper for `tokenOrPassword` (library choice documented); **fix `allowBackup`/`data_extraction_rules` to exclude secrets BEFORE anything lands** | `data/storage/*`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `AndroidManifest.xml` | T1.1 |
| **T1.4 Sample-data demotion** | B | `isSampleData` honored/truthful everywhere; sample content behind explicit opt-in toggle in Settings; default first-run = empty states + Setup screen first-run flow | `MainViewModel.kt`, `SampleDataProvider.kt`, `InboxScreen.kt`, `SettingsScreen.kt`; test files that depended on contamination updated **with written reason** (AGENTS §11) | T0.0-D5 |
| **T1.5 VM ↔ Room/DataStore wiring** | D/C | Startup hydration from Room; read/saved/delete/clear-hit DB; messages flow from `MessageDao` to UI | `MainViewModel.kt`, `data/repository/*` | T1.2, T1.3 |
| **T1.6 Dead UI cleanup (P1 subset, Phase-1 file white-list)** | B | Remove or truthfully wire: fake telemetry cards (`InboxScreen.kt:125-148`), fake retention/diagnostics (`SettingsScreen.kt:311-321,369-377`), notification switches → disabled-with-reason until Phase 3 | screens listed | T1.4 |
| **T1.7 Retry/throttle corrections** | C | **Deviation request R-1 to owner:** these bugs corrupt Phase-1 process-death tests, so they land early: 401/403 stop-flap + honor fatal errors (`NftyRepository.kt:89-118`), separate health/stream timeouts (`NftyStreamClient.kt:39,64`), Setup keystroke debounce (one restart per save, not per char) | `NftyRepository.kt`, `NftyStreamClient.kt`, `SetupScreen.kt`, `MainViewModel.kt` | T0.8 |
| **T1.8 Tests** | D/E + B | VM tests with fake repos (turbine + coroutines-test added), DAO+migration tests, Robolectric "kill-and-restart survives" test, Compose tests for Inbox empty/error states | `app/src/test+androidTest`, `libs.versions.toml` (test deps only) | T1.5 |
| **T1.9 Phase 1 report** | A/E | `PHASE_1_REPORT.md` per AGENTS §13; QA/E independent verification; owner approval gate | – | all |

**Phase 1 acceptance gate (verbatim from PLAN.md):** restart app/process and verify all local state survives; migration tests pass; sample data invisible unless explicitly enabled.
**Rollback:** small phase-scoped commits; `git revert` per commit; Room DB created fresh (no destructive migration possible on v1 schema).

## 3. Fractions deliberately NOT in Phase 0/1

Publish (`PLAN.md` Phase-2), notifications/background modes (Phase 3), command centre/PVE mock executor (Phase 4), E-Ink tokens (Phase 5), admin/diagnostics/release signing (Phase 6), RC (Phase 7). Deviation R-1 is the only cross-phase pull-in, because it is a correctness hazard to Phase 1's own tests.

## 4. Agent/file-ownership matrix (from plan on; AGENTS §3)

| Agent | Owns | May not touch |
|---|---|---|
| A (coordinator) | plan/status docs, phase gates, integration review | implementation files |
| B (Android/UI) | `ui/**`, `MainActivity.kt`, strings/a11y, E-Ink profile (Ph 3) | `data/**` |
| C (ntfy/network) | `data/api/**`, `data/repository/**` network parts, URL builders | `ui/**`, storage |
| D (storage/reliability) | `data/storage/**`, Room/DataStore/Keystore, outbox | Compose screens |
| E (QA/security) | tests plan, security review, phase-report verification, veto P0/P1 | implementation except test dirs |

## 5. Risks specific to Phase 0/1

1. AGP 9.1.1 + BOM 2024.09.00 mismatch discovered at T0.5 → fix path listed in T0.6 (Compose BOM bump is the most likely single-line fix).
2. New AGP DSL `compileSdk { version = release(36) { minorApiLevel = 1 } }` needs latest platform installed (T0.1 list covers platform-tools + platforms only — add `android-36.1` if reported missing).
3. Clean build needs network: documents an exception if offline.
4. Robolectric downloads android-all jars during T0.5 (~100 MB) — expected slowness, not a failure.

## 6. Decision points locked for later phases (carried from owner)

Instant + Battery Saver both delivered (Phase 3); FCM for custom-server push NOT planned (`API_CAPABILITY_MATRIX.md §5`); E-Ink = full profile, not grayscale (Phase 5); Boox vendor specifics deferred until the device arrives; PVE gateway = separate trusted component (Phase 4 mock executor first, integration contract documented; APK never embeds PVE credentials or shell execution).
