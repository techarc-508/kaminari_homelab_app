# PHASE 2 REPORT — Kaminari Nfty V1

## 1. Phase and objective

Implement the real ntfy communication engine on top of the approved Phase 1 architecture:

- Configurable server URLs and multiple server profiles.
- Supported ntfy authentication methods (guest, username/password, access token).
- Public and authenticated topic subscriptions with honest connection/auth/server errors.
- Real long-lived ntfy JSON stream: multiple topics, validated events, `message_delete` / `message_clear`, bounded exponential-backoff reconnect, duplicate suppression, safe cancellation.
- Durable Room history with stable identity, dedupe, pagination and cursor-based catch-up.
- Verified Keystore-backed credential encryption, no credential logging, default TLS.
- MockWebServer-based contract tests; no dependency on the public ntfy endpoint.

Phase scope was respected: no foreground/background delivery, no WorkManager, no publish UI, no E-Ink profile and no PVE gateway were added.

## 2. What changed

### 2.1 Transport and parsing

- **Typed event model + validating parser** (`NtfyEventParser`): `open`, `keepalive`, `message`, `message_delete`, `message_clear`, `poll_request`, unknown types.
  - Requires `id`, `topic` and a positive `time` on messages; seconds are normalized to milliseconds.
  - Validates and bounds: 512 KB max line, 64 KB max body, max 64 tags, max 16 actions; priority clamped to 1–5; blank title → null; attachment without URL dropped; non-string tags skipped.
  - Unparseable/invalid lines become counted diagnostics. **Correction (gate review G-03):** this holds for lines the transport can read; a line exceeding the 512 KB read limit raises `EOFException` from `readUtf8LineStrict`, which currently ends the stream instead of producing a diagnostic (see §9, G-04).
- **URL builder** (`NtfyEndpoint`) validates scheme, rejects credentials-in-URL, query/fragment, invalid topic charset and invalid `since` cursors; preserves a reverse-proxy path prefix; supports `poll=1`.
- **Stream client rewritten** (`NftyStreamClient`):
  - `callbackFlow` + `awaitClose { call.cancel() }` so cancelling the collector cancels the OkHttp call and unblocks a pending socket read (the earlier `invokeOnCompletion` hook could deadlock).
  - Blocking reads run on `Dispatchers.IO`; bounded `readUtf8LineStrict` prevents unbounded line allocation.
  - Bounded retriable vs terminal HTTP classification: 401 → AUTH, 403/404 → FORBIDDEN (terminal, no reconnect storm), 400 terminal, 429/5xx retriable.
  - Auth is header-only: `Bearer` for tokens, Basic for user/password; CR/LF stripped from pasted values (header-injection guard).
  - `readTimeout` 120 s detects dead sockets without dropping healthy streams; no custom `TrustManager`/`HostnameVerifier`.
  - Health probe validates `{"healthy":...}` and returns `REACHABLE` (endpoint up, credentials unproven) instead of falsely claiming `AUTHENTICATED`/`SUPERUSER`.

### 2.2 Reconnect engine

- `NftyRepository` now:
  - Exposes typed `updates` (`NewMessage`, `Removed`, `TopicCleared`) plus `reconnectAttempt` and `malformedLineCount`.
  - Calls a **`sinceProvider` before every attempt**, so a reconnect resumes from the newest durable message ID instead of a fixed 12 h window.
  - Uses bounded exponential backoff with symmetric jitter: `1.5 s → 60 s (±20%)`, reset on `Connected`; policy extracted to pure `StreamBackoff` for direct testing.
  - Reports a stream that ends without a terminal error as `STREAM` (“Stream closed by server”) rather than silently going idle.
  - Suppresses replayed message IDs with a 512-entry bounded access-order cache (reset per subscription); durable dedupe remains the Room unique index.
  - `catchUp()` performs one bounded `poll=1` request for historical synchronization.
  - Stops cleanly: cancelled job, no leaked scope work; `close()` cancels the repository scope.

### 2.3 Persistence

