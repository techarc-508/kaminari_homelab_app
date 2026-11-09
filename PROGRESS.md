# Progress — Kaminari Nfty V1

**Last updated:** 2026-11-09
**Repository:** `KaminariNfty_V1_Plan/repo/kaminari_homelab_app-main`
**Branch:** `main`
**HEAD before Phase 3 checkpoint:** `5d00827` — Phase 2 remediation
**Phase 3 checkpoint:** `72dbf36`
**Phase 4:** Stage A complete (Room v2); Stages B–F require explicit owner approval.

---

## Phase status

| Phase | Status | Evidence |
|---|---|---|
| Phase 0 — reproducible baseline / audit | Complete | `AUDIT_REPORT.md`, `CURRENT_ARCHITECTURE.md`, `API_CAPABILITY_MATRIX.md`, `PHASE_0_EXECUTION_PLAN.md`, `PHASE_0_REPORT.md` |
| Phase 1 — architecture and data foundation | Complete, approved | `PHASE_1_REPORT.md`; Room/DataStore/Keystore, demo isolation |
| Phase 2 — ntfy API correctness | Complete, locally verified; real-server smoke test still valuable | `PHASE_2_REPORT.md`, `PHASE_2_GATE_REPORT.md`; commit `5d00827` |
| Phase 3 — notification and background delivery | **ACCEPTED — PASS WITH CONDITIONS** (owner, 2026-11-09) | `PHASE_3_REPORT.md`, `PHASE_3_AUDIT_REPORT.md`, `PHASE_3_REMEDIATION_REPORT.md`, `PHASE_3_FINAL_AUDIT.md`, `PHASE_3_ACCEPTANCE.md` |
| Phase 4 — Compose/activity/command centre | **Stage A complete; Stages B–F pending approval** | `PHASE_4_ARCHITECTURE.md`, `PHASE_4_IMPLEMENTATION_PLAN.md`, `PHASE_4_STAGE_A_REPORT.md` |
| Phase 5 — low-end/E-Ink | Not started | Generic E-Ink only until the Boox model/firmware is known |
| Phase 6 — administration/security | Not started | ntfy admin + separate trusted PVE gateway |
| Phase 7 — release/QA | Not started | Signed `dist/KaminariNfty-v1.0.0.apk` is the final gate |

## Phase 3 acceptance

- The original independent audit returned **FAIL**; remediation fixed A-01…A-04;
  the final audit returned **PASS WITH CONDITIONS**; the owner **accepted** it.
- History is preserved in the audit/remediation/final-audit reports; it is not
  rewritten to look successful.
- Conditions remain open: no device/emulator validation; Doze, reboot, OEM
  battery managers, notification rendering and the real Android 15 `dataSync`
  timeout are unobserved; real ntfy smoke test blocked by HTTP 502; Keystore
  round-trip unverified on hardware.

## Phase 3 remediation outcome

- Android 15+ `dataSync` timeout handled (`onTimeout`), with Battery Saver
  fallback and no crash path.
- The foreground service stops on terminal auth/forbidden/retry-exhaustion
  failures and never keeps a "live delivery" notification over a dead stream.
- Instant connection status is owned by the service and surfaced truthfully in
  the UI; the ViewModel does not open a second stream.
- Cold-start notification deep links load the target from Room by ID.
- Delivery modes are exactly Instant and Battery Saver; legacy `SAVED`
  selections migrate to Battery Saver; fresh installs default to Battery Saver.
- The top-bar clock fix is preserved with a real regression test.

## Verification (final, each task run separately with `--rerun-tasks`)

```text
./gradlew testDebugUnitTest   # exit 0 — 173 tests, 0 failures, 0 errors, 2 pre-existing skips
./gradlew assembleDebug       # exit 0 — app-debug.apk, 19,557,690 bytes
./gradlew lint                # exit 0 — 0 errors, 56 warnings
```

No device or emulator is available (`adb` not installed); hardware validation
remains outstanding.

## Phase 4 Stage A — Room v2 and persistence foundation (2026-11-09)

