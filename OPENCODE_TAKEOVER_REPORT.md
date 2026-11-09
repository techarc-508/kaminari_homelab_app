# OpenCode Takeover Report — Kaminari Nfty V1

**Date:** 2026-11-09
**Reviewer:** OpenCode (takeover intake, read-only)
**Repository:** `/home/pushpal/Desktop/Kaminari APP/KaminariNfty_V1_Plan/repo/kaminari_homelab_app-main`
**Scope:** Verify the Codebuff/Freebuff handoff against the actual repository. No application code, Gradle files, manifest, resources or history were modified. The only change is this report.

---

## 1. Repository and Git state

| Item | Verified value |
|---|---|
| Branch | `main` |
| HEAD | `2fe0ce6` — *Add OpenCode handoff for the Phase 3 checkpoint* (adds only `OPENCODE_HANDOFF.md`, 165 lines) |
| Phase 3 checkpoint | `72dbf36` — *Phase 3: notification and background delivery (remediated)*, 44 files, `+3225/−43`, no deletions |
| Prior checkpoints | `5d00827` (Phase 2 remediation), `e6cdba5` (Phase 1+2), `f254cd6` (Phase 0/QA conditions) |
| Working tree | Clean (`git status --porcelain -uall` → 0 lines) |
| Tags | `audit-baseline` (no Phase 3 tag) |
| Remote | None configured (local repository) |
| Ignored/untracked | `local.properties`, `debug.keystore`, `*.env`, build output — all ignored; no secrets tracked |

**Build configuration (`app/build.gradle.kts`, `gradle/libs.versions.toml`):**

| Item | Value |
|---|---|
| applicationId / namespace | `com.rplab.kaminarinfty` |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36.1 |
| versionCode / versionName | 1 / `1.0` |
| Gradle / JDK | 9.3.1 wrapper / OpenJDK 21.0.12.1 |
| AGP / Kotlin / KSP | 9.1.1 / 2.2.10 / KSP plugin |
| Room / OkHttp / WorkManager | 2.7.0 / 4.10.0 / 2.10.1 |
| Debug signing | `debug.keystore`, alias `androiddebugkey` |
| Release signing | `my-upload-key.jks` or `KEYSTORE_PATH`, with `STORE_PASSWORD`/`KEY_PASSWORD` env — **keystore and env absent; release signing not currently reproducible** |
| Device tooling | `adb` and `emulator` **not installed**; no device/emulator available |

The handoff's Git claims are accurate: checkpoint present, tree clean, no history rewriting, handoff is the only post-checkpoint file.

---

## 2. Handoff claim verification

| # | Handoff claim | Evidence | Verdict |
|---|---|---|---|
| 1 | Phase 3 checkpoint `72dbf36` exists; 44 files, +3225/−43; no deletions | `git show --stat 72dbf36`; `git show --diff-filter=D` → empty | ✅ Matches |
| 2 | Tree clean; handoff is the only added file | `git status`; `git show 2fe0ce6 --stat` | ✅ Matches |
| 3 | A-01 fixed: Android 15 `dataSync` timeout stops before the fatal deadline | `InstantForegroundService.kt:114–137` (`onTimeout`, `TIMED_OUT`, `stopSelf`); `InstantForegroundServiceTest.kt:19–33` asserts terminal status + self-stop | ✅ Fixed and tested |
| 4 | A-02 fixed: dead stream cannot keep a "live" notification | `NftyRepository.kt:85–86,113–122,186–190` (`isTerminal`); service observation `InstantForegroundService.kt:203–228`; `NftyRepositoryTest.kt:62–84` | ✅ Fixed; service-level branch verified by inspection, not full integration test (documented) |
| 5 | A-03 fixed: Instant status is service-owned; UI opens no second stream | `AppContainer.kt:34` (`DeliveryStatusStore`); `MainViewModelTest.kt:543–565` asserts `stream.startCalls.size == 0` | ✅ Fixed and tested |
| 6 | A-04 fixed: cold-start deep links resolve from Room | `Daos.kt:81` `getById`; `MainViewModel.kt:600–616`; `MainViewModelTest.kt:522–539`; `MessageDaoTest.kt:188–196` | ✅ Fixed and tested |
| 7 | Clock regression test is meaningful (not the old vacuous assertion) | `NiftyTopBarClockTest.kt:34–69` renders the real composable with an injected clock; asserts initial text, no sub-second change, next-second update, and composition idle | ✅ Meaningful |
| 8 | Legacy `SAVED` → `BATTERY_SAVER` migration is correct; fresh default Battery Saver | `DeliveryMode.kt:48–53`; `DeliveryModeTest.kt:17–27`; `AppPreferences.kt:56` | ✅ Correct, one-way, no data deletion |
| 9 | 173 tests, 0 failures, 0 errors, 2 skipped (pre-existing SecretStore KeyStore cases) | Independent `testDebugUnitTest --rerun-tasks`: **173 / 0 / 0 / 2**; skipped = `SecretStoreTest` Keystore round-trip cases | ✅ Exact match |
| 10 | `assembleDebug --rerun-tasks` succeeds; APK 19,557,690 bytes | Independent run: exit 0, 57 s; `app-debug.apk` = **19,557,690 bytes** | ✅ Exact match |
| 11 | `lint --rerun-tasks`: 0 errors, 56 warnings | Independent run: exit 0, 1 m 17 s; XML severity counts: **56 Warning, 0 Error** | ✅ Exact match |
| 12 | No device/hardware validation; `adb` not installed | `command -v adb` → absent; no emulator | ✅ Matches; device matrix remains outstanding |
| 13 | No secrets in the diff/tree | Pattern scan of source/build/config/docs returned no credential literals; secret values only via Keystore; `allowBackup=false` | ✅ Matches |
| 14 | Phase 4 not started; publish/admin/E-Ink absent | No publish client, outbox, activity timeline, command centre, E-Ink profile or admin code in the tree | ✅ Matches |

