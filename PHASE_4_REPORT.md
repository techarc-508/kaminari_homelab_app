# Phase 4 Report — Compose, Activity and Command Centre

**Status:** Stage E complete. Stage F not started.
**Last updated:** 2026-11-10
**Owner authorization:** Stage A approved 2026-11-09; Stage B authorised 2026-11-10; Stage C authorised 2026-11-10; Stage D authorised 2026-11-10.
**Stage A checkpoint:** `db6e77a`. **Stage A.1:** `49e3159`. **Stage B:** `675ec14`. **Stage C:** `85186f9`. **Stage D:** `56e68eb`. **Stage E:** `5388c62`.

This is the living Phase 4 report. Stage A has its own detailed report:
`PHASE_4_STAGE_A_REPORT.md`; Stage B has `PHASE_4_STAGE_B_REPORT.md`; Stage C has
`PHASE_4_STAGE_C_REPORT.md`; Stage D has `PHASE_4_STAGE_D_REPORT.md`. Later
stages append here.

---

## Stage status

| Stage | Scope | Status |
|---|---|---|
| A | Room v2, `MIGRATION_1_2`, DAOs, schema export, migration/DAO tests | **Complete** |
| B | Shared `NtfyAuth`, `PublishValidator`, JSON publish client, MockWebServer contracts | **Complete** |
| C | `OutboxRepository`, `ActivityRepository`, receive-event transaction, manual retry, recovery, retention | **Complete** |
| D | Integrated Compose UI: six destinations, Inbox/Topics/Compose/Outbox/Activity/Settings, Saved filter, profile switch/preview | **Complete** |
| E | `rplab.command.v1` envelope, policy, command repository, `MockCommandExecutor`, CommandPanel | **Complete** |
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
- Local storage failures become typed outcomes; a claimed row stays `SENDING`
  for recovery instead of being reported as sent.
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

## Stage D outcome

- Six primary destinations are implemented and connected to the existing
  repositories: Inbox, Topics, Compose, Outbox, Activity and Settings. The
  owner's Stage D instruction supersedes the older five-destination wording;
  there is still no command tab.
- `PublishViewModel` and `PublishScreen` validate before enqueue, use the Stage C
  outbox, distinguish sending/accepted/failed/ambiguous outcomes, state that
  server acceptance is not delivery, guard duplicate taps, preserve drafts after
  failure and require confirmation before retrying an ambiguous publish.
- `OutboxScreen` is profile-scoped and supports explicit statuses, safe previews,
  attempts, failure text, manual retry and safe removal. No automatic retry or
  background resend exists.
- `ActivityViewModel`/`ActivityScreen` provide a Room-backed, profile-scoped
  timeline with All/Publish/Inbox/Attention filters and sanitized summaries.
- Inbox supports Saved as a filter, inline search, refresh, read/unread, save,
  detail and confirmed destructive actions.
- Topics lists the profile's subscriptions and supports add, subscribe,
  unsubscribe (confirmed), pause/resume and filtered-Inbox navigation. The
  current model has no priority-threshold field, so no fake control was added.
- Settings lists profiles, shows the active server, supports add/edit/select,
  safe switching, delivery modes, notification status, theme/density and local
  data actions. URL changes show the existing counts preview. Creating a new
  profile for a changed URL requires credential re-entry rather than copying a
  stored secret silently.
- No new dependency, migration, schema, command execution or binary upload was
  added. Room v2 is unchanged.

Full detail: `PHASE_4_STAGE_D_REPORT.md`.

---

## Verification (2026-11-10, Stage E)

```text
./gradlew testDebugUnitTest --rerun-tasks
# exit 0 — 346 tests, 0 failures, 0 errors, 2 pre-existing skips
#   (Stage E adds 23; Stage D added 37; Stage C added 46; Stage B added 39)

./gradlew assembleDebug
# exit 0 — app-debug.apk, 19,985,395 bytes

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

Stage D adds UI wiring only; it does not send automatically, execute commands or
call a real server. R-17 (real origin HTTP 502), the device matrix and the
absence of a real ntfy publish/receive smoke test remain open. MockWebServer
remains the network-layer evidence from Stage B. The compose draft is not
persisted through process death, and no per-topic priority threshold exists in
Room v2 — neither is hidden with mock behavior.

---

## Approval required

**Stage E is complete. Stage F has not started.**

Before Stage F:

> Review `PHASE_4_STAGE_E_REPORT.md` and authorise Stage F (full gate,
> independent QA, final Phase 4 report).

No Stage F code will be written until that approval is recorded.
