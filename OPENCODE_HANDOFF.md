# OpenCode Handoff — Kaminari Nfty V1

**Date:** 2026-11-09
**Handoff from:** Codebuff (Phase 3 remediation + audit)
**Repository:** `KaminariNfty_V1_Plan/repo/kaminari_homelab_app-main`
**Plan folder:** `KaminariNfty_V1_Plan`
**Branch:** `main`
**Phase 3 checkpoint:** `72dbf36` — *Phase 3: notification and background delivery (remediated)*
**Previous commits:** `5d00827` (Phase 2 remediation), `e6cdba5` (Phase 1+2 checkpoint),
`f254cd6` (Phase 0/QA conditions)

---

## 1. Project identity and architecture

Kaminari Nfty is a low-resource native Android client for a self-hosted ntfy server, intended for
the owner's phone and eventually an Onyx Boox E-Ink device. Package/application ID:
`com.rplab.kaminarinfty`; minSdk 24, targetSdk/compileSdk 36; Kotlin 2.2.10, AGP 9.1.1, JDK 21.

Architecture:

- **Compose UI** (`ui/screens`, `ui/components`) with a ViewModel-owned state flow.
- **MainViewModel** (`MainViewModel.kt`) coordinates setup, profiles, inbox, live stream, modes,
  deep links and navigation.
- **Repositories:** `NftyRepository`/`StreamRepository` (ntfy HTTP/SSE, reconnect, catch-up),
  `RoomMessageRepository` (Room source of truth), `DefaultProfileRepository` (profiles/topics),
  `SecretStore` (Android Keystore).
- **Persistence:** Room (`data/local`, explicit schemas, no destructive migration) and DataStore
  (`data/preferences`) for non-secret preferences. Room is the source of truth for history.
- **Delivery:** `delivery/` owns notifications and background delivery. Instant uses a `dataSync`
  foreground service; Battery Saver uses unique 15-minute WorkManager catch-up.
- **Manual DI:** `AppContainer` per process (`KaminariApp`), including the process-wide
  `DeliveryStatusStore` and `appScope`.
- **Tests:** JVM + Robolectric + MockWebServer; no real network in unit tests.

## 2. Completed phases

| Phase | State | Evidence |
|---|---|---|
| 0 | Complete | `AUDIT_REPORT.md`, `CURRENT_ARCHITECTURE.md`, `API_CAPABILITY_MATRIX.md`, `PHASE_0_REPORT.md` |
| 1 | Complete, approved | `PHASE_1_REPORT.md`; architecture/Room/DataStore/Keystore |
| 2 | Complete locally; real-server smoke test still valuable | `PHASE_2_REPORT.md`, `PHASE_2_GATE_REPORT.md`, commit `5d00827` |
| 3 | Remediated, checkpointed; **PASS WITH CONDITIONS** (device matrix outstanding) | `PHASE_3_REPORT.md`, `PHASE_3_AUDIT_REPORT.md`, `PHASE_3_REMEDIATION_REPORT.md`, `PHASE_3_FINAL_AUDIT.md`, commit `72dbf36` |
| 4 | **Not started, not authorised** | — |

## 3. Phase 3 summary

- **Delivery modes:** exactly **Instant** and **Battery Saver**. The former third "Saved only"
  selection was removed; persisted `SAVED` migrates one-way to `BATTERY_SAVER`. Fresh installs
  default to `BATTERY_SAVER`. Saved *messages* and the Saved tab are unchanged.
- **Instant:** foreground `dataSync` service owns the SSE stream, performs a bounded catch-up, then
  subscribes; persists to Room and posts notifications only for newly inserted rows.
- **Android 15+ timeout:** `onTimeout` records `TIMED_OUT`, posts a plain notice, stops the service
  within the deadline, persists Battery Saver and schedules catch-up. The 6 h/24 h budget is a
  platform limit; it is not extended or avoided.
- **Terminal failures:** `NftyRepository.isTerminal` is set on terminal auth/forbidden errors, empty
  topics, or unproductive-retry exhaustion. The service observes it, updates the notification with
  the real reason, posts a stopped notice and stops itself.
- **Honest UI status:** `DeliveryStatusStore` is written by the service and read by the ViewModel;
  in Instant mode the UI shows the service's real state and never opens a second stream.
- **Deep links:** cold start and `onNewIntent` resolve the message ID from Room via
  `MessageRepository.getById`; missing/deleted targets show a banner.
- **Boot:** `BootDeliveryRestorer` re-schedules Battery Saver or posts the Instant resume notice; it
  never starts a `dataSync` service from `BOOT_COMPLETED`.
- **Clock:** the `NftyTopBar` second-boundary sleep fix is preserved and now has a real regression
  test.

## 4. Verification evidence (final, `--rerun-tasks`, run separately)

```text
./gradlew testDebugUnitTest --rerun-tasks
# BUILD SUCCESSFUL, exit 0
# 173 tests, 0 failures, 0 errors, 2 skipped (pre-existing SecretStoreTest KeyStore cases)

./gradlew assembleDebug --rerun-tasks
# BUILD SUCCESSFUL, exit 0
# app/build/outputs/apk/debug/app-debug.apk — 19,557,690 bytes

./gradlew lint --rerun-tasks
# BUILD SUCCESSFUL, exit 0
# 0 errors, 56 warnings
```