**Phase 3 audit verdict reproduced from the repository:** `PHASE_3_FINAL_AUDIT.md` → **PASS WITH CONDITIONS**. All four blocking findings A-01…A-04 are closed with regression coverage; A-05…A-10 are closed or documented; A-11…A-14 are accepted residual risks. Conditions are external: the physical-device matrix and a full service terminal-branch integration test.

---

## 3. Outstanding risks (from `RISKS.md`, unchanged)

| ID | Risk | Severity | State |
|---|---|---|---|
| R-01 | No real-device validation (Doze, reboot, Android 15 timeout, OEM killers) | High | Open — do not claim hardware verification |
| R-02 | Android 15+ caps `dataSync` at 6 h/24 h | High | Mitigated by timeout fallback; not eliminated |
| R-03 | Terminal auth/permission failure stops delivery | High | Mitigated with honest status; no auto-recovery |
| R-04 | OEM battery managers may kill WorkManager/service | High | Open, documented |
| R-15 | Keystore round-trip unverified on hardware | High | Unit fallback covered; device check pending |
| R-05, R-09, R-10 | Inexact Battery Saver timing; no notification actions; no FCM fallback | Medium | Accepted/deferred |
| R-06, R-07, R-08, R-11, R-12, R-13, R-14 | Hash collisions, denied-notification visibility, boot scope, missing service integration test, moot backoff, E-Ink unknown, real server unverified | Low/Medium | Accepted/documented |

No **new** Critical or High code defect was found during this intake.

---

## 4. Independent verification evidence

Commands run from the repository root, separately, with forced execution:

```text
./gradlew --version
# Gradle 9.3.1; Launcher JVM 21.0.12.1

./gradlew testDebugUnitTest --rerun-tasks
# BUILD SUCCESSFUL in 1m 27s; 32 tasks executed
# JUnit XML totals: 173 tests, 0 failures, 0 errors, 2 skipped
# Skipped: SecretStoreTest :: unknown keys return null
#          SecretStoreTest :: roundtrip through keystore-backed encryption

./gradlew assembleDebug --rerun-tasks
# BUILD SUCCESSFUL in 57s; 38 tasks executed
# app/build/outputs/apk/debug/app-debug.apk = 19,557,690 bytes

./gradlew lint --rerun-tasks
# BUILD SUCCESSFUL in 1m 17s
# lint-results-debug.xml: 56 Warning, 0 Error
```

**Environment note (not an app defect):** the first attempt to run the three gates in one shell session ended with `Gradle build daemon disappeared unexpectedly` during the test task. The VM has 7.8 GiB RAM and its 3.8 GiB swap was fully exhausted; `-Xmx4g` plus Robolectric is at the memory limit. After `./gradlew --stop` and freeing memory, the same test task passed. This reproduces the limitation already documented in `PHASE_2_REPORT.md` §8: run gates separately. It does not invalidate the app evidence.

