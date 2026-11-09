# Phase 3 Final Audit — Notification and Background Delivery

**Date:** 2026-11-09
**Auditor role:** Independent owner-appointed audit (read-only)
**Repository:** `KaminariNfty_V1_Plan/repo/kaminari_homelab_app-main`
**HEAD:** `5d00827` — *Phase 2 remediation* (no Phase 3 commit exists; all Phase 3 work is uncommitted)
**Scope:** Phase 3 implementation only. No fixes, commits, releases, or Phase 4 work were performed.
**Status:** This document preserves the original FAIL audit. Remediation evidence is appended in
§11; the fresh post-remediation verdict is in `PHASE_3_FINAL_AUDIT.md`.

---

## 1. Verdict

> ## **FAIL — not ready for Phase 3 acceptance as reported**

The three automated gates are green (151 tests, debug APK, lint 0 errors), and the core mode
architecture is sound. The failure is behavioural and platform-compliance, not build quality:
Phase 3 introduces **one fatal platform path** (Android 15+ foreground-service timeout), **one
false-status path** (the Instant service keeps advertising live delivery after the stream has
terminally died), **one misleading UI state** (Inbox shows offline while the Instant service is
live), and **one broken promised feature on cold start** (notification deep links). The Phase 3
report also overstates two of these behaviours.

This is recoverable with the bounded remediation in §8. After those fixes and a re-run of the
gates, the verdict should reasonably convert to **PASS WITH CONDITIONS** (conditions = the device
matrix in §7).

---

## 2. Evidence gathered

Read in full or in relevant part: `AGENTS.md`, `PLAN.md` (root + repo), `PHASE_1_REPORT.md`,
`PHASE_2_REPORT.md`, `PHASE_2_GATE_REPORT.md`, `PHASE_3_REPORT.md`, `CHANGELOG.md`, and every
Phase 3 source/test file. Platform rules were checked against the official Android 15
behaviour-changes documentation (`dataSync` 6 h/24 h cap, `Service.onTimeout(int,int)`, fatal
`RemoteServiceException` if the service does not stop, boot-start prohibition, timer reset when the
app comes to the foreground).

`RISKS.md` and `TODO.md` do not exist anywhere under the plan or repository tree; this audit
therefore grades against `PHASE_3_REPORT.md` §9 and `PLAN.md` instead.

All commands were run from the repository root, one at a time (the Phase 2 report documents daemon
heap exhaustion when Gradle tasks are combined).

| Command | Result | Detail |
|---|---|---|
| `./gradlew testDebugUnitTest --rerun-tasks` | **SUCCESS**, exit 0 | 32 tasks executed, 1 m 05 s. XML totals: **151 tests, 0 failures, 0 errors, 2 skipped** |
| `./gradlew assembleDebug --rerun-tasks` | **SUCCESS**, exit 0 | 38 tasks executed, 39 s. `app/build/outputs/apk/debug/app-debug.apk`, 19,541,306 bytes |
| `./gradlew lint --rerun-tasks` | **SUCCESS**, exit 0 | 30 tasks executed, 1 m 12 s. **0 errors, 55 warnings** |