- Added three additive tables: `outbox_entries`, `activity_events`, `command_requests`.
- `MIGRATION_1_2` is explicit and additive; no destructive fallback exists.
- Existing profiles, topic subscriptions, messages, saved state and profile isolation are preserved.
- Schema export enabled: `app/schemas/.../1.json` (v1) and `2.json` (v2).
- `Migration1To2Test` builds a real v1 database from `1.json`, inserts a profile/topic/message, opens Room at v2 with `MIGRATION_1_2`, and verifies preservation, new tables and FK cascade.
- DAO tests cover profile-scoped queries, conditional outbox transitions, nonce uniqueness, command expiry and bounded retention.
- No Stage B publish client, UI, activity screen or command execution was started.

**Stage A verification (2026-11-09):**

```text
./gradlew :app:testDebugUnitTest --tests 'com.rplab.kaminarinfty.data.local.*'  # exit 0 — 36 tests
./gradlew testDebugUnitTest                                                     # exit 0 — 194 tests, 0 failures, 2 pre-existing skips
./gradlew assembleDebug                                                         # exit 0 — app-debug.apk, 19,648,407 bytes
./gradlew lintDebug                                                             # exit 0 — 0 errors, 56 warnings
```


## Phase 4 — Stage B (2026-11-10)

**Status: Complete.** Publish-client foundation only; Stage C not started.

- Added `data/api/NtfyAuth.kt` (guest/Basic/Bearer, CR/LF sanitisation). The
  stream client now shares it, so receive and publish authenticate identically.
- Added `data/api/PublishValidator.kt`: topic charset, non-blank message,
  priority 1–5, tags, absolute http(s) click/attach URLs, delay duration or Unix
  timestamp, server URL and credential presence — all before any network call.
- Added `NtfyEndpoint.publishUrl(base, topic, requireHttps)`.
- Added `NtfyPublishClient` (`POST {base}/{topic}`; plain text for message-only,
  JSON for metadata; parses the server id) with bounded connect/read/write/call
  timeouts, no retries and no automatic resend.
- Typed `PublishResult`: `Accepted` (server accepted; **not** delivered),
  `ValidationFailed`, `AuthenticationFailed` (401), `PermissionFailed` (403),
  `ServerError` (429/5xx), `NetworkError` (definite transport failure) and
  `Ambiguous` (must not be auto-resent).
- `NftyRepository.publish(profileId, request)` resolves the config through
  `ProfileRepository.loadConfig(profileId)`; blank/unknown profile is rejected
  with no fallback, so credentials cannot cross profiles.
- No database, entity, migration, delivery-mode, UI or navigation change.
  Credentials stay in the Keystore-backed `SecretStore`.

**Tests added:** 39 — `NtfyAuthTest` 6, `PublishValidatorTest` 11,
`NtfyPublishClientTest` 17 (MockWebServer), `NftyRepositoryPublishTest` 5.

**Verified (final code):**

```text
./gradlew testDebugUnitTest  # exit 0 — 240 tests, 0 failures, 2 pre-existing skips
./gradlew assembleDebug      # exit 0 — app-debug.apk 19,720,707 bytes
./gradlew lintDebug          # exit 0 — 0 errors, 56 warnings
```

Real-server publish remains unverified (origin previously HTTP 502); evidence is
MockWebServer only. No device available. See `PHASE_4_STAGE_B_REPORT.md`.

## Documentation authority

- `PLAN.md` in the repository root is authoritative; the plan-folder copy is a
  mirror.
- Phase 1/2 reports are mirrored into the repository root so the project record
  is self-contained.
- `OPENCODE_TAKEOVER_REPORT.md` records the independent Phase 3 verification and
  the six discrepancies found during takeover.

## Git

- Phase 1 and Phase 2 commits are preserved (`f254cd6`, `e6cdba5`, `5d00827`).
- Phase 3 is checkpointed in `72dbf36`.
- The handoff is `2fe0ce6`; the takeover report and documentation reconciliation
  are committed in `93c34d1`; the hygiene fix is `bc6b6cd`; decision cleanup is
  `4140ca0`.
- Stage A is checkpointed in `db6e77a`; Stage A.1 in `49e3159`; Stage B in
  `675ec14` (see `PHASE_4_STAGE_B_REPORT.md`).
- Never reset/clean the repository; do not rewrite history.

## Next

1. Owner review of `PHASE_4_STAGE_B_REPORT.md` and the Stage B diff.
2. Owner approval before Stage C (outbox/activity/command repositories and the
   receive-event transaction).
3. Device test matrix in `PHASE_3_REPORT.md` §6 remains owner/hardware-gated.
4. Real publish smoke test once the ntfy origin is healthy (R-17).
