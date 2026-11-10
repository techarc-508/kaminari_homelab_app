# Phase 4 Stage E Report — Typed Mock Command Centre

**Status:** Complete.
**Date:** 2026-11-10
**Checkpoint:** `5388c62`
**Previous checkpoint:** Stage D `56e68eb`
**Next gate:** Stage F requires explicit owner approval.

## Scope delivered

Stage E implements the typed, allowlisted, mock-only Command Centre. It does not
implement real PVE, SSH, shell, process, network or gateway execution. Every
result is explicitly marked `MOCK`, and no result can claim that real
infrastructure changed.

## Architecture

- `model/Command.kt`: `CommandStatus.fromPersisted` / `isTerminal`,
  `CommandResult` with strict JSON round-tripping and
  `realInfrastructureChanged = false`, and the `CommandRequest` domain view.
- `commands/CommandEnvelope.kt`: `rplab.command.v1` envelope, secure 32-byte
  URL-safe Base64 nonce, five-minute window, strict JSON parse and formatting.
- `commands/CommandPolicy.kt`: fixed six-action allowlist, exact typed argument
  validation, schema/UUID/nonce/expiry/target/profile/reply-topic checks,
  clock-skew handling and destructive confirmation binding.
- `commands/MockCommandExecutor.kt`: isolated deterministic executor with no
  Android context, HTTP client, process, shell, socket, SSH or PVE dependency.
  It accepts only the typed envelope and returns simulated `MOCK` results; it
  has no hidden real-execution path.
- `data/repository/CommandRepository.kt`: profile-scoped create, confirmation,
  rejection, expiry, retry/resume, durable execution-start claim, Activity events
  and retention.
- `data/local/CommandDao.kt`: exact profile/command/nonce confirmation and
  rejection queries, exact-boundary expiry, overdue listing and retention.
- `data/local/RetentionPolicy.kt`: command terminal retention of 90 days or 500
  rows per profile.

## Allowlisted commands

| Action | Destructive | Arguments |
|---|---|---|
| `host.health` | No | none |
| `service.status` | No | `service` |
| `storage.summary` | No | none |
| `backup.latest_status` | No | none |
| `container.status` | No | `container` |
| `service.restart` | Yes | `service` |

No arbitrary command text, shell, script or parameter is accepted. Unknown
actions, unknown arguments, malformed JSON and invalid argument values are
rejected before persistence.

## Security invariants

- Nonce: 32 random bytes, Base64 URL-safe, no wrap, no padding, 43 characters.
- Expiry: five minutes from issue; expired requests cannot execute.
- Single use: deterministic `command:<id>:execution_started` Activity event
  claims an execution across process recreation; a replayed claim is rejected.
- Profile isolation: every query and transition requires `profileId`; another
  profile cannot confirm, reject, execute or read the command.
- Destructive confirmation: `service.restart` requires the exact command ID and
  nonce; confirmation is revalidated for expiry, nonce, profile and status
  immediately before execution.
- Mock only: `MockCommandExecutor` has no process/network/PVE API and every
  result has `mock = true`, `realInfrastructureChanged = false` and a `MOCK`
  summary.
- No migration, schema change, new dependency or background timer was added.

## UI

- Seventh bottom tab: `Command`.
- Command Centre screen: allowlisted catalogue, typed parameter forms, preview,
  destructive warning, confirmation sheet bound to the nonce, status/expiry,
  mock result, Room-backed history, loading/validation/error/empty states and
  the mandatory banner `MOCK EXECUTION — NO REAL INFRASTRUCTURE CHANGES`.
- Activity timeline gains a `Commands` filter and command event types.

## Activity events

Added `COMMAND_CONFIRMATION_REQUIRED` and `COMMAND_EXECUTION_STARTED`. Existing
command events remain: `COMMAND_SUBMITTED`, `COMMAND_CONFIRMED`,
`COMMAND_RESULT`, `COMMAND_EXPIRED`, `COMMAND_REJECTED`. Events are sanitized,
profile-scoped and use deterministic IDs.

## Tests

Added 23 tests:

- `CommandPolicyTest`: 11 tests covering allowlist size, valid envelopes,
  unknown actions/arguments, missing arguments, nonce format, expiry, clock
  skew, destructive confirmation binding, JSON round trip and malformed JSON.
- `CommandRepositoryTest`: 12 tests covering non-destructive mock execution and
  Activity events, destructive confirmation and replay, wrong nonce, expiry at
  five minutes, unknown action, invalid argument, explicit rejection,
  cross-profile rejection, terminal non-reexecution and mock-only result
  wording.

## Verification

```text
./gradlew testDebugUnitTest --rerun-tasks
# exit 0 — 346 tests, 0 failures, 0 errors, 2 pre-existing skips

./gradlew assembleDebug
# exit 0 — app-debug.apk, 19,985,395 bytes

./gradlew lintDebug
# exit 0 — 0 errors, 56 warnings
```

No device/emulator or real server is available. Robolectric/JVM tests are not a
substitute for device validation.

## Open risks

- Real infrastructure mutation remains unimplemented by design; Stage F must not
  add a real executor without a separate security review.
- The durable execution-start claim prevents replayed execution, but a process
  death after the claim and before the terminal result leaves a `SUBMITTED`
  row; it is shown as pending and is not retried automatically.
- Device/emulator, real ntfy origin and hardware Keystore validation remain
  outstanding.

## Stop point

Stage E is complete. Stage F (full gate, independent QA, final Phase 4 report)
has not started and requires explicit owner approval.