Skipped tests are the two pre-existing `SecretStoreTest` AndroidKeyStore cases ("unknown keys return
null", "roundtrip through keystore-backed encryption"); they are unrelated to Phase 3. No test was
weakened, deleted, or newly skipped. The suite grew from 136 to 151 (15 new tests: 9
`MainViewModelTest`, 6 `NftyNotifierTest`).

Per-class totals: `MainViewModelTest` 28, `NftyStreamClientTest` 23, `NtfyEventParserTest` 19,
`NtfyEndpointTest` 14, `MessageDaoTest` 12, `StreamEngineTest` 11, `MessageRepositoryTest` 9,
`ProfileRepositoryTest` 9, `NftyNotifierTest` 6, `NftyRepositoryTest` 5, `InboxScreenTest` 4,
`ExampleRobolectricTest` 4, `MessageMapperTest` 3, `SecretStoreTest` 2, `ExampleUnitTest` 1,
`NiftyTopBarClockTest` 1.

**Device validation was not performed and cannot be claimed.** `adb` is not installed on this
workstation, so no emulator or physical device could be reached. Everything below that
concerns Doze, OEM killers, real notification rendering, reboot, or the 6-hour budget is static
analysis against the platform contract, not observed behaviour.

---

## 3. Findings

### Critical

| ID | Finding | Evidence |
|---|---|---|
| **A-01** | **Instant mode can crash the process on Android 15+.** The `dataSync` foreground service has no `Service.onTimeout(int,int)` override. After 6 h of `dataSync` foreground time in a rolling 24 h window the system calls `onTimeout`; without a prompt `stopSelf()` the platform throws a fatal `RemoteServiceException`. `targetSdk = 36`, so the rule applies. There is also no fallback to Battery Saver and no user-visible warning as the budget is consumed; after the crash the budget is spent, so the service cannot be restarted until the app has been in the foreground to reset the timer. | `app/src/main/java/com/rplab/kaminarinfty/delivery/InstantForegroundService.kt:50-64` (no `onTimeout`), `:38-40`; manifest `app/src/main/AndroidManifest.xml:31-34` (`dataSync`, `exported=false`); `app/build.gradle.kts:13-14` (`minSdk 24`, `targetSdk 36`); permission `AndroidManifest.xml:8-9`. Verified absent with `grep -rn "onTimeout" app/src/main` → none. |

### High

| ID | Finding | Evidence |
|---|---|---|
| **A-02** | **The service keeps claiming "live delivery" after the stream is permanently dead.** `runEngine` starts the subscription (which returns immediately) and then suspends in `awaitCancellation()` forever. It never observes `repository.connectionState` or `lastError`, so terminal AUTH/FORBIDDEN failures and `MAX_UNPRODUCTIVE_ATTEMPTS` exhaustion leave the foreground service running with "Watching N topics — live delivery" while no stream exists. The `catch` at `:127-133` only covers exceptions thrown before suspension; terminal stream errors are values, not throws. Only process death or the user switching mode clears it. | `InstantForegroundService.kt:102-133`; `NftyRepository.kt:171-180` (terminal flag + state, no throw), `:209-220` (retry exhaustion `break`); live notification text `NftyNotifier.kt:116-121`. |
| **A-03** | **Instant mode shows a false offline state in the app.** While Instant is active the ViewModel deliberately stops its own repository (`streamRepository.stopSubscription()`), and the service uses a separate `NftyRepository()`. The ViewModel's `authState` therefore stays `DISCONNECTED`, and Inbox renders the "offline / cached history only" treatment even though the service is connected and delivering. This directly contradicts the phase's "honest status" requirement. | `MainViewModel.kt:534-545` (stops VM stream in INSTANT), `:154-161` (collects only `streamRepository.connectionState`); `InstantForegroundService.kt:99` (own repository). |
| **A-04** | **Cold-start notification taps do not open the message.** `MainActivity` consumes the pending ID in a `LaunchedEffect` fired on first composition, while `_uiState.messages` is still `emptyList()` (DataStore → profile → Room has not emitted). `openMessageFromNotification` searches only the loaded window and immediately shows "Notification is not in the loaded history window". The existing test pre-populates `messages`, so it does not cover this path. Warm `onNewIntent` taps work. This contradicts `PHASE_3_REPORT.md` §2 ("handled both for cold start and `onNewIntent`"). | `MainActivity.kt:55-62`, `:86-92`, `:125-129`; `MainViewModel.kt:569-578`; initial state `MainViewModel.kt:80` + `UiState.messages = emptyList()`; test gap `MainViewModelTest.kt` deep-link case. |

### Medium

| ID | Finding | Evidence |
|---|---|---|
| **A-05** | **A foreground-start failure is invisible to the user.** `startInstant()` returns true when `startForegroundService()` is *accepted*, not when `startForeground()` succeeds. If the service cannot go foreground (including an exhausted/denied FGS start), `startForegroundSafely()` fails and the service stops itself silently; the ViewModel has already shown "Instant mode on". A missing profile/topics inside `runEngine` likewise calls `stopSelf()` with no feedback. | `DeliveryController.kt:41-47`; `InstantForegroundService.kt:50-56`, `:66-78`, `:94-97`; banner logic `MainViewModel.kt:536-545`, `:287-301`. |
| **A-06** | **The clock "regression evidence" is vacuous.** `NiftyTopBarClockTest` renders an empty composition, never instantiates `NftyTopBar`, never advances the clock state, and asserts `assertTrue(true)`. It cannot detect a return of the frame-loop bug or a stopped clock. `PHASE_3_REPORT.md` §2/§5 presents the fix as regression-tested. | `app/src/test/java/com/rplab/kaminarinfty/ui/components/NiftyTopBarClockTest.kt:27-43`; production fix (injectable `nowMillis`) `NftyTopBar.kt:58-76`. (Note: the class is `NftyTopBar`, not `NiftyTopBar` as written in the report.) |
| **A-07** | **The actual delivery engines have no automated tests.** No test instantiates `InstantForegroundService`, `BatterySaverWorker`, `BootReceiver`, or the real `AndroidDeliveryController`. Dedupe is only tested at the DAO/repository level; mode logic is tested against a fake controller. Worker/doze/reboot/timeout behaviour is entirely unverified, and the service is where A-01/A-02 live. | `app/src/test/java/com/rplab/kaminarinfty/delivery/NftyNotifierTest.kt` only; `testing/Fakes.kt` fake controller; zero matches for the engine classes under `app/src/test`. |
| **A-08** | **The third mode ("Saved only") collides with the Saved-messages feature and is the default.** The selector offers `INSTANT`, `BATTERY_SAVER`, `SAVED`; `SAVED` is labelled "Saved only" with summary "No background delivery", while the navigation already has a `SAVED` tab for bookmarked messages. Fresh installs default to `SAVED`, i.e. no background delivery until the user changes Settings. The mode is legitimate legacy/foreground-only behaviour, but the naming is ambiguous and the default deserves an explicit owner decision. | `model/DeliveryMode.kt:25-29`; `AppPreferences.kt:19`, `:56-57`; Settings selector over `DeliveryMode.values()`; `AppDestination.SAVED` in `MainViewModel.kt:591-599`. |
| **A-09** | **Boot recovery is narrower than documented, and unmanaged.** `BootReceiver` handles only `BOOT_COMPLETED` (no `LOCKED_BOOT_COMPLETED`, no `MY_PACKAGE_REPLACED`), launches work from a fresh unmanaged `CoroutineScope(Dispatchers.IO)` inside `goAsync()` (AGENTS.md prohibits unmanaged app-owned global scopes), swallows every exception, and posts the Instant resume notification only if notifications are permitted — otherwise the user gets no signal at all that Instant paused. | `BootReceiver.kt:23-46`; manifest `:36-42`; AGENTS.md concurrency rule. |
| **A-10** | **Phase 3 report accuracy.** Three material claims are not supported: cold-start deep links (A-04), the "honest banner when Instant could not be started" (A-05 only covers request rejection), and clock regression evidence (A-06). The §9 FGS row understates the timeout as "can stop the service" rather than "crashes the app unless `onTimeout` stops it". | `PHASE_3_REPORT.md` §2, §5, §9, §11. |

### Low

| ID | Finding | Evidence |
|---|---|---|
| **A-11** | `setBackoffCriteria(EXPONENTIAL, 30s)` on the periodic request is moot: the worker never returns `Result.retry()`; every failure is `Result.success`. Not harmful, but dead configuration that suggests retry behaviour that does not exist. | `DeliveryController.kt:60-66`; `BatterySaverWorker.kt:76-78`. |
| **A-12** | Notification identity uses 30-bit `String.hashCode()` (message IDs) and `messageId.hashCode()` as the `PendingIntent` request code, so collisions overwrite an earlier notification/tap target. Channels are recreated on every post; the ongoing notification text never reflects connecting/reconnecting/auth-failed states; no mark-read/save actions (deferred by owner, correctly documented). | `NftyNotifier.kt:159-166`, `:179-185`, `:31-59`, `:116-121`. |
| **A-13** | If `POST_NOTIFICATIONS` is denied, Instant mode still runs as a foreground service but its persistent notification is suppressed on Android 13+ (visible only in the task manager). There is no warning in the delivery selector beyond the general blocked-notifications row, and the boot resume notice cannot be shown. | `NftyNotifier.kt:64-69`, `:141-157`; `SettingsScreen.kt` notification-blocked row. |
| **A-14** | Process/reboot polish: `START_STICKY` restarts the service after a kill but `onTaskRemoved` is not handled; `startInstant()` may be invoked twice during startup (bind + mode reconcile), though `onStartCommand` cancels the previous engine so this is benign. | `InstantForegroundService.kt:50-64`; `MainViewModel.kt:287-301`. |

---

## 4. What is correct and verified by inspection

- **Manifest and permissions:** `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_DATA_SYNC`, `RECEIVE_BOOT_COMPLETED`; service `exported=false` with
  `foregroundServiceType="dataSync"`; boot receiver `exported=false` (acceptable for the protected
  system broadcast). `AndroidManifest.xml:5-10`, `:31-42`.
- **Foreground start timing:** `startForegroundSafely()` is called synchronously at the top of
  `onStartCommand`, within the 5-second window, and uses `ServiceCompat` with the correct type.
- **Android 15 boot rule:** the app correctly refuses to start `dataSync` from `BOOT_COMPLETED`
  and posts a resume notification instead (`BootReceiver.kt:33-36`).
- **Battery Saver mechanics:** unique periodic work `kaminari_battery_saver` at WorkManager's
  15-minute floor with `NetworkType.CONNECTED`, `ExistingPeriodicWorkPolicy.UPDATE`, one bounded
  `poll=1` catch-up capped at 60 s, mode re-check before work, cursor from the newest stored ID,
  and notification only for rows actually inserted (Room `IGNORE` dedupe). Scheduling is
  explicitly inexact and documented as such. `DeliveryController.kt:55-72`;
  `BatterySaverWorker.kt:35-78`.
- **Notification privacy:** `VISIBILITY_PRIVATE` plus a public stub that shows only `#topic` and
  "New notification"; IDs only in PendingIntent extras; `FLAG_IMMUTABLE`; permission check and
  `SecurityException` catch around every `notify()`. `NftyNotifier.kt:64-80`, `:99-113`,
  `:159-166`.
- **Single delivery owner (warm path):** the ViewModel suppresses its own notifications in
  Instant mode and stops its own stream; the service stores through the same Room dedupe gate, so
  a message cannot be notified twice through the normal paths. `MainViewModel.kt:190-200`,
  `:287-301`.
- **Clock implementation:** the infinite per-frame `withFrameNanos` loop is gone; the clock sleeps
  to the next second boundary (one recomposition/s) and exposes an injectable `nowMillis`.
  `NftyTopBar.kt:58-76`. The four previously hanging `InboxScreenTest` cases now pass.
- **Mode persistence:** DataStore key defaults to `SAVED` and uses a safe `valueOf` fallback, so a
  corrupt/unknown value cannot crash startup. `AppPreferences.kt:44-57`.
- **No Phase 1/2 regression found** in streaming, deletion events, credentials, or the Room
  contract; no schema change was made.

---

## 5. Delivery guarantee assessment

| Mode | What the code actually guarantees | What it does not guarantee |
|---|---|---|
| **Instant** | A visible foreground `dataSync` service while the app process lives, one SSE connection, Room persistence, per-message notifications, `START_STICKY` restart after a kill, resume notice after reboot. | No uptime guarantee. Doze/OEM killers can stop it; **Android 15+ caps `dataSync` at 6 h/24 h and will crash the app without `onTimeout` (A-01)**; after a terminal auth/stream error it silently stops delivering while still claiming live (A-02); its state is invisible to the UI (A-03). |
| **Battery Saver** | A unique ~15-minute periodic catch-up under a network constraint, bounded at 60 s, deduped by Room, scheduled after reboot and re-scheduled on boot. | Exact timing (Doze defers indefinitely), delivery while the device is offline, any retry sooner than the next period, and any visibility into repeated failures (counts are returned as work data but never surfaced). |
| **Saved only** | Foreground stream + notifications while the app is open (Phase 2 behaviour), nothing scheduled in the background. | Any delivery once the app leaves the foreground. Default for fresh installs; see A-08. |

No mode provides FCM/UnifiedPush fallback. The report's wording ("best-effort") is fair for
Battery Saver; for Instant it is incomplete because the strongest failure is a crash and a
false live indicator.

---

## 6. Saved-mode assessment

`SAVED` is not a fourth delivery engine; it is the Phase 2 foreground-only behaviour exposed as a
selectable mode and kept as the persisted default. It does not violate the owner's "both modes"
decision (Instant and Battery Saver both exist), and it is genuinely useful as an "in-app only"
choice. Two issues require an owner decision, not a code change yet:

1. **Naming:** "Saved only" is easily confused with the Saved (bookmarked) messages tab. Suggested
   label: **"In-app only"** with summary "No background delivery" and the existing trade-off text.
2. **Default:** a fresh install receives nothing while backgrounded until the user visits Settings.
   That is defensible (least-surprise battery/privacy default) but should be an explicit choice;
   the alternative is defaulting to Battery Saver.

**Recommendation:** keep the mode, rename it, keep the default for now, update the banner copy
("Saved-only mode — no background delivery") to match, and record the decision in `PLAN.md`.
Behaviour must not change until the owner approves.

---

## 7. Outstanding hardware validation

No device or emulator is available here (`adb` not installed). The following remain **unverified**,
not passed:

1. Android 13/14/15/16: grant, deny, and mid-session revoke `POST_NOTIFICATIONS`; confirm the
   Settings status row and the notification-settings action (API 26+ and API 24–25 fallbacks).
2. Instant: screen off / app backgrounded, live delivery and persistent notification; **observe the
   Android 15 6-hour `dataSync` cutoff and the `onTimeout` behaviour** (currently expected to crash
   — this is the A-01 reproduction).
3. Instant: kill the server / use a wrong token while the service runs; confirm what the user sees
   (A-02 reproduction) and whether the service ever stops.
4. Battery Saver: 15-minute cadence, Doze deferral, duplicate-free repeated runs, 24 h lag.
5. Mode switching under message load: no loss, no double notification.
6. Reboot with each mode persisted; Instant resume notice; Battery Saver rescheduling.
7. Process death (`am kill`, "Don't keep activities") in Instant, including `START_STICKY` restart.
8. Network loss/recovery in both the service and the worker.
9. Lock-screen privacy: body hidden when locked, visible when unlocked.
10. OEM battery managers (Xiaomi/Samsung/Oppo): document variance, never promise delivery.
11. **Cold-start deep link** on a real device (A-04 reproduction) and warm `onNewIntent` tap.

---

## 8. Required remediation before Phase 3 can be accepted

Ordered; none of this was implemented during the audit.

1. **A-01 (blocking):** override `onTimeout(startId, fgsType)` on API 35+, call `stopSelf()`
   immediately, persist a stop reason, and post a "live delivery paused — reopen the app" notice
   (respecting the notification permission). Consider whether `dataSync` is the right type for a
   long-lived message stream (e.g. `remoteMessaging`/`specialUse` have different policy and timeout
   implications) and document the choice. Add a Robolectric test invoking `onTimeout`.
2. **A-02 (blocking):** have the service collect `connectionState`/`lastError`; on terminal
   auth/permission failure or retry exhaustion, update the notification to a truthful terminal
   state and `stopSelf()`. Do not leave an ongoing "live delivery" notification over a dead
   stream.
3. **A-03 (blocking):** expose the service's real state to the UI (shared `StateFlow` in
   `AppContainer`, or a process-wide connection state the service writes), so Instant mode never
   renders "offline" while the service is live, and renders the actual failure when it is not.
