# Phase 4 Architecture — Compose, Activity and Command Centre

**Status:** proposed; Phase 4 is **not authorised** until the owner approves this
document and `PHASE_4_IMPLEMENTATION_PLAN.md`.

**Baseline:** Phase 3 accepted as PASS WITH CONDITIONS (`PHASE_3_ACCEPTANCE.md`),
checkpoint `72dbf36`, HEAD `2fe0ce6`.

**Scope in one sentence:** add a real publish path with a durable outbox, a
persistent activity timeline, and a typed command envelope with a **mock**
executor — reusing the Phase 1–3 repositories, Room database, delivery modes and
profile model.

---

## 1. Non-goals (explicitly out of Phase 4)

| Not built in Phase 4 | Phase |
|---|---|
| Binary attachment upload (files, camera, share sheet uploads) | later, separately scoped |
| Automatic background publish retries | later, only with an idempotency design |
| Real PVE / homelab command execution, gateway deployment, credentials | Phase 6 |
| ntfy user/ACL/token administration | Phase 6 |
| E-Ink profile and Boox tuning | Phase 5 |
| FCM / UnifiedPush | optional, not scheduled |
| Notification actions (mark read/save from the shade) | deferred by D-05 |
| Release signing, APK publication, R8 | Phase 7 |

Phase 4 must not weaken Phase 3 delivery ownership, introduce a second stream,
or add a destructive migration.

**External blocker:** the owner's ntfy origin returned HTTP 502 on 2026-11-08
(DNS/TLS fine). Phase 4 contract tests therefore use MockWebServer. A real-server
publish round trip is a release condition, not a Phase 4 coding gate; it cannot
be claimed until the origin is up and `ntfy version` is pinned.

## 2. What is reused (do not rebuild)

| Existing component | Reuse |
|---|---|
| `KaminariDatabase` (v1) | Extend to v2; same profile/topic/message tables |
| `MessageRepository` / `MessageDao` | Source of truth for inbox, search, saved, unread |
| `ProfileRepository` / `SecretStore` | Profiles, active profile, encrypted credentials |
| `AppPreferences` (DataStore) | Active profile, delivery mode, theme, density |
| `NftyStreamClient` / `NftyRepository` | Receive path unchanged |
| `InstantForegroundService` / `BatterySaverWorker` | Delivery unchanged |
| `MainViewModel` + `MainActivity` | Navigation/state host; extended, not replaced |
| Manual DI (`AppContainer`) | New repositories/clients constructed once per process |

No Hilt, no Paging 3, no new JSON library, no new HTTP client. `org.json` and
OkHttp already ship with the app.

## 3. Boundary map

```text
Compose UI
  PublishScreen / ActivityScreen / CommandPanel / Inbox / Topics / Settings
        │
        ▼
ViewModels (UI state + intent only)
  PublishViewModel / ActivityViewModel / CommandViewModel / MainViewModel
        │
        ▼
Repositories (interfaces; Room + network orchestration)
  OutboxRepository ──► PublishClient (OkHttp, documented ntfy JSON publish)
                   ──► ProfileRepository (config + secret)
                   ──► ActivityRepository (events)
  ActivityRepository ─► ActivityDao
  CommandRepository ──► CommandDao + CommandExecutor
                   ──► ActivityRepository
  MessageRepository ──► MessageDao + ActivityDao (receive event, same transaction)
        │
        ▼
Data
  Room v2: messages, topics, profiles, outbox_entries, activity_events,
           command_requests
  DataStore: non-secret preferences
  KeystoreSecretStore: credentials only
```

Rules:

- Composables never call OkHttp, Room or WorkManager directly.
- Repositories own transactions and state transitions; ViewModels own form and
  screen state.
- No repository holds a global mutable scope. Suspend calls run on the caller's
  dispatcher; long-lived flows come from Room.
- The receive stream still has exactly one owner per delivery mode (Phase 3
  invariant). Phase 4 adds no stream.

## 4. Room v2

Version 1 stays untouched. Version 2 adds three tables and no column changes to
existing tables. No destructive migration; no fallback.

### 4.1 `outbox_entries`

