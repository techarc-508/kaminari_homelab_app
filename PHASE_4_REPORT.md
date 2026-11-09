# Phase 4 Report — Compose, Activity and Command Centre

**Status:** Stage C complete. Stages D–F not started.
**Last updated:** 2026-11-10
**Owner authorization:** Stage A approved 2026-11-09; Stage B authorised 2026-11-10; Stage C authorised 2026-11-10.
**Stage A checkpoint:** `db6e77a`. **Stage A.1:** `49e3159`. **Stage B:** `675ec14`.

This is the living Phase 4 report. Stage A has its own detailed report:
`PHASE_4_STAGE_A_REPORT.md`; Stage B has `PHASE_4_STAGE_B_REPORT.md`; Stage C has
`PHASE_4_STAGE_C_REPORT.md`. Later stages append here.

---

## Stage status

| Stage | Scope | Status |
|---|---|---|
| A | Room v2, `MIGRATION_1_2`, DAOs, schema export, migration/DAO tests | **Complete** |
| B | Shared `NtfyAuth`, `PublishValidator`, JSON publish client, MockWebServer contracts | **Complete** |
| C | `OutboxRepository`, `ActivityRepository`, receive-event transaction, manual retry, recovery, retention | **Complete** |
| D | Publish/Activity/Command ViewModels, five destinations, Saved filter, profile switch/preview | Not started |
| E | `rplab.command.v1` envelope, policy, command repository, `MockCommandExecutor`, CommandPanel | Not started |
| F | Full gate, independent QA, final Phase 4 report | Not started |

---

## Stage A outcome

- Room v2 adds `outbox_entries`, `activity_events` and `command_requests`.
- Existing profiles, topics, messages, saved state and profile isolation are
  unchanged.
- `MIGRATION_1_2` is explicit and additive; no destructive fallback.
- Schemas `1.json` and `2.json` are exported and committed.
- `Migration1To2Test` builds a real v1 database from `1.json`, inserts a profile,
  topic and saved message, opens Room at v2, and verifies preservation plus new
  table behavior.
- DAO tests cover outbox transitions, command nonce/expiry, activity bounds,
  search/saved/unread and profile scoping.
- No Stage B publish client, UI, activity screen or command execution exists.

Detailed schema, DAO behavior, file list and risks: `PHASE_4_STAGE_A_REPORT.md`.

---

## Stage B outcome

- `NtfyAuth` is the single auth implementation for receive and publish: guest
  sends no header, Basic uses `Credentials.basic`, Bearer sends the token; CR/LF
  are stripped; credentials only ever travel in the `Authorization` header.
- `PublishValidator` rejects invalid topic/message/priority/tags/click/attach/
  delay/server-URL/credentials before any network call.
- `NtfyEndpoint.publishUrl` builds `POST {base}/{topic}` from validated input.
- `NtfyPublishClient` sends plain text when only a message is present and the
  documented JSON body otherwise; parses the server message id; uses bounded
  connect/read/write/call timeouts; never retries and never auto-resends.
- Typed outcomes distinguish validation, authentication (401), permission (403),
  server (429/5xx), definite network failure and ambiguous results. `Accepted`
  means the server accepted the publish, not that anyone received it.
- `NftyRepository.publish(profileId, request)` resolves config through
  `ProfileRepository.loadConfig(profileId)`, so the profile's server URL and
  SecretStore credential are used with no cross-profile fallback.
- No database or entity change: credentials remain in the Keystore-backed
  `SecretStore`.
- No Stage C outbox/activity/command code, no Compose UI, no binary uploads.

Full detail: `PHASE_4_STAGE_B_REPORT.md`.

---

## Stage C outcome

- `OutboxRepository` wraps the existing conditional DAO transitions with
  validated enqueue, atomic claim, typed Stage B result mapping, manual retry,
  stale-`SENDING` recovery and retention. No automatic retry or resend exists.
- `Accepted` maps to `ACCEPTED`; definitive rejections to
  `FAILED(retryable=false)`; auth, permission, 429/5xx and definite connect
  failures to `FAILED(retryable=true)`; timeouts, ambiguous and unexpected
  outcomes to `AMBIGUOUS`.
- `ACCEPTED` means the server accepted the publish, not that a recipient
  received it.
- Retrying `AMBIGUOUS` requires an explicit confirmation and reuses the original
  row, preserving its attempt history. Non-retryable failures are refused.
- `SENDING` rows older than two minutes become `AMBIGUOUS`, never `QUEUED`.
- `ActivityRepository` records queued, started, accepted, failed, ambiguous and
  retry events with deterministic IDs and sanitized summaries; message bodies,
  titles and credentials never enter the timeline.
- `MessageRepository.store` writes the message and one `MESSAGE_RECEIVED` event
  in one transaction; a duplicate message writes no event.
- Retention is applied through `RetentionPolicy`: activity 90 days / 5,000 per
  profile; outbox 30 days / 500 terminal rows per profile. Active work is never
  pruned.
- Every operation is profile-scoped; no config or credential fallback exists.
- No database/entity/migration change, no UI, no background sender, no command
  execution and no binary uploads.

Full detail: `PHASE_4_STAGE_C_REPORT.md`.


---

## Verification (2026-11-10)

```text
./gradlew :app:testDebugUnitTest --tests 'com.rplab.kaminarinfty.data.local.*'
# exit 0 — 36 tests, 0 failures, 0 errors, 0 skipped

./gradlew testDebugUnitTest --rerun-tasks
# exit 0 — 284 tests, 0 failures, 0 errors, 2 pre-existing skips
#   (Stage C adds 44: OutboxRepository 27, ActivityRepository 9,
#    MessageRepositoryActivity 4, OutboxIntegration 4; Stage B added 39)

./gradlew assembleDebug
# exit 0 — app-debug.apk, 19,720,707 bytes

./gradlew lintDebug
# exit 0 — 0 errors, 56 warnings
```

No physical device or emulator is available. Robolectric/JVM tests are not a
substitute for device validation; see R-23.

---

## Open risks carried forward

| ID | Risk | State |
|---|---|---|
| R-17 | Real ntfy origin returned HTTP 502; real publish/receive unverified. | Open, external |
| R-18 | Room v2 is forward-only. | Accepted by design |
| R-23 | Migration verified on Robolectric only. | Open, device-gated |
| R-25 | `LIKE` search is not FTS. | Accepted, measure on device |
| R-26 | Pruning removes old terminal history by design. | Accepted, documented |

Stage C adds orchestration only; it does not send automatically and does not
call a real server. R-17 (real origin HTTP 502), the device matrix and the
absence of a real ntfy publish/receive smoke test remain open. MockWebServer
remains the network-layer evidence from Stage B.

---

## Approval required

**Stage C is complete. Stage D has not started.**

Before Stage D:

> Review `PHASE_4_STAGE_C_REPORT.md` and authorise Stage D (publish, activity and
> command ViewModels; five-destination navigation; Saved filter; profile
> switching and URL-change preview).

No Stage D code will be written until that approval is recorded.