4. **A-04 (blocking):** resolve notification taps by ID from Room (or defer consumption until the
   profile and message window have loaded), not from the initial empty list. Add a test with an
   empty initial `messages` list and a stored row.
5. **A-05:** propagate `startForeground` failure back to the ViewModel (shared state or a local
   broadcast) so the banner reflects reality, not merely that the start request was accepted.
6. **A-06:** replace the vacuous clock test with a real one using the injectable `nowMillis` and
   `mainClock.advanceTimeBy`, asserting the rendered time changes and no continuous frame requests
   occur.
7. **A-08:** owner decision on label/default; rename "Saved only" to avoid collision, update copy.
8. **A-07:** add engine tests: worker dedupe/failure/timeout, boot receiver per mode, service
   terminal-error stop and `onTimeout`, cold-start deep link.
9. **A-09/A-11/A-12 (cleanup):** use a managed scope or `runBlocking` within `goAsync`; drop the
   dead backoff config or actually retry; make IDs collision-safe and stop recreating channels on
   every post; consider a higher-importance channel for the reboot resume notice.
10. **A-10/A-14 (docs):** correct `PHASE_3_REPORT.md` §2/§5/§9, use the real class name
    `NftyTopBar`, and add `RISKS.md`/`TODO.md` or remove the expectation that they exist.

