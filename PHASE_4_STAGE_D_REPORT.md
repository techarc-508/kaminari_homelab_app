# Kaminari Nfty — Phase 4 Stage D Report

**Integrated Compose UI**
**Status:** Complete. Stage E not started.
**Date:** 2026-11-10
**Owner authorization:** Stage C accepted; Stage D explicitly authorized.
**Checkpoint commit:** `56e68eb` — the final Stage D code checkpoint, containing the integration commit `4747786` plus the in-flight profile-scoping fix.
**Schema:** Room v2 unchanged. No new entity, table, column or migration.
**Dependencies:** none added.

---

## 1. Scope delivered

Stage D turns the existing persistence, publish and orchestration layers into a
working application. It does not redesign those layers.

Implemented primary destinations:

1. **Inbox** — received messages.
2. **Topics** — subscriptions and topic management.
3. **Compose** — publish a message.
4. **Outbox** — queued, sending, accepted, failed and ambiguous publishes.
5. **Activity** — persistent event timeline.
6. **Settings** — profiles, delivery, notifications and app preferences.

The owner's six-destination Stage D instruction supersedes the older
five-destination wording in `PHASE_4_ARCHITECTURE.md`. There is no command tab,
command panel or command action; those remain Stage E.

Existing drill-in screens remain for message detail and global search. Setup is
still the first-run destination when no profile exists.

## 2. Navigation and application shell

- `AppDestination` now includes `COMPOSE`, `OUTBOX` and `ACTIVITY` alongside the
  existing `INBOX`, `TOPICS`, `SETTINGS`, `SETUP`, `DETAIL` and `SEARCH`.
- `NftyBottomNavigationBar` renders six compact, evenly weighted tabs with short
  labels and a restrained selected state. It is shown only on the six primary
  destinations; detail, search and setup keep their dedicated chrome.
- `MainViewModel.navigateTo` updates the retained tab when entering a primary
  destination, so Settings opened from a top bar is the selected tab; detail and
  search return to the tab they came from.
- `MainActivity` creates three UI ViewModels from `AppContainer`:
  `MainViewModel`, `PublishViewModel` and `ActivityViewModel`, and collects each
  with `collectAsStateWithLifecycle`.
- Destructive confirmations remain global: message delete, clear history, clear
  saved, profile switch, ambiguous retry, URL preview and external-link open.
- Rotation/process recreation is safe for persistent data because all lists are
  re-read from Room/DataStore. The in-memory compose draft is not persisted
  across process death; see §8.

## 3. Inbox

- Profile-aware Room message list supplied by `MainViewModel`; no database call
  from a Composable.
- Topic and message previews, read/unread visual state, saved state, message
  count and relative/absolute timestamps remain in the existing Kaminari cards.
- **Saved is an Inbox filter**, not a delivery mode and not a separate screen:
  `InboxFilter.ALL / UNREAD / SAVED` plus the existing topic filter.
- Inline search is wired to the existing profile-scoped query. The dedicated
  search screen remains available from the top bar.
- Detail view retains copy, save/unsave, safe delete with confirmation and
  external URL confirmation before the browser opens.
- Delete, clear history and clear saved all require confirmation. Clear history
  preserves saved items; clear saved only removes the flag.
- Empty, loading, disconnected, permission, error and sample-data states are
  retained. Refresh retries the live stream through the existing ViewModel path.
- Message deduplication, saved/read persistence and profile isolation are
  unchanged Stage A/B behavior.

## 4. Topics

- Lists only the current profile's subscriptions from the existing repository.
- Add topic, subscribe, unsubscribe (with confirmation), pause/resume and
  navigate to the topic-filtered Inbox.
- Message counts are derived from the currently displayed profile window.
- Empty and error states are explicit; the add dialog is shared by the empty and
  populated states.
- No second subscription store was introduced.
- **Priority threshold:** the current `NftyTopic`/Room model has no per-topic
  minimum-priority field. The screen therefore does not display or invent one.
  Priority is supported where the model supports it today: on publish.

## 5. Compose and publishing

- `PublishViewModel` owns the form and publish state machine:
  `PublishForm` (topic, title, body, priority, tags, click URL, attachment URL,
  delay, advanced-section visibility).
- Topic chips come from the active profile's subscribed topics; free topic entry
  remains possible.
- Priority is constrained to 1–5. Tags accept comma/space-separated values.
  Click and attachment fields are URL metadata only. Delay accepts the Stage B
  duration/Unix-timestamp format.
- Validation uses `PublishValidator.validateRequest`, which checks the request
  without loading credentials. Errors are shown before enqueue. No credential is
  placed in UI state, logs or Compose parameters.
- Enqueue and send go through the existing Stage C `OutboxRepository`; there is
  no second publish path.