No tests were deleted or weakened. Machine: Linux, JDK 21; no emulator, and `adb` is not installed.

## 5. Git state at handoff

- Phase 3 checkpoint commit: **`72dbf36`** (44 files, +3225/−43).
- Working tree clean immediately after that commit; this handoff is the only file added afterwards.
- No history rewriting, no force-push, no deleted files.
- `local.properties`, `*.env`, keystores and build output remain untracked/ignored.
- A secret-pattern scan of the diff and untracked files found no credentials.

## 6. Remaining validation and known risks

No hardware validation has been performed. Before any release or "verified" claim, run the matrix in
`PHASE_3_REPORT.md` §6:

1. Notification permission grant / deny / revoke on Android 13/14/15/16.
2. Instant delivery with screen off; persistent notification.
3. Android 15+ 6-hour `dataSync` cutoff: no crash; timeout notice; Battery Saver fallback.
4. Terminal auth/permission failure while running: service stops, real reason shown.
5. Battery Saver cadence, Doze deferral, duplicate-free 24 h run.
6. Mode switching under load; reboot with each mode; process death; network loss/recovery.
7. Lock-screen privacy; OEM battery managers; cold-start and warm notification taps.

Known accepted risks: inexact Battery Saver timing; OEM process killers; 30-bit notification ID hash
collisions; denied notifications hide FGS/paused notices; no mark-read/save notification actions;
`BOOT_COMPLETED` only (not before first unlock); no FCM fallback; the service terminal branch is
covered by dependencies and inspection, not a full configured integration test. See `RISKS.md`.

## 7. Owner-locked decisions (do not change without owner approval)

- Delivery must support **both Instant and Battery Saver**; never claim Battery Saver is instant.
- Admin scope includes ntfy administration and PVE/homelab operations; PVE only through a separately
  deployed trusted gateway. Never embed credentials or arbitrary shell execution in the APK.
- Two complete UI profiles: Standard Kaminari and dedicated E-Ink (not just grayscale).
- Boox model is unknown until the device arrives; keep E-Ink generic, no invented vendor APIs.
- Notification actions beyond "open message" are deferred.
- Instant is best-effort; Battery Saver is inexact periodic catch-up; no guarantee of instant
  delivery.
- **Phase 4 is NOT authorised.**

## 8. Phase 4 scope (do not start without owner approval)

> **Scope correction (2026-11-09, owner-approved):** `PLAN.md` is authoritative.
> Phase 4 is publish/outbox/activity/command envelope with a **mock** executor.
> E-Ink is Phase 5; ntfy administration and the real PVE gateway are Phase 6.
> The list below is a combined roadmap, not Phase 4 scope. See
> `PHASE_4_ARCHITECTURE.md` and `PHASE_4_IMPLEMENTATION_PLAN.md`.

Compose/publish screen and send outbox; activity timeline (receive, send, server acceptance, command
request/result); command centre with a trusted PVE gateway; dedicated E-Ink profile; ntfy
administration grounded in verified server capabilities.

## 9. Read these first (in order)

1. `AGENTS.md` — engineering constitution and owner-locked decisions.
2. `PLAN.md` — phase scope and status log.
3. `PHASE_3_FINAL_AUDIT.md` — current verdict and residual risks.
4. `PHASE_3_REPORT.md` — implementation, guarantees and device matrix.
5. `PHASE_3_REMEDIATION_REPORT.md` — what the FAIL audit changed.
6. `PHASE_3_AUDIT_REPORT.md` — original findings (preserve; do not rewrite history).
7. `DECISIONS.md`, `RISKS.md`, `TODO.md`, `PROGRESS.md`, `CHANGELOG.md`.
8. `MainViewModel.kt`, `delivery/InstantForegroundService.kt`, `delivery/DeliveryStatus.kt`,
   `data/repository/NftyRepository.kt`, `AppContainer.kt`.
9. Phase 1/2 reports before touching the stream, Room or credentials.

## 10. First actions OpenCode should take

1. Verify the environment and repository state: `git log --oneline -5`, `git status`,
   `./gradlew --version`.
2. Re-run the three gates separately with `--rerun-tasks`; confirm 173 tests and 0 lint errors before
   changing anything.
3. Obtain the owner's Phase 3 sign-off and an explicit decision on Phase 4, or the next device-test
   task. Do not start feature work on assumption.
4. If device testing becomes possible, work through the matrix and record evidence; do not claim
   results without observing them.
5. If a defect is found, fix the cause, add a regression test, and update the reports/changelog.

## 11. Rules

- Do **not** restart the project, replace the architecture, or discard the existing changes.
- Do **not** use destructive Git commands, rewrite history, or force-push.
- Do **not** weaken, delete or skip tests to get a green build.
- Do **not** weaken the delivery guarantees or claim device verification without a device.
- Do **not** start Phase 4 (or publishing UI, activity timeline, command centre, E-Ink profile, ntfy
  admin, PVE gateway) without explicit owner approval.
- Keep secrets out of source, logs, notifications and reports.

**Stop after Phase 3 until the owner responds.**