---

## 5. Discrepancies between the handoff and the repository

None invalidates the Phase 3 checkpoint. The following are documentation/hygiene issues to resolve deliberately, not silently.

| ID | Severity | Finding | Recommendation |
|---|---|---|---|
| D-1 | Low | **`PLAN.md` exists in two divergent copies.** The plan-folder copy contains §14 "Phase status log (living)" and verification commands; the repo-root copy removed that section, renumbered §15→§14, and contains the C3 event-list amendment and Phase 3 status paragraph that the plan-folder copy lacks. | Choose one canonical copy (recommend the repo root for code-coupled docs, the plan folder for phase reports) and sync both at the Phase 4 documentation gate. Do not rewrite history. |
| D-2 | Low | **Phase 1/2 reports exist only in the plan folder**, not the repository root: `PHASE_1_REPORT.md`, `PHASE_2_REPORT.md`, `PHASE_2_GATE_REPORT.md` are at `KaminariNfty_V1_Plan/`, while the repo root carries Phase 0/3 reports. A repo-only handoff lacks Phase 1/2 evidence. | Copy (do not move) the three reports into the repo root at the next documentation update. |
| D-3 | Low | **`PHASE_1_REPORT.md` claims "lint 0 warnings"; current `lint` reports 56 warnings.** `PHASE_2_REPORT.md` already flags the Phase 1 claim as not reproducible. | Historical report; append a correction note in the Phase 4 docs; do not deny the warning count. |
| D-4 | Low | **`NftyRepository.kt:187` has two `when` branches on one physical line** (`FORBIDDEN -> … PERMISSION_DENIED                            else -> …`), introduced by `72dbf36`. It compiles (assemble is green) and the `else` entry is parsed correctly because the `when` is an exhaustive expression; behavior is intact. It is a source-hygiene defect and makes the terminal-error branch read as if malformed. | Normalize formatting in a later phase with no behavior change; add a targeted `FORBIDDEN → PERMISSION_DENIED` assertion (currently only terminal `AUTH` is tested at `NftyRepositoryTest.kt:67–84`). |
| D-5 | Low | **Handoff §8 conflates phase scope:** it lists the dedicated E-Ink profile and ntfy/PVE administration under "Phase 4 scope", while `PLAN.md` assigns E-Ink to Phase 5 and admin/PVE diagnostics to Phase 6. | Treat `PLAN.md` as authoritative: Phase 4 = publish/outbox, activity timeline, command envelope + mock executor, Room-backed inbox/topic UX. |
| D-6 | Medium (release) | **Release signing is not reproducible:** `signingConfigs.release` needs `my-upload-key.jks`/`KEYSTORE_PATH` and `STORE_PASSWORD`/`KEY_PASSWORD`; neither is present. `versionName` is `1.0`/`versionCode 1`, not the final `v1.0.0` target. No `dist/` artifacts exist (expected at this phase). | Owner provides/approves the release key in Phase 7; do not fabricate signing evidence. Bump version at release. |

**No mismatch was found in:** the Phase 3 checkpoint hash, file/line counts, A-01…A-04 closures, the clock test, the Saved migration, the gate results, the APK size, the lint counts, or the "no device verification" disclosure.

---

## 6. Recommendation

1. **Accept Phase 3 as PASS WITH CONDITIONS.** The checkpoint is sound, all blocking audit findings are fixed with meaningful tests, and the three automated gates reproduce exactly.
2. **Keep the conditions open and visible:** device matrix (Android 13/14/15/16, Doze, reboot, 6-hour cutoff, OEM managers), full service terminal-branch integration test, Keystore verification on hardware, and owner sign-off on the two-mode UI + `SAVED` → `BATTERY_SAVER` migration.
3. **Do not start Phase 4 yet.** The owner has not authorized it. No smallest remaining code remediation is required for Phase 3; the only missing acceptance evidence is device/hardware validation, which this host cannot produce.
4. **Phase 4 is proposed below for owner review only.** Nothing in it has been implemented.

---

## 7. Proposed Phase 4 plan (for owner review — not authorised, not started)