---

## 9. Git checkpoint audit

| Check | Result |
|---|---|
| HEAD | `5d00827` "Phase 2 remediation"; no Phase 3 commit |
| Working tree | 13 modified files, 9 untracked paths; **+540 / −29** lines in tracked files |
| Deleted files | None |
| `git diff --check` | Clean (no whitespace errors) |
| Secrets in diff/untracked | None found (scan for token/API-key/private-key/password patterns) |
| Ignored config | `local.properties`, `*.env`, `debug.keystore`, `*.jks`, build dirs are ignored; none tracked |
| Build artifacts | `app/build`, `.gradle`, `.kotlin` ignored; no APK staged |
| Generated sources | None added to the tree |
| Untracked | `PHASE_3_REPORT.md`, the `delivery/` package, `DeliveryMode.kt`, `ic_notification.xml`, delivery tests — all intended Phase 3 files |

**Recommendation: do not create the Phase 3 checkpoint commit yet.** A checkpoint now would record
A-01–A-04 as "Phase 3 complete". After remediation and a clean re-run of all three gates, commit
the whole Phase 3 change set once, including the corrected report and this audit report
(re-audited), with no unrelated files. No destructive Git operation was used, and nothing was
committed.

---

## 10. Decision requested from the owner

1. **Accept or reject the FAIL verdict**, and authorise remediation items 1–4 (A-01…A-04) before
   any further phase work.