- Outcomes are explicit:
  - **SENDING** while the request is in flight.
  - **ACCEPTED BY SERVER** means the ntfy server accepted the message. The screen
    states that no recipient delivery receipt exists.
  - **FAILED** shows the reason and whether retry is allowed.
  - **OUTCOME UNKNOWN / AMBIGUOUS** warns that the message may already have been
    accepted and requires explicit confirmation before retry.
- The send button is disabled while sending and `PublishViewModel` rejects a
  second send synchronously, so repeated taps cannot create duplicate publishes.
- A failed send preserves the draft. A successful send clears the body/title but
  keeps the topic for the next message.
- No binary upload is implemented. Attachments are URL metadata only.

## 6. Outbox

- Profile-scoped observation through the existing repository.
- Status labels for `QUEUED`, `SENDING`, `ACCEPTED`, `FAILED` and `AMBIGUOUS`.
- Safe topic/body preview, timestamps, attempt count and sanitized failure text.
- Retry is manual only:
  - `QUEUED` can be sent now.
  - retryable `FAILED` can be retried.
  - `AMBIGUOUS` requires the global “may duplicate” confirmation.
  - accepted rows cannot be retried.
- Remove/cancel is offered only for unsent/unsuccessful rows and uses the
  repository's conditional delete.
- Empty, loading and error states are present.
- There is no automatic retry, background resend, scheduler or hidden publish
  loop. `ACCEPTED` is never presented as delivered.

## 7. Activity

- Room-backed, profile-scoped timeline from `ActivityRepository`.
- Newest-first chronological list with event label, icon, severity color and
  timestamp.
- Filters: All, Publish, Inbox, Attention (warning/error).
- Empty and loading states are explicit. The “no profile” path settles to idle
  instead of loading forever (regression fixed during Stage D testing).
- Summaries are the sanitized Stage C text only. Message bodies, titles,
  credentials and authorization headers are not exposed.
- No command lifecycle is invented; command events remain Stage E.

## 8. Settings and profiles

- Profile list with display name, URL, auth method, encrypted-credential
  indicator and **ACTIVE** marker.
- Add profile and edit active profile using the existing supported APIs.
  New credential entry is local dialog state; `ProfileEditUi` carries no secret.
- Profile switching always asks for confirmation and runs the safe sequence:
  stop the old stream and delivery engine, clear only the in-memory window,
  persist the new active ID, bind the new profile, reapply the selected delivery
  mode. No messages, topics, credentials or outbox rows are deleted.
- Active server/profile is shown in Settings and the top bar. There is no silent
  switch or fallback to another profile.
- Instant and Battery Saver selectors persist through `AppPreferences`; delivery
  status and the honest limits remain visible.
- Notification status and the system settings shortcut remain wired.
- Theme, compact density, demo dataset, history/saved clearing, diagnostics and
  privacy copy are retained.
- **URL-change safety:** editing a URL opens the approved preview with the
  profile-scoped message, topic and outbox counts. The user chooses **Create new
  (recommended)** or **Keep & mix**; nothing is moved, merged or deleted.
- When creating a new profile for a changed URL, a blank credential field is
  rejected with a re-entry request. A stored secret is never silently copied to
  a different server URL.
- Stored secrets are never rendered in Settings; only “encrypted credential
  stored” appears.

## 9. Repository and ViewModel wiring

### Added

| File | Purpose |
|---|---|
| `PublishViewModel.kt` | Compose form, validation, enqueue/send, outcome mapping, ambiguous confirmation, outbox actions. |
| `ActivityViewModel.kt` | Profile-scoped activity query, filters, loading/error. |
| `ui/screens/PublishScreen.kt` | Functional compose form, advanced section, outcome card and send controls. |
| `ui/screens/OutboxScreen.kt` | Status list, detail text, manual retry/remove and empty states. |
| `ui/screens/ActivityScreen.kt` | Timeline cards, filters and states. |
| `ui/screens/ProfileDialogs.kt` | Edit/add profile and URL-change preview dialogs. |
| `ui/components/NftyStatusBadge.kt` | Shared compact status/notice badge and inline notice. |

### Modified

| File | Change |
|---|---|
| `MainViewModel.kt` | Six destinations, saved filter, profile edit/preview, safe switching, sanitized configs, topic counts, active connection test, URL preview counts. |
| `MainActivity.kt` | New ViewModels, six destinations, screen wiring, profile preview and ambiguous-retry confirmations. |
| `ui/navigation/BottomNavigationBar.kt` | Six weighted compact tabs and test tags. |
| `ui/screens/InboxScreen.kt` | Saved filter, inline search and refresh wiring. |
| `ui/screens/TopicsScreen.kt` | Empty/error states, unsubscribe confirmation, shared add dialog, priority text cleanup. |
| `ui/screens/SettingsScreen.kt` | Profile list/add/edit/select, active server clarity, retention note and new callbacks. |
| `data/api/PublishValidator.kt` | Split request validation from config validation so no credential is needed to validate the form. |
| `data/repository/OutboxRepository.kt` | Added `count(profileId)` for URL-change preview. |

