# Kaminari Nfty — Phase 4 Stage C Report

**Outbox & activity orchestration**
**Status:** Complete. Stage D not started.
**Date:** 2026-11-10
**Owner authorization:** Stage B accepted; Stage C explicitly authorized.
**Checkpoint commit:** `85186f9` — *phase-4/stage-c: harden storage-failure handling*.
**Schema:** Room v2 unchanged. No new entity, table, column or migration.

---

## 1. Implemented features

- **Durable outbox orchestration** around the existing
  `QUEUED → SENDING → ACCEPTED / FAILED / AMBIGUOUS` state machine.
- **Profile-scoped enqueue** with validation before any row is written and no
  network access.
- **Atomic claim** using the existing conditional `OutboxDao.claim`; only the
  winner calls the network.
- **Typed result mapping** from the Stage B `PublishResult` to persisted state,
  with explicit retryability.
- **Manual retry** with eligibility checks, a required “may duplicate”
  confirmation for `AMBIGUOUS`, and preservation of the original row and attempt
  history.
- **Interrupted-send recovery** that moves stale `SENDING` rows to `AMBIGUOUS`,
  never back to `QUEUED`, both at app start and before a send.
- **Room-backed activity repository** with deterministic event IDs, sanitized
  summaries and bounded retention.
- **Transactional receive events**: one `MESSAGE_RECEIVED` event is written with
  the message, and a duplicate `(profileId, messageId)` writes neither.
- **Retention integration** for terminal outbox rows and activity events using
  the existing `RetentionPolicy`.
- **Profile isolation** for every read, write, claim, retry, cancel, recovery
  and prune operation.
- **Production wiring**: `AppContainer` now gives the Stage B publisher the
  profile repository it needs, and startup recovery runs without sending.
- **No Stage D UI, no command execution, no binary uploads, no automatic
  retry/resend loop.**

## 2. Important files

### Created

| File | Purpose |
|---|---|
| `data/repository/OutboxRepository.kt` | `OutboxRepository`, `SendOutcome`, `RoomOutboxRepository`, state/result mapping, retry, recovery and pruning. |
| `data/repository/ActivityRepository.kt` | `ActivityRepository`, `RoomActivityRepository`, deterministic insertion and retention. |
| `test/.../OutboxRepositoryTest.kt` | 27 orchestration tests: transitions, retries, races, recovery, isolation, retention. |
| `test/.../ActivityRepositoryTest.kt` | 9 activity tests: sanitization, idempotency, persistence, cutoff and maximum count. |
| `test/.../MessageRepositoryActivityTest.kt` | 4 transactional receive-event tests. |
| `test/.../OutboxIntegrationTest.kt` | 4 tests through the real Stage B `NftyRepository` with per-profile configs. |

### Modified

| File | Change |
|---|---|
| `model/Outbox.kt` | Added the `OutboxEntry` domain model; `OutboxState.fromPersisted` remains the fail-closed parser. |
| `model/Activity.kt` | Added `ActivityEvent`, and `PUBLISH_STARTED` to the existing event types. |
| `data/local/ActivityDao.kt` | `insert` is idempotent (`OnConflictStrategy.IGNORE`) and returns the row ID. |
| `data/local/OutboxDao.kt` | Added the profile-scoped `getStuckSending` read used by recovery; no SQL state/prune query changed. |
| `data/repository/MessageRepository.kt` | `store` writes the message and its receive event in one Room transaction, then prunes activity best-effort. |
| `AppContainer.kt` | Constructs the activity/outbox repositories; wires `NftyRepository(profileRepository = …)`; runs startup recovery. |

No schema file changed: `app/schemas/.../2.json` is untouched.

## 3. Outbox state transition behavior

```text
enqueue                 claim
  → QUEUED ─────────────────────────────► SENDING
       ▲                                    │
       │ requeue (explicit retry)           ├─ 2xx ─────────────► ACCEPTED
       │                                    ├─ definitive 4xx ──► FAILED (retryable=false)
       └────────────────────────────────────┘ 429/5xx/connect ──► FAILED (retryable=true)
                                            └─ timeout/unknown ──► AMBIGUOUS
```

