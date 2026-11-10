# Phase 4 Stage F Report — Final Integration, Security & Regression Gate

**Status:** Complete — **PASS WITH CONDITIONS**.
**Date:** 2026-11-10
**Stage F code checkpoint:** `a0f753c`
**Previous checkpoint:** Stage E `5388c62`
**Phase 4 overall verdict:** PASS WITH CONDITIONS.

## 1. Overall Phase 4 verdict

Phase 4 is complete. The local automated gate passes: 348 unit tests, 0 failures,
0 errors, 2 pre-existing skips; a successful debug build of 20,105,707 bytes;
and lint with 0 errors and 56 warnings.

The two open conditions are unchanged from earlier phases and are not code
defects found by this gate:

- The owner's ntfy origin remains blocked by the recorded HTTP 502, so no real
  server publish/receive round trip was claimed.
- No device, emulator, Doze, OEM, reboot, foreground-service hardware or
  hardware-Keystore validation was performed.

No real PVE, SSH, shell, process, network or gateway command was executed. The
Command Centre remains mock-only.

## 2. Summary of Stages A–F

| Stage | Scope | Status |
|---|---|---|
| A | Room v2, `MIGRATION_1_2`, DAOs, schema export, migration/DAO tests | Complete |
| B | Shared `NtfyAuth`, `PublishValidator`, JSON publish client, MockWebServer contracts | Complete |
| C | `OutboxRepository`, `ActivityRepository`, receive-event transaction, manual retry, recovery, retention | Complete |
| D | Integrated Compose UI: destinations, Inbox/Topics/Compose/Outbox/Activity/Settings, Saved filter, profile switch/preview | Complete |
| E | `rplab.command.v1` envelope, policy, command repository, `MockCommandExecutor`, CommandPanel | Complete |
| F | Full gate, independent audit, corrective hardening, final Phase 4 report | Complete |

## 3. End-to-end integration results

Verified by automated tests and source inspection:

- Room persistence and profile isolation: every message, topic, outbox, activity
  and command query is profile-scoped; `Migration1To2Test` verifies additive
  migration and preservation.
- ntfy receive and publish: stream parsing, reconnect, catch-up, publish
  outcomes and profile-scoped credentials are covered by MockWebServer and
  repository tests.
- Authentication: guest sends no header; Basic uses `Credentials.basic`; Bearer
  uses the token; CR/LF stripping is enforced; credentials are not placed in
  URLs, logs, Activity or UI state.
- Delivery: Instant mode owns the live SSE connection through the service;
  Battery Saver uses WorkManager; Room deduplication prevents duplicate
  notifications; delivery mode switching persists.
- Inbox, Topics, Compose, Outbox, Activity, Settings and Command Centre use the
  intended repositories and profile-aware state.
- Outbox lifecycle `QUEUED → SENDING → ACCEPTED / FAILED / AMBIGUOUS` is
  implemented with conditional SQL transitions, manual retry only, ambiguous
  retry confirmation and stuck-`SENDING` recovery.
- Command Centre: six approved typed actions, strict envelope validation, secure
  nonce, five-minute expiry, destructive confirmation bound to request and
  nonce, terminal/non-execution guarantees and mock results.
- Retention: active work is never pruned; terminal rows are bounded by age and
  count per profile.

## 4. Security and profile-isolation results

- No arbitrary command text, shell, SQL, SSH, Proxmox API or process execution
  exists in the app.
- `MockCommandExecutor` has no Android context, HTTP client, process, shell,
  socket, SSH or PVE dependency.
- Every command result is marked `MOCK`, `simulated = true` and
  `realInfrastructureChanged = false`.
- Publish and stream authentication share `NtfyAuth`; credentials never enter
  URLs or activity summaries.
- Publish redirects and hidden connection retries are disabled to prevent
  duplicate sends.
- Profile switching clears the in-memory window and never deletes another
  profile's data; sends use the originating entry's profile.

## 5. Command Centre safety findings

- Approved actions only: `host.health`, `service.status`, `storage.summary`,
  `backup.latest_status`, `container.status`, `service.restart`.
- Unknown actions, unknown arguments, malformed JSON, invalid argument values,
  non-allowlisted targets and over-long fields are rejected before persistence.
- Nonces are 32 random bytes in URL-safe Base64 without padding (43 chars) and
  are single-use through a unique database constraint.
- Five-minute expiry is enforced at creation and immediately before execution.
- `service.restart` requires confirmation bound to the exact command ID and
  nonce; confirmation is revalidated before execution.
- Expired, rejected and terminal requests cannot execute.
- Concurrent attempts cannot execute the same request twice: the deterministic
  `command:<id>:execution_started` Activity event is inserted with
  `OnConflictStrategy.IGNORE`; a second claim observes `-1` and is rejected.
- All operations are profile-scoped.

## 6. Test totals, failures, errors and skips

```text
./gradlew testDebugUnitTest --rerun-tasks
# exit 0 — 348 tests, 0 failures, 0 errors, 2 pre-existing skips

./gradlew assembleDebug
# exit 0 — app-debug.apk, 20,105,707 bytes

./gradlew lintDebug
# exit 0 — 0 errors, 56 warnings
```

The two skips are pre-existing and unrelated to Phase 4.

## 7. Build result and APK size

- Build: `assembleDebug` successful.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 20,105,707 bytes.
- No release signing, R8 or production deployment was performed.

## 8. Lint errors and warnings

- Errors: 0.
- Warnings: 56.
- No lint suppression was added to make the gate pass.

## 9. Git HEAD and working-tree status

- Stage F code checkpoint: `a0f753c`.
- Working tree: clean after the documentation checkpoint.
- Earlier stage checkpoints are preserved.

## 10. Corrective changes and regression tests

1. `NtfyPublishClient`: default OkHttp client now disables `followRedirects`,
   `followSslRedirects` and `retryOnConnectionFailure`. This prevents a hidden
   redirect or connection retry from replaying a publish. Regression test:
   `NtfyPublishClientTest` “redirects are not followed to avoid duplicate
   sends”.
2. `CommandDao.expireOverdue`: changed the boundary from `expiresAt < :now` to
   `expiresAt <= :now`, matching `expireOne`, `getOverdueSubmitted` and the
   policy. Regression test: `CommandDaoTest` “expireOverdue expires at the
   five-minute boundary”.

No schema, migration, dependency or architecture change was required.

## 11. Open risks and unverified hardware/server behavior

- Real ntfy server communication is unverified because the origin returned HTTP
  502.
- Physical Android device behavior, Doze, OEM background restrictions, reboot,
  foreground-service behavior and hardware Keystore are unverified.
- Real notification delivery and attachment fetching are unverified on hardware.
- Release signing and distribution are out of scope.

## 12. Remaining work before production release

- Restore the ntfy origin and run a real publish/receive smoke test.
- Complete the device/emulator matrix and hardware validation.
- Phase 5: E-Ink and low-resource profiles.
- Phase 6: ntfy administration and the separately deployed PVE command gateway.
- Phase 7: release candidate, signing, checksums and final operator guide.

## 13. Explicit confirmation

No real PVE commands were executed. No SSH, shell, process, Proxmox API or
external command execution exists in the application. The Command Centre is
mock-only and every result is explicitly marked `MOCK`.
