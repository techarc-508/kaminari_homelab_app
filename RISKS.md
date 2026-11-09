# Risk Register — Kaminari Nfty V1

**Last updated:** 2026-11-09 (Phase 3 remediation)
Severity reflects user impact; state reflects what is verified.

| ID | Risk | Severity | State / mitigation |
|---|---|---|---|
| R-01 | **No real-device validation of background delivery.** Doze, OEM killers, reboot, notification rendering and the Android 15 timeout are unobserved. | High | Open. Device matrix in `PHASE_3_REPORT.md` §6. Do not claim hardware verification. |
| R-02 | **Android 15+ caps `dataSync` foreground services at 6 h per rolling 24 h.** Instant cannot run indefinitely. | High | Mitigated, not eliminated: `onTimeout` stops before the fatal deadline and migrates to Battery Saver; documented in the UI copy and report. Budget is neither extended nor reset by the app. |
| R-03 | **Terminal auth/permission failure stops delivery.** User must fix credentials. | High | Mitigated: service stops, posts the real reason, status/banner updated. No automatic credential recovery by design. |
| R-04 | **OEM battery managers may kill WorkManager/service delivery.** | High | Open and documented; no promise of delivery. Requires device testing per OEM. |
| R-05 | **Battery Saver timing is inexact** (15-minute WorkManager floor, Doze). | Medium | Accepted and visible in the mode copy. Measure on device. |
| R-06 | **Notification IDs/PendingIntent request codes use 30-bit hashes.** A collision can overwrite an earlier notification or tap target. | Low | Accepted; history is unaffected. Collision-safe IDs are future polish. |
| R-07 | **Denied `POST_NOTIFICATIONS` hides the persistent/paused notices** on Android 13+. | Low | Mitigated by the Settings status row and the notification-settings action; delivery itself still runs where Android permits. |
| R-08 | **Boot restore handles `BOOT_COMPLETED` only**, and only after first unlock. | Low | Accepted: credential-encrypted DataStore is unavailable earlier; documented. |
| R-09 | **Notification actions (mark read/save) are absent.** | Medium | Deferred by owner decision D-05; only "open message" is implemented. |
| R-10 | **No FCM/UnifiedPush fallback.** Delivery relies on the app process/WorkManager. | Medium | Optional spike in PLAN; not required for Phase 3. |
| R-11 | **Service terminal branch lacks an end-to-end test.** Repository flag, status mapping and notifier are tested; the full service reaction is inspected, not executed in a configured integration test. | Medium | Documented in `PHASE_3_FINAL_AUDIT.md`; add a service integration test when a mock profile/stream harness exists. |
| R-12 | **Moot WorkManager backoff.** `setBackoffCriteria` is configured but the worker never returns `retry()`. | Low | Accepted: the next scheduled period is the retry. Remove or document in a future cleanup. |
| R-13 | **E-Ink profile and vendor behavior unknown** until the Boox device arrives. | Medium (Phase 4+) | Deferred by owner decision D-04; do not invent capabilities. |
| R-14 | **Real ntfy server version / self-hosted smoke test** remains unverified. | Medium | Phase 2 condition; run against the actual server before release claims. |
| R-15 | **Secrets/Keystore availability** (Robolectric skips the KeyStore round-trip; a device Keystore issue would block credentials). | High | Unit tests cover fallback behavior; verify credential save/load on hardware. |