Mapping from Stage B `PublishResult`:

| Publish result | Persisted state | `retryable` | Manual retry |
|---|---|---|---|
| `Accepted(id)` | `ACCEPTED` | false | not allowed |
| `ValidationFailed` | `FAILED` | false | refused |
| `AuthenticationFailed` (401) | `FAILED` | true | allowed after fixing credentials |
| `PermissionFailed` (403) | `FAILED` | true | allowed after fixing permissions |
| `ServerError` (429/5xx) | `FAILED` | true | allowed |
| `NetworkError` with `UnknownHostException` / `ConnectException` / `SSLException` | `FAILED` | true | allowed (request never left the device) |
| `NetworkError` with timeout/other I/O | `AMBIGUOUS` | true | requires confirmation |
| `Ambiguous` | `AMBIGUOUS` | true | requires confirmation |
| Preflight validation failure | `FAILED` | true unless the stored topic is invalid | allowed after fixing config |
| Profile config missing | `FAILED` | false | refused; re-enqueue |
| Unexpected publisher exception | `AMBIGUOUS` | true | requires confirmation |

Invariants:

- Every transition is a conditional SQL update with the expected current state.
  A zero-row result produces `Busy`; nothing is published or re-recorded.
- `SENDING` is never presented as success.
- `ACCEPTED` means the server accepted the publish, **not** that a recipient
  received it.
- `attemptCount` increments only on a successful claim.
- Cancellation (`CancellationException`) is rethrown without inventing a result;
  the row stays `SENDING` until recovery.
- Local storage failures return a typed outcome instead of crashing: `enqueue`
  returns `Result.failure`, `send` returns `Ambiguous` (a claimed row stays
  `SENDING` for recovery), and `retry` returns `Failed(retryable=true)` because
  no network call happens before the requeue succeeds.
- No retry loop, scheduler or hidden background resend exists.

## 4. Manual retry and ambiguous outcomes

- `retry(profileId, clientId)` accepts only a row owned by that profile.
- `QUEUED`: delegates to `send`; the claim still guarantees one publisher.
- `FAILED` with `retryable=true`: conditional requeue, then one send.
- `FAILED` with `retryable=false`: refused with an explanation and a WARNING
  activity event.
- `AMBIGUOUS` without `allowAmbiguous=true`: returns
  `SendOutcome.ConfirmationRequired` and leaves the row untouched.
- `AMBIGUOUS` with confirmation: requeues the **same** row (the original entry
  and attempt count are preserved), records `PUBLISH_RETRY`, then sends once.
- `SENDING` returns `Busy`; `ACCEPTED` cannot be retried.
- Repeated taps cannot publish twice: the first claim wins, later attempts get
  `Busy`; retry activity uses deterministic IDs so history is not duplicated.
- `AMBIGUOUS` rows are never resent automatically. There is no code path that
  calls `send` except an explicit enqueue-then-send or retry action.

## 5. Activity and retention behavior

Events recorded by this stage:

| Type | When | Severity |
|---|---|---|
| `PUBLISH_QUEUED` | `enqueue` transaction | INFO |
| `PUBLISH_STARTED` | immediately after a successful claim | INFO |
| `PUBLISH_ACCEPTED` | terminal 2xx transition | SUCCESS |
| `PUBLISH_FAILED` | terminal definitive failure | ERROR |
| `PUBLISH_AMBIGUOUS` | uncertain send or interrupted `SENDING` recovery | WARNING |
| `PUBLISH_RETRY` | retry requested, or retry refused | INFO / WARNING |
| `MESSAGE_RECEIVED` | message + event in one transaction | INFO |

- Every event carries the profile, correlation ID (outbox `clientId`), optional
  server message ID, optional topic and a consistent `createdAt`.