| Column | Type | Notes |
|---|---|---|
| `clientId` | TEXT PK | UUID generated before enqueue; stable local correlation |
| `profileId` | TEXT NOT NULL | FK `server_profiles.id`, CASCADE |
| `topic` | TEXT NOT NULL | validated ntfy topic |
| `title` | TEXT | optional, ≤ 256 chars |
| `body` | TEXT NOT NULL | message text; UTF-8 ≤ 4096 bytes |
| `priority` | INTEGER NOT NULL | 1–5 |
| `tagsJson` | TEXT NOT NULL | JSON array, ≤ 10 tags |
| `clickUrl` | TEXT | optional, http/https only |
| `attachmentUrl` | TEXT | optional, http/https URL; the server fetches it. Binary upload is deferred |
| `actionsJson` | TEXT | optional, `view` actions only |
| `delay` | TEXT | optional, e.g. `5m`; server-dependent |
| `state` | TEXT NOT NULL | `QUEUED`, `SENDING`, `ACCEPTED`, `FAILED`, `AMBIGUOUS` |
| `attemptCount` | INTEGER NOT NULL DEFAULT 0 | incremented on claim |
| `retryable` | INTEGER NOT NULL DEFAULT 0 | meaningful for `FAILED` |
| `lastError` | TEXT | sanitized category/message; never a body or token |
| `serverMessageId` | TEXT | parsed from accepted response when present |
| `createdAt` / `updatedAt` | INTEGER NOT NULL | epoch millis |
| `acceptedAt` | INTEGER | nullable |

Indices: `(profileId, createdAt)`, `(profileId, state)`.
No unique constraint on `serverMessageId`: the server ID is informational; the
local `clientId` is the durable identity.

### 4.2 `activity_events`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | UUID |
| `profileId` | TEXT NOT NULL | FK CASCADE |
| `type` | TEXT NOT NULL | enum name |
| `severity` | TEXT NOT NULL | `INFO`, `SUCCESS`, `WARNING`, `ERROR` |
| `summary` | TEXT NOT NULL | short, sanitized; **no message body** |
| `correlationId` | TEXT | outbox `clientId` or command ID |
| `messageId` | TEXT | ntfy message ID for receive events |
| `topic` | TEXT | optional |
| `createdAt` | INTEGER NOT NULL | epoch millis |

Indices: `(profileId, createdAt)`, `(correlationId)`.

Types recorded in Phase 4:

- `MESSAGE_RECEIVED` — one per genuinely new stored message; inserted in the same
  transaction as the message row, so duplicates never create events.
- `PUBLISH_QUEUED`, `PUBLISH_ACCEPTED`, `PUBLISH_FAILED`, `PUBLISH_AMBIGUOUS`,
  `PUBLISH_RETRY`.
- `COMMAND_SUBMITTED`, `COMMAND_CONFIRMED`, `COMMAND_RESULT`,
  `COMMAND_EXPIRED`, `COMMAND_REJECTED`.

Delivery of a message to Android's notification shade is **not** a server read
receipt and is not recorded as one. `PUBLISH_ACCEPTED` means the server accepted
the publish request; it does not mean a handset displayed it.

### 4.3 `command_requests`

| Column | Type | Notes |
|---|---|---|
| `commandId` | TEXT PK | UUID |
| `profileId` | TEXT NOT NULL | FK CASCADE |
| `actionId` | TEXT NOT NULL | allowlisted action |
| `target` | TEXT NOT NULL | `mock-pve` in Phase 4 |
| `argsJson` | TEXT NOT NULL | validated, action-specific |
| `nonce` | TEXT NOT NULL | unique index; replay protection |
| `issuedAt` / `expiresAt` | INTEGER NOT NULL | epoch millis; max 5 min window |
| `requiresConfirmation` | INTEGER NOT NULL | must match policy |
| `confirmedAt` | INTEGER | required for destructive actions |
| `status` | TEXT NOT NULL | `SUBMITTED`, `COMPLETED`, `FAILED`, `EXPIRED`, `REJECTED` |
| `resultJson` / `resultSummary` | TEXT | mock result; sanitized |
| `createdAt` / `updatedAt` | INTEGER NOT NULL | epoch millis |

Indices: `(profileId, createdAt)`, unique `(nonce)`.

### 4.4 Migration and schema export

- `KaminariDatabase` becomes `version = 2`, `exportSchema = true`.
- Add KSP arg `room.schemaLocation = "$projectDir/schemas"`.
- **Freeze the v1 schema before changing entities:** with version still 1 and
  export enabled, run `:app:kspDebugKotlin --rerun-tasks`, commit the generated
  `app/schemas/.../1.json`. Then add v2 entities, bump to 2, generate and commit
  `2.json`.