- Added DAO/repository operations: `page(limit, offset)`, `latestMessageId`, `exists`, `countForProfile`, `clearTopicKeepingSaved`.
- Stable identity preserved: unique `(profileId, messageId)` index; `store()` returns `false` for replays.
- `message_clear` with a topic deletes only that topic’s unsaved rows and keeps locally starred items (local user data). A **topic-less** `message_clear` is treated as global and clears all unsaved rows for the profile (gate review G-07).
- Message `expiry` now round-trips through Room instead of being discarded.
- **No schema change**: database version stays 1, so there is no migration and no risk of destructive upgrade.

### 2.4 Multi-profile support

- `ProfileRepository` gained `observeProfiles`, `loadConfig(id)`, `createProfile`, `updateProfile`, `deleteProfile`; IDs are generated for new profiles, secrets remain keyed per profile in the Keystore store.
- `AppPreferences` gained `activeProfileId()` and `clearActiveProfileId()`.
- ViewModel gained `addServerProfile`, `switchProfile`, `deleteServerProfile`; switching rebinds repository/topic collectors and forces a fresh subscription with a new cursor.
- UI (minimal, existing screens only): Settings lists saved profiles with an active marker and “Use” action plus “Re-sync missed history”; Setup adds “Save as New Profile” when a profile already exists.

### 2.5 ViewModel stream pipeline

- Subscribes to typed updates: persists new messages, applies `Removed` → `delete`, `TopicCleared` → topic clear.
- Runs a **catch-up poll from the durable cursor before opening the live stream**, guarded by a 30 s timeout, and reports received counts.
- Restarts the stream only when the subscribed-topic set actually changes (previously every topic-flow emission restarted it).
- Tracks `lastCursor`, `reconnectAttempt`, `isSyncing` for diagnostics.

## 3. Files added/modified

Reconciled against `git status --porcelain -uall` during the gate review. Because **Phase 1 was never committed**, files created in Phase 1 appear as untracked rather than modified; the split below is therefore by origin, and the Git-visible counts are 15 modified + 27 untracked relative to `HEAD` (`f254cd6`, a Phase 0 commit).

**Added in Phase 2 (main, 5):**
- `app/src/main/java/com/rplab/kaminarinfty/model/StreamUpdate.kt`
- `app/src/main/java/com/rplab/kaminarinfty/model/ServerProfile.kt`
- `app/src/main/java/com/rplab/kaminarinfty/data/api/NtfyEndpoint.kt`
- `app/src/main/java/com/rplab/kaminarinfty/data/api/NtfyEventParser.kt`
- `app/src/main/java/com/rplab/kaminarinfty/AppContainer.kt` — manual DI container plus `KaminariApp` (`Application`), registered in the manifest

**Added (tests, 5):**
- `app/src/test/java/com/rplab/kaminarinfty/data/api/NtfyEndpointTest.kt`
- `app/src/test/java/com/rplab/kaminarinfty/data/api/NtfyEventParserTest.kt`
- `app/src/test/java/com/rplab/kaminarinfty/data/api/NtfyStreamClientTest.kt`
- `app/src/test/java/com/rplab/kaminarinfty/data/repository/StreamEngineTest.kt`
- `app/src/test/java/com/rplab/kaminarinfty/data/repository/MessageRepositoryTest.kt`

**Modified (main, 12):**
- `data/api/NftyStreamClient.kt` (rewritten), `data/repository/NftyRepository.kt` (rewritten)
- `data/repository/MessageRepository.kt`, `data/repository/ProfileRepository.kt` (Phase 1 files, extended)
- `data/local/Daos.kt`, `data/local/MessageMapper.kt`
- `data/preferences/SecretStore.kt`, `data/preferences/AppPreferences.kt`
- `model/ServerConfig.kt`, `model/NftyMessage.kt`
- `MainViewModel.kt`, `MainActivity.kt`
- `ui/screens/SettingsScreen.kt`, `ui/screens/SetupScreen.kt`
- `ui/screens/InboxScreen.kt` — retry wiring and state plumbing for the stream (123 insertions/129 deletions)
- `ui/components/NftyConnectionIndicator.kt`, `ui/components/NftyTopBar.kt` — `isSampleData` default false
- `app/src/main/AndroidManifest.xml` — registers `.KaminariApp`, sets `allowBackup="false"`, drops the now-unused backup rules attributes