No new database entity, DAO, migration, schema file, dependency or delivery
engine was added.

## 10. Tests added

37 new tests, all on final code:

| Area | Tests |
|---|---|
| `PublishViewModelTest` | 10 — validation, invalid URL, accepted, failed with draft preservation, ambiguous confirmation, double-tap, outbox ambiguous retry, cancel, profile-switch draft clear, in-flight result blocked after a profile switch. |
| `ActivityViewModelTest` | 6 — load, all filters, profile rebind, no-profile idle. |
| `MainViewModelTest` | 3 — safe switch keeps history, URL preview counts/keep, new-profile secret re-entry guard. |
| `PublishScreenTest` | 5 — form/acceptance note, validation, ambiguous warning, accepted wording, advanced fields. |
| `OutboxScreenTest` | 4 — empty, accepted/failed labels, ambiguous confirmation, queued actions. |
| `ActivityScreenTest` | 3 — summaries, empty, filter click. |
| `SettingsScreenTest` | 3 — profile select, delivery selection, no secret rendered. |
| `InboxScreenTest` | 2 — saved filter and search. |
| `BottomNavigationBarTest` | 1 — all six destinations reachable. |

Test fakes now model the Stage C repositories without adding production data or
mock success paths to `main`.

## 11. Verification

Run on final code with the temporary Maven Central mirror init script (local DNS
workaround only; no project build file was changed):

```text
./gradlew testDebugUnitTest --rerun-tasks
# exit 0 — 323 tests, 0 failures, 0 errors, 2 pre-existing skips

./gradlew assembleDebug
# exit 0 — app-debug.apk, 19,950,553 bytes

./gradlew lintDebug
# exit 0 — 0 errors, 56 warnings (unchanged baseline)
```

The combined Robolectric suite initially killed the Gradle daemon under host
memory pressure. Orphaned test workers were cleared and the gate was rerun with
a 2 GB Gradle heap and two workers; every test completed successfully. This was
an environment constraint, not a skipped test.

Kotlin compilation warnings on new files were cleaned (AutoMirrored `Send`).
The remaining compile warning is the pre-existing `OpenInNew` deprecation in
`NotificationDetailScreen.kt`; the remaining test warnings are the pre-existing
`Migration1To2Test` inference warnings.

## 12. Usability and accessibility notes

- Light-first Kaminari palette, compact cards and restrained accent usage are
  preserved; night/day/system theme continues to work.
- Bottom tabs have content descriptions and test tags; primary actions are text
  buttons or labeled icons.
- Content is scrollable on ordinary phone widths; Robolectric tests use a
  411 × 891 dp viewport.
- Status is conveyed by both text and color (for example `ACCEPTED`, `FAILED`,
  `AMBIGUOUS`), not color alone.
- Destructive actions are confirmations, not one-tap operations.
- No gradients, decorative animation or heavy dashboard chrome was added.
- Landscape/tablet-specific layouts were not separately designed; the existing
  responsive columns remain.

## 13. Known limitations

- **No device or emulator validation.** No adb/device is available. Robolectric
  verifies composition and state wiring, not rendering, touch, IME, TalkBack or
  real Android lifecycle behavior.
- **No real-server verification.** R-17 remains open: the ntfy origin previously
  returned HTTP 502. Stage B MockWebServer contracts remain the network evidence.
- **Draft is not persisted through process death.** It survives recomposition,
  rotation and a failed send, but not a killed process. This is a deliberate
  small-scope choice; persistent drafts were not requested.
- **No per-topic priority threshold UI.** The current topic model has no such
  field, so no synthetic control was added.
- **No command UI.** Command Centre, `rplab.command.v1`, PVE actions and command
  lifecycle are Stage E and were not implemented or mocked.
- **No binary uploads.** Attachment fields are URL metadata only.
- **No automatic retry/resend.** `AMBIGUOUS` recovery remains manual and requires
  confirmation.
- **Activity is not a forensic log.** It intentionally excludes bodies and
  credentials; retention remains 90 days / 5,000 events per profile.
- **No new schema.** Existing Room v2 remains forward-only (R-18).

## 14. Scope confirmation

Not implemented in Stage D:

- Stage E Command Centre, command envelopes, PVE gateway or command execution.
- Binary attachment uploads.
- Automatic publish retry or background resend.
- New database migrations or schema changes.
- Release signing or deployment.
- New UI framework or large dependency.

## 15. Checkpoint

Stage D code, tests and documentation are checkpointed in the commit recorded
below. Stage E has not been started.

**Checkpoint commit:** `56e68eb`. The report-hash update is a follow-up documentation commit.