- Migration `MIGRATION_1_2` creates the three tables and indices with SQL copied
  from `2.json` (Room validates the identity hash and column definitions).
- No `fallbackToDestructiveMigration`.
- Add test-only `androidx.room:room-testing` (same Room version) and a
  `Migration1To2Test`. If `MigrationTestHelper` cannot run under Robolectric on
  this host, keep the test in `androidTest` and report it as **not run** until a
  device exists; never mark it passed.

### 4.5 New DAO queries (existing tables)

- `MessageDao.search(profileId, query, limit, offset)` — `LIKE` over title, body
  and topic, bounded; no FTS in Phase 4.
- `MessageDao.observeSaved(profileId, limit)` — saved filter.
- `MessageDao.unreadCounts(profileId)` — `GROUP BY topic` flow for Topics.
- `TopicSubscriptionDao.count(profileId)` and `OutboxDao.count(profileId)` — for
  the profile-change preview.

## 5. Publish client and outbox

### 5.1 Transport choice

Use the documented **JSON publish** form:

```http
POST {base}/
Content-Type: application/json; charset=utf-8
Authorization: Basic … | Bearer …
```

```json
{
  "topic": "ops",
  "message": "Backup finished",
  "title": "Nightly backup",
  "priority": 4,
  "tags": ["floppy_disk", "white_check_mark"],
  "click": "https://example.invalid/run/42",
  "attach": "https://example.invalid/files/backup.log",
  "actions": [{"action": "view", "label": "Open", "url": "https://example.invalid/run/42"}],
  "delay": "5m"
}
```

Attachment scope: `attach` accepts an **http/https URL** only; the ntfy server
fetches it and derives the filename/type/size metadata. The app does not upload
binary data, does not read local files, and never base64-encodes content in
Phase 4.

Why JSON rather than `POST /{topic}` headers:

- ntfy documents UTF-8 titles and tag values; JSON avoids OkHttp header
  encoding ambiguity (`addUnsafeNonAscii`) and header-injection classes.
- Actions are structured JSON instead of a serialized header.
- The body is one validated payload, easy to size-limit and test.

`POST /{topic}` remains a documented alternative; it is not used in Phase 4.

`NtfyEndpoint.publishJsonUrl(rawBaseUrl, requireHttps)` reuses
`normalizeBaseUrl` and returns the root URL. Credentials never enter the URL.

### 5.2 Client contract

```kotlin
// data/api/PublishClient.kt
data class PublishRequest(
    val topic: String,
    val title: String?,
    val body: String,
    val priority: NftyPriority,
    val tags: List<String>,
    val clickUrl: String?,
    val attachmentUrl: String?,
    val viewActions: List<NftyAction>,
    val delay: String?,
)

sealed interface PublishOutcome {
    data class Accepted(val serverMessageId: String?, val httpCode: Int) : PublishOutcome
    data class Rejected(
        val category: NftyErrorCategory,
        val message: String,
        val retryable: Boolean,
    ) : PublishOutcome
    data class Ambiguous(
        val category: NftyErrorCategory,
        val message: String,
    ) : PublishOutcome
}

interface PublishClient {
    suspend fun publish(config: ServerConfig, request: PublishRequest): PublishOutcome
}
```

`NtfyPublishClient`:

- OkHttp client: connect 10 s, read 15 s, write 15 s, call 30 s,
  `retryOnConnectionFailure(false)`, redirects disabled. A hidden automatic
  retry or a replayed 307 is a duplicate-send risk; both are refused.
- Auth: extract the existing private `applyAuthHeaders`/`sanitizeHeaderValue`
  from `NftyStreamClient` into `data/api/NtfyAuth.kt`; stream and publish share
  it. No duplicated auth logic, no behavior change to the stream.
- Response:
  - 2xx → `Accepted(id = json.id)`; a malformed success body still counts as
    accepted, with `serverMessageId = null`.
  - 400/404/405/413/415/422 → `Rejected(retryable = false)`.
  - 401/403 → `Rejected(retryable = false)` with `AUTH`/`FORBIDDEN`; the UI tells
    the user to fix credentials/permissions.
  - 429 and 5xx → `Rejected(retryable = true)`; `Retry-After` is read only for
    the message text, not auto-scheduled.
  - `UnknownHostException`, `ConnectException`, `SSLException` →
    `Rejected(retryable = true)` — the request was not sent.
  - `SocketTimeoutException` and other I/O after a possible write → `Ambiguous`.
  - Coroutine cancellation propagates; no state transition is invented.