**Modified (build, 2):**
- `gradle/libs.versions.toml` — added `mockwebserver` (test-only, same OkHttp 4.10.0 version)
- `app/build.gradle.kts` — `testImplementation(libs.mockwebserver)`

**Modified (tests, 5):**
- `testing/Fakes.kt`, `MainViewModelTest.kt`, `data/local/MessageDaoTest.kt`, `data/repository/ProfileRepositoryTest.kt`, `data/repository/NftyRepositoryTest.kt`

No files were deleted. No dependency was upgraded; the only new dependency is test-scoped MockWebServer at the already-pinned OkHttp version, so runtime APK size and minSdk 24 behavior are unchanged.

## 4. Architecture / API decisions

| Decision | Rationale | Alternative rejected |
|---|---|---|
| ntfy `/json` NDJSON long-lived HTTP stream | This is ntfy’s documented subscribe API; ntfy does not implement browser EventSource SSE | A literal `text/event-stream` client would not work against ntfy |
| `callbackFlow` + `awaitClose` | Cancellation must cancel the OkHttp call to unblock the socket; verified by test | `job.invokeOnCompletion` fired only after the blocked call returned, leaking the connection |
| Cursor = newest stored message ID (`since=<id>`) | Exact resume without replaying a fixed window; re-evaluated on every attempt | Fixed `since=12h` on reconnect can silently lose older gaps |
| Catch-up (`poll=1`) before live subscribe | Deterministic historical sync, bounded by server drain and a 30 s client timeout | Relying on live-stream replay only |
| Durable dedupe in Room + bounded replay cache | Room is the source of truth; cache only avoids redundant emissions | Unbounded in-memory ID set |
| `AUTHENTICATED` only after a stream opens; `REACHABLE` after health | Health is unauthenticated; claiming credentials were accepted was misleading | Config-derived “SUPERUSER” |
| 404 treated as terminal FORBIDDEN | ntfy returns 404 for unknown *and* unauthorized topics; retrying hot-loops | Retrying every 404 |
| `message_clear` keeps locally saved rows | Saved items are user data, not server state | Deleting everything the server cleared |
| Read timeout 120 s | Detects a dead socket that never sends keepalive | Read timeout 0 (can hang forever) |

## 5. Tests run and outcomes

Environment: Gradle 9.3.1, JDK 21, AGP 9.1.1, Kotlin 2.2.10, minSdk 24.

| Command | Result |
|---|---|
| `./gradlew testDebugUnitTest` | **125 tests, 0 failures, 0 errors, 2 skipped** |
| `./gradlew testDebugUnitTest --rerun-tasks` (gate review, forced, not from cache) | **125 tests, 0 failures, 2 skipped**, 53 s |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL**, `app-debug.apk` 19,193,832 bytes |
| `./gradlew lint` | **BUILD SUCCESSFUL**, **0 errors, 55 warnings** |

Per-class results (from `app/build/test-results/testDebugUnitTest/*.xml`):

| Class | Tests |
|---|---|
| NtfyStreamClientTest (new) | 20 |
| NtfyEventParserTest (new) | 19 |
| NtfyEndpointTest (new) | 14 |
| StreamEngineTest (new) | 10 |
| MessageRepositoryTest (new) | 9 |
| MainViewModelTest | 15 |
| MessageDaoTest | 12 |
| ProfileRepositoryTest | 7 |
| NftyRepositoryTest | 5 |
| InboxScreenTest | 4 |
| ExampleRobolectricTest | 4 |
| MessageMapperTest | 3 |
| SecretStoreTest | 2 (both skipped: AndroidKeyStore unavailable under Robolectric) |
| ExampleUnitTest | 1 |

Coverage of the phase’s testing requirements:

