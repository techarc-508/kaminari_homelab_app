# Phase 3 Final Audit — Post-Remediation

**Date:** 2026-11-09
**Tree audited:** `KaminariNfty_V1_Plan/repo/kaminari_homelab_app-main`
**Baseline commit:** `5d00827` (Phase 2 remediation; Phase 3 changes are uncommitted at audit time)
**Inputs:** original `PHASE_3_AUDIT_REPORT.md` (FAIL), `PHASE_3_REMEDIATION_REPORT.md`,
remediated source, tests and build outputs.
**Nature of this audit:** fresh inspection and re-execution over the remediated tree. The original
FAIL history is preserved in `PHASE_3_AUDIT_REPORT.md`; its verdict was not edited.

---

## 1. Verdict

> ## **PASS WITH CONDITIONS**

No Critical or High **code** defect remains. All four blocking audit findings (A-01…A-04) are fixed
and have regression coverage. The conditions are external: the physical-device matrix
(Doze, reboot, Android 15 timeout, OEM power management, notification rendering) has not been run,
because no device/emulator is available on this workstation. Phase 3 may be checkpointed and handed
off, but it must not be described as device-verified, and Phase 4 still requires explicit owner
approval.

---

## 2. Independent evidence gathered in this audit

All commands were run from the repository root, separately, with forced execution:

| Command | Exit | Result |
|---|---|---|
| `./gradlew testDebugUnitTest --rerun-tasks` | 0 | BUILD SUCCESSFUL, 32 tasks executed, 1 m 07 s; XML totals **173 tests, 0 failures, 0 errors, 2 skipped** |
| `./gradlew assembleDebug --rerun-tasks` | 0 | BUILD SUCCESSFUL, 38 tasks executed, 39 s; `app/build/outputs/apk/debug/app-debug.apk` (**19,557,690 bytes**) |
| `./gradlew lint --rerun-tasks` | 0 | BUILD SUCCESSFUL, 30 tasks executed, 1 m 13 s; **0 errors, 56 warnings** |

Skipped tests are the two pre-existing `SecretStoreTest` AndroidKeyStore cases. No test was deleted,
weakened, or newly skipped; the suite grew from 151 to 173. Test classes now include
`InstantForegroundServiceTest`, `InstantTimeoutFallbackTest`, `BootDeliveryRestorerTest`,
`BatterySaverWorkerTest`, `DeliveryStatusTest`, `DeliveryModeTest`, and a real
`NiftyTopBarClockTest`.

Repository checks:

- `git diff --check` clean; **no deletions**; 18 tracked files changed (+781/−43 before docs).
- No secret-like literals in the diff or untracked files.
- `local.properties`, `*.env`, keystores and build directories remain untracked/ignored; no APK or
  generated output is tracked.
- The only new dependency is test-only `androidx.work:work-testing:2.10.1`, matching the production
  WorkManager version.

No code was changed during this audit.

---

## 3. Finding closure

| ID | Severity | Status | Verification |
|---|---|---|---|
| A-01 | Critical | **Fixed** | `onTimeout(int,int)` override on API 35+, `TIMED_OUT` status, notification, Battery Saver fallback, `stopSelf`. `InstantForegroundServiceTest` asserts terminal status and self-stop; `InstantTimeoutFallbackTest` asserts persisted mode + scheduling. |
| A-02 | High | **Fixed** | `NftyRepository.isTerminal`; service observes state/terminal/error, updates notification and stops. Repository terminal flag covered; notifier text covered; service terminal branch verified by inspection (see §4). |
| A-03 | High | **Fixed** | `DeliveryStatusStore` written by the service and read by the ViewModel in Instant mode; test asserts `AUTHENTICATED` is shown while `stream.startCalls` stays empty. |
| A-04 | High | **Fixed** | Profile wait + Room `getById`; cold-start test with empty in-memory window and a persisted row; missing-target test; DAO test outside the recent window. |
| A-05 | Medium | **Fixed** | `FAILED` status on `startForeground` failure, user notification and banner; terminal banner test. |
| A-06 | Medium | **Fixed** | Clock test renders the real composable, asserts initial time, no sub-second change, next-second update, and idle composition. |
| A-07 | Medium | **Substantially fixed** | Service timeout, fallback, boot, worker, status, deep-link and DAO coverage added. No full service integration test (see §4). |
| A-08 | Medium | **Fixed** | Only `INSTANT`/`BATTERY_SAVER` selectable; legacy `SAVED` migration tested; fresh default tested. Saved messages untouched. |
| A-09 | Medium | **Fixed / limited by design** | Boot uses the app-owned scope and tested `BootDeliveryRestorer`; `LOCKED_BOOT_COMPLETED` intentionally unsupported before first unlock. |
| A-10 | Medium | **Fixed** | Reports and plan corrected; original audit preserved with appended evidence. |
| A-11–A-14 | Low | **Accepted / documented** | Backoff, hash collisions, denied-notification visibility and benign double start recorded as residual risks. |