### 5.3 Outbox state machine

```text
             enqueue                 claim (QUEUED→SENDING)
  [new] ───────────────► QUEUED ──────────────────────────► SENDING
                            ▲                                  │
                            │ requeue (explicit retry)         ├── 2xx ──► ACCEPTED
                            │                                  ├── definitive 4xx ──► FAILED (retryable=false)
                            │                                  ├── 429/5xx/connect ─► FAILED (retryable=true)
                            └──────────────────────────────────┴── timeout/unknown ─► AMBIGUOUS
```

Invariants:

1. **Every transition is a conditional SQL update** with the expected `state`.
   If `rowsUpdated == 0`, another sender or the UI won the race; report `Busy`,
   do not send.
2. `SENDING` is never shown as success. On process start,
   `recoverStuckSending(updatedAt < now−2 min)` moves orphaned rows to
   `AMBIGUOUS`, not `QUEUED`: the request may have reached the server.
3. No automatic retries in Phase 4. The user presses **Retry**; a retry from
   `AMBIGUOUS` requires an explicit "may create a duplicate" confirmation.
4. `ACCEPTED` is terminal. `cancel` is allowed only for `QUEUED`, `FAILED` and
   `AMBIGUOUS`.
5. ntfy has no idempotency key. The app cannot prove a retry is duplicate-free;
   the UI says so instead of pretending.

### 5.4 Repository

```kotlin
interface OutboxRepository {
    fun observe(profileId: String, limit: Int = 100): Flow<List<OutboxEntry>>
    suspend fun enqueue(profileId: String, request: PublishRequest): Result<String>
    suspend fun send(clientId: String): SendOutcome
    suspend fun retry(clientId: String, allowAmbiguous: Boolean): SendOutcome
    suspend fun cancel(clientId: String): Boolean
    suspend fun recoverStuck(profileId: String): Int
    suspend fun prune(profileId: String)
}

sealed interface SendOutcome {
    data class Accepted(val serverMessageId: String?) : SendOutcome
    data class Failed(val reason: String, val retryable: Boolean) : SendOutcome
    data class Ambiguous(val reason: String) : SendOutcome
    data object Busy : SendOutcome
    data object NotFound : SendOutcome
}
```

- `enqueue` validates, inserts `QUEUED`, records `PUBLISH_QUEUED`, returns the
  `clientId`. It does not touch the network.
- `send` claims the row, loads the **entry's** profile config, calls the client,
  completes the transition and records one activity event.
- `retry` requeues then delegates to `send`; `allowAmbiguous=false` refuses an
  `AMBIGUOUS` row.
- `prune`: terminal entries older than 30 days or beyond 500 per profile; never
  touches `QUEUED`/`SENDING`.

## 6. Activity timeline

```kotlin
enum class ActivityType { MESSAGE_RECEIVED, PUBLISH_QUEUED, PUBLISH_ACCEPTED,
    PUBLISH_FAILED, PUBLISH_AMBIGUOUS, PUBLISH_RETRY, COMMAND_SUBMITTED,
    COMMAND_CONFIRMED, COMMAND_RESULT, COMMAND_EXPIRED, COMMAND_REJECTED }

enum class ActivitySeverity { INFO, SUCCESS, WARNING, ERROR }

interface ActivityRepository {
    fun observe(profileId: String, limit: Int = 200): Flow<List<ActivityEvent>>
    suspend fun record(profileId: String, type: ActivityType, severity: ActivitySeverity,
        summary: String, correlationId: String? = null, messageId: String? = null,
        topic: String? = null)
    suspend fun prune(profileId: String)
}
```

- **No bodies in summaries.** Receive: `"Message received in ops"`. Publish:
  `"Publish to ops"`. Failure: the error category label, never a URL/token.
- Receive events are written by `MessageRepository.store` in the same Room
  transaction as the message insert; a duplicate `(profileId, messageId)` returns
  `false` and writes no event.
- Retention: 90 days and 5,000 rows per profile, pruned after each insert. The
  query is bounded by `(profileId, createdAt)`.
- **Delivery events in Phase 4 are the outbox transitions** (`PUBLISH_ACCEPTED`,
  `PUBLISH_FAILED`, `PUBLISH_AMBIGUOUS`). There is no server delivery receipt and
  no read receipt. Local notification-shade display is an app-local action, not
  server delivery, and is not recorded as one.