- **SSE/stream parsing**: full-field message, missing required fields, malformed JSON, unknown event, oversized body/line at **parser** level, priority clamp, tags/actions/attachment edge cases. Transport-level oversized-line behaviour is **not** covered by a test (gate review G-04).
- **Authentication**: Bearer header, Basic header, token never in URL, 401 terminal.
- **Reconnect**: cursor passed per attempt, terminal auth stop, drop reported, backoff growth/bounds, stop cancels.
- **Multiple topics**: comma-separated path asserted (`/ops,media/json?...`).
- **Duplicates**: replay guard + durable `store()` returning false + unique index.
- **Deletion**: `message_delete`/`message_clear` parser → stream → repository → Room (saved rows preserved).
- **Persistence**: reopen repository, metadata round-trip, paging windows, per-profile isolation.
- **Offline / timeout / server error**: connection refused, short read timeout classified `TIMEOUT`, 401/403/404/429/5xx classification.
- **Cancellation**: collecting a `NO_RESPONSE` server then cancelling completes promptly; the test failed against the first implementation and passes after the `callbackFlow` fix.

Development note: the first full run had 8 failures. Three were real defects or wrong test assumptions: a genuine cancellation deadlock, org.json coercing non-string tags, and reconnect tests spinning `advanceUntilIdle` against an intentionally infinite retry loop. No test was skipped or weakened to pass.

**Baseline disclosure (gate review G-02):** three Phase 1 tests in `ExampleRobolectricTest` were removed because they constructed `MainViewModel()` with no arguments, which the new constructor forbids. Their behaviours are re-covered in `MainViewModelTest` (`first run without profile…`, `save and delete are persisted…`, `mark all read reaches the repository`), so coverage increased rather than shrank — but the removal was not disclosed in the original version of this report.

## 6. Manual / device tests still required

1. **Real self-hosted smoke test** (blocked: no server credentials or reachable instance provided in this session):
   - health probe; guest topic; token topic; 401/403/404 responses;
   - publish from another client and confirm receipt, cursor resume after force-stop;
   - `message_delete` / `message_clear` against the real server version;
   - reconnect after airplane-mode toggle and Wi-Fi↔mobile handover.
2. **Keystore on hardware**: confirm AES-GCM key is hardware-backed and credentials survive process death; Robolectric cannot attest this.
3. **OEM process death**: confirm stream stops/restarts per lifecycle expectations.
4. **Large-history catch-up**: verify a backlog larger than one poll page and the 30 s timeout behavior.
5. Device verification of the 120 s read timeout against the server’s configured `keepalive-interval`.

## 7. Security / privacy review

- **Keystore inspection**: `KeystoreSecretStore` uses `AndroidKeyStore`, AES-256-GCM (`AES/GCM/NoPadding`, 128-bit tag), provider-generated random 12-byte IV, `setUserAuthenticationRequired(false)`, `setRandomizedEncryptionRequired(true)`, synchronized key creation, and deletes undecryptable/corrupt payloads instead of retrying. Keys never leave the Keystore.
- **No credential logging**: repository-wide scan found no `Log.*`, `println`, `System.out` or analytics calls in `app/src/main/java`.
- **No unsafe TLS handling**: scan found no `X509TrustManager`, `HostnameVerifier`, `setHostnameVerifier`, `checkServerTrusted` or trust-all overrides (only a comment in `SecretStore`). Manifest has no `usesCleartextTraffic` / network-security-config opt-in. HTTPS is enforced in both ViewModel validation and URL building.
- **No secrets in source/Git**: scan for token-like literals, `Bearer <long>`, and hardcoded passwords found none. Secrets are only written through the Keystore store; profile models expose `hasStoredSecret`, never the value.
- **Header safety**: CR/LF stripped from pasted tokens; credentials never placed in query strings or paths.
- **Backup exposure**: the manifest sets `android:allowBackup="false"` and registers `.KaminariApp`, so the Keystore ciphertext/IV pair is not copied to cloud backups.
- **TLS**: platform trust chain only; certificate failures map to `NftyErrorCategory.TLS`.
- **Gate-review caveat (G-06)**: `SecretStore.put` is not guarded — the length `require(...)` and Keystore generation can throw inside `viewModelScope`, so a storage failure during profile save is uncaught. `ProfileRepository.upsertProfile` also erases the stored secret whenever the submitted credential is blank.