---

## 4. Requirements re-checked

- **Manifest:** `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`,
  `RECEIVE_BOOT_COMPLETED`; service `exported=false`, `dataSync`; receiver `exported=false`.
- **Foreground start:** synchronous `startForeground` in `onStartCommand` via `ServiceCompat`;
  failure is reported and stops the service.
- **Android 15/16:** timeout handled for `targetSdk 36`; boot start correctly refused; the 6 h/24 h
  budget is documented as a platform restriction, not circumvented.
- **Terminal failures:** auth, forbidden, no topics, and retry exhaustion all set `isTerminal`;
  the service maps them to honest phases, refreshes/removes the ongoing notification and stops.
- **Status ownership:** in Instant mode exactly one stream exists (service-owned); the ViewModel
  observes status and can no longer show "offline" over a live service or "live" over a dead one.
- **Deep links:** cold start and `onNewIntent` both resolve the ID against Room; missing/deleted
  targets produce a banner, never an empty detail.
- **Battery Saver:** unique 15-minute periodic work, network constraint, 60 s bounded catch-up,
  mode re-check, Room dedupe, cursor, reboot re-schedule; worker paths tested without network.
- **Two-mode decision:** only Instant and Battery Saver are selectable; legacy selection migrates
  one-way to Battery Saver. Saved messages/screen remain.
- **Clock fix:** preserved; real regression test replaces the vacuous assertion.
- **Privacy:** private lock-screen version, topic-only public stub, immutable PendingIntents,
  no credentials in intents/notifications, permission checks and `SecurityException` guards.

---

## 5. Residual risks and limitations (do not overstate)

1. **No device validation.** `adb` is unavailable and no emulator is configured. Doze, OEM killers,
   reboot, real notification rendering, permission revocation and the actual 6-hour timeout are
   unobserved.
2. **Service terminal branch is not end-to-end tested.** The repository flag, status mapping and
   notifier are tested; the service-level reaction is verified by code inspection. A full
   Robolectric service test would need a configured profile, Keystore credential and mock stream.
3. **Notification permission denial** hides the persistent and paused notices on Android 13+; the
   status is still visible in Settings.
4. **Notification/PendingIntent IDs** use 30-bit hashes; a collision can overwrite an earlier
   notification/tap target. History is unaffected.
5. **Notification actions** (mark read/save) remain deferred by the owner; only "open" exists.
6. **Boot** handles `BOOT_COMPLETED` only; DataStore is unavailable before first unlock.
7. **Battery Saver** is inexact by design; repeated failure is visible only as less frequent data,
   not as an alert.
8. **Instant remains best-effort.** The timeout fallback prevents a crash and a silent gap, but it
   does not keep the live stream alive past the platform budget.

---

## 6. Why PASS WITH CONDITIONS

- **Not PASS:** the device matrix and the service end-to-end terminal test are missing, and several
  Low risks are accepted rather than eliminated.
- **Not FAIL:** the original Critical/High defects are fixed, the build/test/lint gates are clean,
  no secret/regression/deletion was found, and the remaining gaps cannot be validated on this host.
- The conditions below are prerequisites for treating Phase 3 as device-verified; they are not
  prerequisites for the checkpoint commit or the OpenCode handoff.

## 7. Conditions for final acceptance

1. Run the §6 device matrix from `PHASE_3_REPORT.md` on Android 13/14/15/16, including the 6-hour
   `dataSync` cutoff and the timeout fallback.
2. Verify the terminal auth/forbidden path on a device or with a service integration test.
3. Confirm OEM battery-manager behaviour and record it; never promise delivery.
4. Owner sign-off on the two-mode UI and the legacy `SAVED` → `BATTERY_SAVER` migration.
5. Owner authorisation before any Phase 4 work.