- The timeline is Room-backed; no in-memory history.

## 7. Command envelope and mock executor

### 7.1 Schema (`rplab.command.v1`)

```json
{
  "schema": "rplab.command.v1",
  "command_id": "0f5c0f0e-6d7f-4a0d-9b1e-2f1f6f5e8a11",
  "nonce": "5Jx3m0m9dQ8m3S8k2pW7c1v4n6b9x0z2q4r6t8u1w3y",
  "issued_at": "2026-11-09T12:00:00Z",
  "expires_at": "2026-11-09T12:05:00Z",
  "requested_by": "android:primary",
  "target": "mock-pve",
  "action": "service.status",
  "args": { "service": "navidrome" },
  "reply_topic": "rplab-command-results",
  "requires_confirmation": false
}
```

- Timestamps are epoch millis in the data model and ISO-8601 UTC in JSON.
  `java.time` is unavailable on minSdk 24 without desugaring; use
  `SimpleDateFormat` with `Locale.US`/UTC for formatting only.
- `command_id` is a UUID. `nonce` is 32 random bytes, Base64 URL-safe, no
  padding (43 chars), generated per command.
- `requested_by = "android:<profileId>"`; max 64 chars.
- `reply_topic` is a valid ntfy topic; Phase 4 never subscribes to it.

### 7.2 Allowlist

| Action | Args | Confirmation | Mock result |
|---|---|---|---|
| `host.health` | none | no | `mock: host healthy` |
| `service.status` | `service` | no | `mock: <service> running` |
| `storage.summary` | none | no | `mock: 42% used` |
| `backup.latest_status` | none | no | `mock: last backup OK` |
| `container.status` | `container` | no | `mock: <container> running` |
| `service.restart` | `service` | **yes** | `mock: restart simulated` |

`service`/`container` regex: `^[a-z0-9][a-z0-9_-]{0,31}$`.
Unknown actions, unknown args, wrong types, non-allowlisted targets and
over-long fields are rejected before insert.

### 7.3 Validation and replay

`CommandPolicy.validate(envelope, nowMillis, expectedTarget)` enforces:

- exact schema string;
- UUID command ID; nonce regex; single-use nonce (unique index);
- `issuedAt` within ±60 s of now; `expiresAt > issuedAt`; window ≤ 5 min;
- target exactly `mock-pve` in Phase 4;
- action in the allowlist; args exactly the declared keys with valid values;
- `requires_confirmation` equals the policy value;
- destructive action requires a stored `confirmedAt`;
- reply topic valid.

Expired rows are marked `EXPIRED` by `CommandRepository.expireOverdue` when the
Activity/Command screen loads; no background timer.

### 7.4 Executor boundary

```kotlin
interface CommandExecutor {
    suspend fun execute(envelope: CommandEnvelope): CommandExecutionResult
}

class MockCommandExecutor : CommandExecutor
```

- Deterministic, in-process, no network, no shell, no PVE, no credentials.
- Re-validates the envelope; returns `mock: true` in the result JSON.
- The UI banner is mandatory: **“MOCK executor — no real execution.”**
- A real gateway is Phase 6. The envelope schema is the integration contract; the
  app does not ship gateway credentials.

## 8. Profiles, isolation and switching

### 8.1 Isolation rules

- Every message, topic, outbox, activity and command query takes `profileId`.
- Dedupe remains `(profileId, messageId)`; the same server message ID under two
  profiles is two rows, correctly.
- `activeProfileId` in DataStore is the only global pointer.
- Outbox sends use the entry's `profileId`, even if the user switches profiles
  mid-send. Results stay with the originating profile.
- No screen shows a mixed-profile list.

### 8.2 Safe switch sequence

`MainViewModel.switchProfile(id)`:

1. Stop Instant (`deliveryController.stopInstant()`); cancel Battery Saver work.
2. Persist the new active ID.
3. Clear in-memory messages, topics, selected message, search and filters; show
   a loading state.
4. Load the new config, topics and bounded message window.
5. Re-apply the saved delivery mode for the new profile (Instant start or
   Battery Saver schedule).
6. Never delete the old profile's data. Switching is reversible.

### 8.3 Server-URL change preview

Editing a profile's base URL can mix data from two servers under one profile ID.
Phase 4 therefore adds a preview dialog before saving:

