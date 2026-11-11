# Risk Register — Kaminari Nfty V1

**Last updated:** 2026-11-09 (Phase 3 accepted; Phase 4 Stage A complete)
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
| R-14 | ~~Real ntfy server version / self-hosted smoke test unverified.~~ | — | **Closed 2026-11-11:** ntfy 2.28.0, health 200, publish/read-back on `kaminari-onboarding-test`. |
| R-15 | **Secrets/Keystore availability** (Robolectric skips the KeyStore round-trip; a device Keystore issue would block credentials). | High | Unit tests cover fallback behavior; verify credential save/load on hardware. |
| R-16 | **Publish ambiguity:** a timeout after the request may have reached the server; ntfy has no idempotency key, so a retry can duplicate a message. | High | Phase 4 design: `AMBIGUOUS` state, manual retry only, explicit duplicate-risk confirmation. Accepted, not eliminable client-side. |
| R-17 | ~~Real-server publish/receive unverified.~~ | — | **Closed 2026-11-11:** canonical `ntfy.rplab.qzz.io` health 200; publish/read-back verified. Old `nfty` hostname is a 502 typo, not the server. |
| R-18 | **Room v2 is forward-only:** a v1 build cannot open a v2 database; downgrade would fail. | Medium | Accepted by design (no destructive fallback). Add v3 migrations for fixes; never downgrade on a user device. Test `Migration1To2Test`. |
| R-19 | **Mock executor could be mistaken for real execution.** | Medium | Mitigated: mandatory “MOCK — no real execution” banner, `mock: true` results, no network/shell code. Real gateway is Phase 6. |
| R-20 | **Command replay/expiry bypass if a future executor trusts the app blindly.** | High (Phase 6) | Envelope requires single-use nonce, ±60 s issue window, 5 min expiry, allowlist, explicit confirmation. The gateway must re-validate independently. |
| R-21 | **Activity/outbox growth on a low-RAM device.** | Medium | Bounded queries and retention: activity 90 days/5,000 rows per profile; outbox 30 days/500 terminal entries; never prune queued work. Measure on hardware. |
| R-22 | **No automatic publish retry:** a failed publish waits for the user. | Low | Accepted Phase 4 default; automatic retry needs an idempotency design and is deferred. |
| R-23 | **Stage A migration is verified on Robolectric only**, not on a physical device with a real v1 database. | Medium | `Migration1To2Test` builds the v1 schema from `1.json` and opens v2 through Room; still add an instrumented/device migration test when hardware exists. |
| R-24 | **Schema v2 is committed before the repositories and UI exist.** A later entity change would need a v3 migration even if no user ever received v2. | Low | Accepted: v2 is additive and stable; fix forward with explicit migrations, never edit v2 or use destructive fallback. |
| R-25 | **Substring search uses SQL `LIKE`, not FTS.** Large histories may scan. | Medium | Bounded by `LIMIT`/`OFFSET`; measure on a low-end device; FTS is a future optimization, not a Stage A requirement. |
| R-26 | **Outbox/activity/command pruning could remove old terminal history** by design. | Low | Retention is 90 days/5,000 activity rows and 30 days/500 terminal outbox rows; queued work is never pruned. Documented in Settings in Stage D. |

| R-27 | **Live ntfy is unauthenticated.** Anyone with a topic name can read or publish. | High | Open owner decision. Enable auth only after updating PVE webhook, watchtower and docker-events; do not default-deny blindly. |
| R-28 | **Gateway is not deployed.** Real Proxmox views/commands are unavailable from the app. | Medium | Documented; least-privilege token ready. Deploy on an existing private host with TLS. |
| R-29 | **Release is test-only signed.** Not a production/Play Store identity. | Medium | Accepted for owner evaluation; generate a production key before distribution. |
| R-30 | **LAN HTTP origin is unusable by the app.** `http://192.168.0.104:8201` is CLI-only because the app enforces HTTPS and Android denies cleartext. | Low | Use the public HTTPS URL; do not weaken TLS for LAN convenience. |
| R-31 | **Old `nfty` hostname returns 502.** Cached configs may still point at it. | Medium | Use `ntfy.rplab.qzz.io`; update any remaining client configs. |