## 8. Performance / memory implications

- One stream connection per active profile; no polling loop, no timers.
- Stream lines bounded at 512 KB; body 64 KB; tags/actions bounded; malformed lines only increment a counter.
- Replay guard bounded at 512 IDs; history observation bounded to 300 rows; paging available in the repository.
- Catch-up is a single bounded poll request with a 30 s timeout — no background work added.
- Test-only MockWebServer adds nothing to the APK.
- Combined `test + assembleDebug + lint` in one Gradle invocation exhausted memory and killed the daemon; each task passes when run separately. This is an environment limitation, not an app defect.

## 9. Known issues and risks

| Risk | Severity | Detail / mitigation |
|---|---|---|
| No live server smoke test | **P1 (gate)** | Behavior verified against MockWebServer only; real-server compatibility (version-specific 404/since semantics) unproven |
| Cursor semantics with out-of-order timestamps | P2 | Resume uses the newest stored ID; ntfy orders history by time, so a message arriving later with an older timestamp could be missed until manual re-sync |
| Catch-up bounded to one poll page + 30 s | P2 | Very large backlogs need repeated “Re-sync missed history”; documented rather than hidden |
| Read timeout 120 s | P2 | A server configured with `keepalive-interval > 120s` would reconnect unnecessarily |
| Keystore hardware backing unverified here | P2 | Robolectric skips the 2 SecretStore tests; requires device verification |
| History UI not paged | P3 | Repository supports paging; inbox still observes the bounded 300-row window |
| Profile deletion not exposed in UI | P3 | Repository + ViewModel implement it; no destructive UI added in this phase |
| Lint warnings | P3 | 55 warnings (22 GradleDependency, 10 NewerVersionAvailable, 9 UnusedResources, 5 UseKtx, 5 IconLauncherShape, 4 misc); 0 errors. Phase 1’s “0 warnings” claim is not reproducible with the current `lint` task |
| No notifications/background delivery | P4 | Explicitly out of Phase 2 scope |
| Oversized stream line is not recoverable (G-04) | P2 | An over-limit line raises `EOFException`, ending the stream; reconnect resumes from the same cursor and re-reads it → bounded reconnect loop that never progresses |
| Probe failures mislabelled (G-05) | P2 | `testConnection` reports DNS/TLS/timeout as `AUTHENTICATION_FAILED` |
| Secret erased on blank re-save (G-06) | P2 | `upsertProfile` removes the Keystore entry when `tokenOrPassword` is blank; unguarded `put` can throw |
| Topic-less `message_clear` (G-07) | P2 | Clears all unsaved history for the profile, not one topic |
| Frozen top-bar clock (G-08) | P3 | Live 1 s clock replaced with a static timestamp; not previously documented |
| Phase 1/2 work is uncommitted (G-09) | P3 | 15 modified + 27 untracked files; a single worktree loss erases both phases |

## 10. Rollback / recovery

- No database version change and no migration were introduced, so rolling back code leaves existing local data readable.
- All edits are additive/replaceable files; reverting the listed files restores Phase 1 behavior. No destructive commands, resets or force-pushes were used.
- **No commit exists for Phase 1 or Phase 2.** `HEAD` is the Phase 0 commit; rollback currently means discarding worktree changes, which would also discard Phase 1. A reviewed commit checkpoint is recommended before Phase 3.
- Secrets remain under the same Keystore alias and per-profile keys; a rollback does not orphan credentials for the primary profile.

## 11. Recommendation

**PASS WITH CONDITIONS.**

The communication engine is implemented, integrated, and covered by 125 passing tests including MockWebServer contracts; the build and lint gates pass. The condition is the one gate item this environment cannot satisfy: a real self-hosted smoke test (and on-device Keystore verification) before delivery work depends on it.

## 12. Exact approval needed

Approve Phase 2 and authorize **Phase 3 only** (Android notification channels/permission, deep links/privacy controls, Instant and Battery Saver delivery modes, lifecycle/Doze/process-death handling), with the understanding that the real-server smoke test above is still outstanding.