- shows destination URL and affected counts (messages, topics, outbox entries);
- explains that existing rows stay under this profile and may mix servers;
- primary action: **Create a new profile instead** (recommended); secondary:
  **Keep this profile and mix data** with explicit confirmation; cancel is safe.

No automatic data move, merge or deletion. Cross-profile migration is Phase 6.

**Database migration UI:** schema migrations are automatic, forward-only and
never user-triggered; there is no destructive option to present. The only
user-facing migration surface is this profile URL-change preview, which shows
the destination and affected counts before anything changes.

## 9. UI and navigation

Bottom destinations become the five from `PLAN.md` §2.1:

```text
Inbox | Topics | Compose | Activity | Settings
```

- **Saved** remains a first-class Inbox filter (`InboxFilter.SAVED`), not a sixth
  bottom item. The existing saved-list surface is reused; no saved feature is
  removed.
- Search stays in the top bar; message detail stays a drill-in screen.
- A profile chip in the top bar opens a switcher; Setup remains the no-profile
  state.
- Compose is a full destination with the outbox below the form.
- Activity has two tabs: **Timeline** and **Commands (MOCK)**. This avoids an
  admin destination before Phase 6.

### Screen responsibilities

| Screen | Responsibility |
|---|---|
| `PublishScreen` | Validate and enqueue a publish (topic, title, body, priority, tags, click, attachment URL, view action, delay); show outbox with honest states; retry/cancel |
| `ActivityScreen` | Room-backed timeline; filters; correlation links; no bodies |
| `CommandPanel` | Allowlisted action form, expiry preview, destructive confirmation, mock results |
| `InboxScreen` | Room window, filters incl. Saved, unread counts, profile-aware empty/offline states |
| `TopicsScreen` | Room-backed subscriptions, unread counts, mute/pause, no remote ACL claims |
| `SettingsScreen` | Profiles, delivery mode, E-Ink entry point (Phase 5), diagnostics |

### State requirements

Every new screen defines: loading, empty, offline, error, permission-denied,
sending/sent/failed/ambiguous, and (commands) mock banner. No action button is
dead; each has an observable result. Accessibility labels, 48 dp touch targets,
and no color-only statuses are required (E-Ink proofing starts here).

## 10. ViewModels

| ViewModel | State | Intents |
|---|---|---|
| `PublishViewModel` | form, available topics, validation, sending, outbox | edit, send, retry, cancel |
| `ActivityViewModel` | timeline, filter, selected tab | filter, load more, open correlation |
| `CommandViewModel` | action, args, confirmation, history | submit (mock), confirm, load |
| `MainViewModel` | existing state + `unreadCounts`, `visibleLimit`, profile preview | navigate, switch profile, load more |

- New VMs are created with explicit factories in `MainActivity` using
  `AppContainer`, matching `MainViewModelFactory`.
- UI state never holds the full history: outbox 100, activity 200, inbox a
  growing window capped at 1,000 (`visibleLimit` steps 100 → 200 → 400 → 1,000).
- No timers or polling in Phase 4. The only clock read is the command expiry
  check on screen load.

## 11. Security and privacy

- Credentials stay in `KeystoreSecretStore`; outbox/activity/command tables hold
  no tokens, passwords or auth headers.
- Publish JSON never includes credentials, profile secrets or gateway keys.
- Activity summaries never include message bodies, titles or URLs. Sanitized
  error categories only.
- Click, attachment and view-action URLs: http/https only, host required, no
  user-info, no `javascript:`/`file:`/`intent:`, max 2048 chars.
- Publish body size is capped (4 KiB UTF-8 message, 32 KiB JSON) before any
  network call; the server may impose a lower limit and is reported honestly.
- `POST` redirects and OkHttp connection retries are disabled to avoid silent
  duplicate sends.
- Commands are explicit UI actions, never derived from incoming messages. No
  shell, SQL, PVE or arbitrary HTTP execution. Nonce + expiry + allowlist +
  confirmation are enforced before the mock executor runs.
- Destructive command results are labelled **MOCK**; no real operation is
  simulated as successful.
- External links open only after the existing confirmation sheet and URL policy.
- Local database remains app-private; `allowBackup=false` stays in force.

## 12. Offline and failure behavior