2. **Saved-mode naming/default:** rename to "In-app only" and keep the current default, or choose a
   different default. No behaviour changed.
3. **Device availability:** confirm whether a physical Android 13+ device (ideally Android 15/16)
   can be attached, since the blocking guarantees cannot be signed off without it.
4. **Checkpoint:** authorise the single Phase 3 commit only after the fixes and re-audit.

No source, test, build, or configuration file was modified by this audit. The only file created is
this report. Phase 4 was not started.


---

## 11. Remediation evidence (appended 2026-11-09, after owner authorisation)

The original findings above are unchanged. This section records what was fixed, where, and how it
was verified.

| Finding | Resolution | Evidence |
|---|---|---|
| **A-01** Critical — Android 15 timeout crash | `InstantForegroundService` now overrides `onTimeout(startId, fgsType)` (API 35+), records `TIMED_OUT`, posts a plain notice, stops foreground, persists Battery Saver, schedules catch-up and calls `stopSelf(startId)`. The 6 h budget is documented as a platform limit, not avoided. | `delivery/InstantForegroundService.kt`; `delivery/InstantTimeoutFallback.kt`; `InstantForegroundServiceTest` (timeout → `TIMED_OUT` + stopped), `InstantTimeoutFallbackTest`, `DeliveryStatusTest` |
| **A-02** High — dead stream, false live notification | `NftyRepository` exposes `isTerminal` (terminal auth/forbidden, no topics, retry exhaustion). The service observes `connectionState` + `isTerminal` + `lastError`, updates the live-notification text per phase, and on terminal failure notifies the real reason and stops itself. | `NftyRepository.kt`, `InstantForegroundService.observeRepository`, `NftyNotifierTest` (phase text/never-live), `NftyRepositoryTest` terminal flag |
| **A-03** High — Inbox showed offline while service was live | Added process-wide `DeliveryStatusStore`; the service writes it, the ViewModel reads it in Instant mode, mapping `LIVE` to the observed auth result and never opening a second stream. Setting `authState` from the stopped ViewModel repository is skipped in Instant mode. | `delivery/DeliveryStatus.kt`, `AppContainer.kt`, `MainViewModel.kt`; `MainViewModelTest` "reflects live service state without opening a second stream" |
| **A-04** High — cold-start deep link failed | `openMessageFromNotification` now suspends until a profile exists, checks the loaded window, then loads by ID from Room (`MessageRepository.getById` / `MessageDao.getById`). Missing/deleted targets get an honest banner. | `MainViewModel.kt`, `MessageRepository.kt`, `Daos.kt`, `Fakes.kt`; `MainViewModelTest` cold-start + missing targets; `MessageDaoTest` |
| **A-05** Medium — invisible `startForeground` failure | Failure publishes `FAILED` with a readable reason, posts a notice when permitted and stops; the ViewModel surfaces terminal status in its banner. | `InstantForegroundService.onStartCommand`; `MainViewModelTest` terminal status banner |
| **A-06** Medium — vacuous clock test | Replaced with a real Compose test that renders `NftyTopBar`, asserts the injected time, asserts no change within a second, asserts the next second updates, and asserts the composition reaches idle. | `NiftyTopBarClockTest` (2 tests) |
| **A-07** Medium — no engine tests | Added timeout service test, timeout fallback test, boot restorer tests, worker mode/no-profile tests, repository terminal test and DAO deep-link test. These are Robolectric/unit tests, not device tests. | `app/src/test/java/.../delivery/`, `NftyRepositoryTest`, `MessageDaoTest` |
| **A-08** Medium — third "Saved only" mode | Removed `SAVED` from the selectable enum; stored `SAVED` migrates one-way to `BATTERY_SAVER`; fresh installs default to `BATTERY_SAVER`. Saved messages screen unchanged. | `model/DeliveryMode.kt`, `AppPreferences.kt`, `SettingsScreen.kt`; `DeliveryModeTest`, `MainViewModelTest` default |
| **A-09** Medium — unmanaged boot scope / narrow handling | `BootReceiver` uses the app-owned scope and delegates to tested `BootDeliveryRestorer`; `LOCKED_BOOT_COMPLETED` remains intentionally unsupported (DataStore unavailable before unlock) and is documented. | `BootReceiver.kt`, `BootDeliveryRestorer.kt`, `BootDeliveryRestorerTest` |
| **A-10** Medium — report accuracy | This report, `PHASE_3_REPORT.md`, `PLAN.md` and `PHASE_3_FINAL_AUDIT.md` were corrected; the cold-start, banner and clock claims now match tested behaviour. | documentation set |
| **A-11** Low — moot backoff | Documented in `RISKS.md`; the worker deliberately does not retry sooner than the next period. | `RISKS.md` |
| **A-12** Low — notification ID/PendingIntent hash collisions | Documented in `RISKS.md` as an accepted bounded risk; not changed in this phase. | `RISKS.md` |
| **A-13** Low — notification permission denied hides the FGS notice | Documented; status remains visible in Settings. | `RISKS.md` |
| **A-14** Low — startup can start the service twice | Benign: `onStartCommand` cancels the previous engine before starting a new one; documented. | service code |

Post-remediation verification (all run separately with `--rerun-tasks`): `testDebugUnitTest`
**173 tests / 0 failures / 2 pre-existing skips**, `assembleDebug` **SUCCESS** (APK 19,557,690
bytes), `lint` **0 errors / 56 warnings**. No device validation was possible.