- Summaries contain only the topic and a bounded reason/category. They never
  contain the message body, title, credentials, authorization headers or URLs
  with secrets. Newlines are flattened and length is capped.
- Deterministic event IDs (`outbox:<clientId>:<type>:<attempt>`) make duplicate
  transition recording idempotent. A stream replay cannot write a second receive
  event because the message insert and event share one transaction.
- History is Room-backed and survives process/repository recreation.
- Retention is applied through `RetentionPolicy`:
  - Activity: 90 days and 5,000 events per profile, pruned after each insert and
    on explicit `prune`.
  - Outbox: 30 days and 500 terminal entries per profile, pruned after a terminal
    transition and on explicit `prune`.
  - Pruning is profile-scoped and its SQL targets only terminal rows. `QUEUED`
    and `SENDING` work is never eligible. No command prune path was added in
    Stage C, so `SUBMITTED` commands are untouched.

## 6. Profile-isolation guarantees

- `observe`, `getById`, `claim`, `complete`, `requeue`, `deleteIfUnsent`,
  `recoverStuckSending` and every prune path require a `profileId`.
- `send`, `retry` and `cancel` reject blank IDs and rows owned by another
  profile with `NotFound`; they never fall back to another profile.
- The outbox row’s own `profileId` is passed to the Stage B publish entry point,
  which loads that profile’s server URL and Keystore-backed credential.
- Enqueue validates against the selected profile and refuses unknown or blank
  profiles before writing.
- Tests cover: profile-scoped observations, cross-profile `send`/`retry`
  rejection, per-profile tokens through the real `NftyRepository`, and retention
  isolation.

## 7. Test, build and lint results

Run on final code with the temporary Maven Central mirror init script (local DNS
workaround only; no project build file was changed):

```text
./gradlew testDebugUnitTest --rerun-tasks
# exit 0 — 286 tests, 0 failures, 0 errors, 2 pre-existing skips
#   Stage C adds 46: OutboxRepository 29, ActivityRepository 9,
#   MessageRepositoryActivity 4, OutboxIntegration 4

./gradlew assembleDebug
# exit 0 — app-debug.apk, 19,777,789 bytes

./gradlew lintDebug
# exit 0 — 0 errors, 56 warnings (unchanged baseline)
```

Git at verification time: `e950b51` (Stage B docs), clean working tree before
Stage C edits. Room schema `2.json` unchanged.

## 8. Known limitations and remaining risks

- **No UI or background sender yet.** Stage C exposes `enqueue`/`send`/`retry`
  but nothing calls them from a screen, and no WorkManager/scheduler sends
  `QUEUED` work automatically. That is Stage D by design.
- **No real-server or device verification.** Outbox and activity tests use an
  in-memory Room database and a recording `NtfyPublish`; the integration test
  runs the real Stage B `NftyRepository` but not HTTP. Stage B’s MockWebServer
  evidence remains the network-layer evidence. R-17 (ntfy origin health) and the
  hardware matrix stay open.
- **Recovery is not immediate.** A killed process leaves `SENDING` until app
  start or the next explicit send/recovery pass and the 2-minute cutoff. This is
  intentional: the outcome may be a server-accepted publish.
- **`ACCEPTED` is not delivery.** ntfy provides no recipient delivery receipt or
  idempotency key; retrying an `AMBIGUOUS` row can duplicate a publish, which is
  why confirmation is mandatory.
- **Non-retryable failures require a new entry.** The outbox row is immutable
  apart from state transitions, so invalid input cannot be edited in place.
- **Server error text is stored, bounded and flattened.** It is not rendered in
  Stage C; Stage D must treat it as plain text, never HTML.
- **Command lifecycle events and command retention are not implemented.** They
  belong to Stage E and were deliberately not fabricated here.
- Startup recovery is best-effort. If it fails, rows remain `SENDING`; the next
  explicit send/recovery pass retries the same non-network transition.

## 9. Checkpoint

Stage C code, tests and documentation are checkpointed in `85186f9`.
This report's hash update is a follow-up documentation commit. Stage D has not
been started.