| Condition | Outbox state | UI copy |
|---|---|---|
| Offline at enqueue | `QUEUED` | “Queued — will stay here while offline.” |
| DNS/connect/TLS failure on send | `FAILED`, retryable | “Not sent. Check the connection and retry.” |
| 401/403 | `FAILED`, not retryable | “Server rejected the credentials/permission.” |
| 4xx payload | `FAILED`, not retryable | Server message, sanitized |
| 429/5xx | `FAILED`, retryable | “Server busy. Retry later.” |
| Timeout/unknown after write | `AMBIGUOUS` | “Uncertain — the server may have received it. Retry only if you accept a possible duplicate.” |
| Process death during send | `AMBIGUOUS` on next start | Same as above |
| Accepted | `ACCEPTED` | “Server accepted. This is not a read receipt.” |

The activity timeline records each transition except `SENDING`; the outbox row
itself is the source of truth.

## 13. Low-RAM and performance rules

- No new production dependency; no Paging 3; no image work.
- Bounded Room queries and LazyColumn keys; no full-history lists in state.
- One insert + one activity insert per received message in one transaction.
- No per-keystroke network or database writes; form validation is local.
- Publish body and JSON caps prevent large allocations.
- Activity/outbox pruning is amortized on insert and bounded by indices.
- No continuous animation, timer, shimmer or polling in the new screens.
- Profile switch clears the window before loading the next profile; memory does
  not accumulate across switches.
- Performance claims remain unmeasured until a real low-RAM device exists.

## 14. Test strategy

| Layer | Tests |
|---|---|
| Publish validation | topic, body bytes, title, tags, priority, click/attachment URLs, actions, delay, JSON size |
| Publish client | MockWebServer 2xx/400/401/403/404/413/429/5xx, malformed success body, timeout, cancellation, no redirect replay |
| Outbox | enqueue, claim race (`rowsUpdated = 0`), definitive/retryable/ambiguous, stuck-SENDING recovery, retry rules, cancel, prune |
| Activity | receive event only on new message, no body leakage, retention cap, profile isolation |
| Command envelope | schema, UUID, nonce, expiry window, target, allowlist, arg schemas, unknown keys, confirmation, replay duplicate, expired rejection |
| Mock executor | deterministic results, no network/shell calls, `mock: true`, failure mapping |
| Repository | profile isolation, switch sequence, URL-change preview counts |
| ViewModels | form validation, send/retry states, timeline filters, command confirmation, no duplicate send on double tap |
| Compose UI | publish empty/loading/error/offline/ambiguous, timeline empty, command mock banner, accessibility labels |
| Migration | `Migration1To2Test` from the frozen `1.json`; data survives; indices exist |
| Regression | full Phase 3 suite, `assembleDebug`, `lint` at the phase gate |

No real network in unit tests. No device claim without a device.

## 15. Dependencies and implementation order

**Build changes:** `exportSchema = true`, `room.schemaLocation`, test-only
`androidx.room:room-testing` (same version). No production dependency changes.

| Stage | Work | Complexity | Gate |
|---|---|---|---|
| A | Freeze v1 schema; Room v2 entities/DAOs; migration; migration test | M | Migration test green; existing tests green |
| B | `NtfyAuth` extraction; `PublishClient`; URL/validation; MockWebServer tests | S/M | Contract tests green; Phase 2/3 tests unchanged |
| C | Outbox repository/state machine; activity repository; receive-event wiring; pruning | M | State-machine and retention tests green |
| D | ViewModels, Publish/Activity screens, navigation, Saved filter, profile switch/preview | L | Compose tests green; no dead buttons |
| E | Command policy, repository, mock executor, CommandPanel | M | Envelope/replay/mock tests green |
| F | Full gate, independent audit, `PHASE_4_REPORT.md` | S/M | PASS WITH CONDITIONS or better |

## 16. Deferred and open owner decisions

Deferred by design: binary attachments, automatic background publish retries,
real gateway/PVE execution, ntfy admin, E-Ink profile, notification actions,
FTS search, Paging 3, multi-account sync, R8/release signing.

Owner decisions requested with this plan:

1. Approve the Phase 4 scope and staged gates above.
2. Approve JSON-root publish as the Phase 4 transport (topic-header publish
   remains a documented alternative).
3. Approve `view`-only publish actions in Phase 4; `broadcast` and `http`
   actions stay out for security.
4. Approve **manual-only** outbox retry (no background auto-retry) with the
   ambiguous/duplicate warning.
5. Confirm the command centre remains **mock-only**; no real gateway in Phase 4.
6. Approve the profile URL-change preview (keep-and-mix with confirmation, or
   create a new profile; never delete data).