**Objective (per `PLAN.md` §Phase 4):** a complete local round trip — compose/publish with an outbox, an honest activity timeline, a safe command-envelope contract exercised against a **mock** executor, and inbox/topic UX wired to Room.

**Explicitly out of scope:** the full E-Ink profile (Phase 5); real PVE gateway and ntfy administration (Phase 6); FCM/UnifiedPush; notification actions unless separately approved; release signing (Phase 7).

### Workstreams and ownership

| Task | Owner | Deliverable | Depends on |
|---|---|---|---|
| T4.1 Publish contract | C (ntfy/networking) + E review | Typed publish DTO/client for documented `POST /{topic}`: title, message, priority, tags, click, delay, attachment reference; auth from `SecretStore`; typed error mapping; MockWebServer contract tests | — |
| T4.2 Outbox | D (storage/reliability) | Room `OutboxEntity` + DAO + repository: `QUEUED → SENDING → ACCEPTED / FAILED`, attempts, bounded retry, correlation ID, honest "accepted by server ≠ delivered"; explicit v1→v2 migration + migration test; no destructive fallback | T4.1 |
| T4.3 Activity timeline | D + B (UI) | Room-backed events: received, queued, sent/accepted, failed, command submitted/result; read-only timeline screen; no synthetic telemetry | T4.2 |
| T4.4 Publish UI | B | Compose compose/publish screen (title, message, priority, tags, click), validation, queue/send/retry/cancel, states for offline/error; accessibility labels; no fake success | T4.2 |
| T4.5 Command envelope v1 + mock executor | A (architecture) + D; E reviews security | Versioned JSON schema with fixed allowlisted action IDs, target validation, `issuedAt`/`expiresAt`, nonce + replay rejection, explicit confirmation for destructive actions, correlated result; mock executor only; `COMMAND_PROTOCOL.md`; no shell/URL execution, no credentials | — |
| T4.6 Inbox/topic UX to Room | B + D | Room-backed paging (stable keys), topic management, search/filter/saved/read states; remove any remaining sample/demo paths from production views (demo mode stays isolated) | — |
| T4.7 Tests | E (independent) | Publish contract tests, outbox state machine, envelope validation/expiry/replay, migration test, ViewModel and Compose UI tests; no real network in unit tests | T4.1–T4.6 |
| T4.8 Phase gate | A + E | Run build/test/lint **separately** with `--rerun-tasks`; write `PHASE_4_REPORT.md`; update tracking docs; no device claims | all |

### Honest constraints to design around

- **Publish idempotency:** ntfy documents no idempotency key. A retry after an ambiguous transport failure can duplicate a message. The outbox must distinguish "accepted by server" from "not sent", keep ambiguous failures visible, and avoid automatic retry in the ambiguous case unless the owner accepts duplicate risk.
- **Command execution:** no trusted gateway exists yet; Phase 4 therefore ships the envelope, validation and a **mock executor** with clearly labelled mock results. Real PVE/gateway work remains Phase 6 and needs a separate deployment approval.
- **Attachments:** the owner goal mentions supported attachments. Phase 4 should start with URL/metadata attachments; binary `PUT` upload is a scope decision (below).
- **Server:** the ntfy origin previously returned HTTP 502. Publish contract tests use MockWebServer; a real-server smoke test remains a condition, not a claim.

### Gate and rollback

- **Gate:** full local round trip demonstrated with a correlation ID and honest state labels; `testDebugUnitTest`, `assembleDebug`, `lint` each green; migration test passes; no secret or destructive change.
- **Rollback:** one phase-scoped commit; explicit Room migration means reverting code leaves data readable; no `reset --hard`, `clean`, or history rewrite.

---

## 8. Exact owner decisions required

1. **Accept Phase 3** as PASS WITH CONDITIONS (or specify the smallest additional remediation you require).
2. **Authorise Phase 4** with the scope in §7 (or trim it).
3. **Attachments:** URL/metadata only for Phase 4, or include binary upload?
4. **Command centre:** confirm mock-executor-only in Phase 4; real gateway remains Phase 6 with separate deployment approval.
5. **Two-mode sign-off:** confirm the Instant/Battery Saver UI and the legacy `SAVED` → `BATTERY_SAVER` migration are accepted.

**STOP.** No Phase 4 work has begun. This report is the only file added; the working tree otherwise remains at `2fe0ce6`.
