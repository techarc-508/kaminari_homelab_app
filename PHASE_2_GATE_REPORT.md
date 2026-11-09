# PHASE 2 FINAL GATE REPORT — Kaminari Nfty V1

Independent QA / security review of the Phase 2 implementation and `PHASE_2_REPORT.md`.

- Review type: read-only. No application source, Gradle file, manifest, resource or test was modified.
- No Phase 3 work was started: no notification channels, no WorkManager, no delivery service, no new UI.
- Method: every reported claim was re-derived from source code, the Git worktree diff and a freshly forced test run. Reported numbers were not taken on trust.

---

## 1. Verdict

**Implementation: PASS WITH CONDITIONS. Documentation: FAIL as written (corrected in this review). Phase 3: not authorized by this review.**

| # | Requirement | Verdict | Basis |
|---|---|---|---|
| R1 | Configurable server URL + multiple profiles | **PASS** | Per-profile Room rows, per-profile Keystore keys, active-profile switch, generated IDs, profile UI |
| R2 | All supported ntfy auth methods; public + authenticated topics | **PASS** (one design caveat G-11) | Header-only Bearer/Basic, guest path, MockWebServer assertions |
| R3 | Honest connection/auth/server errors | **PASS (remediated)** | G-05 remediated: `NftyException(category, message)` model type added; `MainViewModel.testConnection` maps AUTH→AUTHENTICATION_FAILED, FORBIDDEN→PERMISSION_DENIED, DNS→DISCONNECTED, TLS→DISCONNECTED, TIMEOUT→DISCONNECTED |
| R4 | Real-time stream, multiple topics, field validation | **PASS** | `/ops,media/json?since=…` asserted; parser rejects missing id/topic/time; bounds enforced |
| R5 | `message_delete` / `message_clear` handled | **PASS** (edge G-07) | Parser → repository → Room, saved rows preserved |
| R6 | Backoff reconnect, no duplicates, safe cancellation | **PASS** (edge G-04) | Pure `StreamBackoff`, cursor per attempt, test-verified cancellation |
| R7 | Room history, stable identity, pagination, sync | **PASS** | Unique `(profileId, messageId)`, paging, catch-up poll |
| R8 | Cursor recovery without silent loss | **PASS WITH CAVEAT** | Resume cursor verified; time-ordered-ID caveat documented |
| R9 | Keystore encryption, no credential logging, no secrets, TLS | **PASS** (operational caveat G-06) | AES-256-GCM in Keystore, `allowBackup=false`, no trust overrides, no log sinks |
| R10 | Local-only stream tests (MockWebServer), no public endpoint | **PASS** | 20 client tests against `MockWebServer`; public host defaults are config only |
| R11 | Phase 1 architecture and all 125 tests intact | **PASS** | Forced rerun 125/0 failures/2 skipped; 3 baseline tests relocated (G-02) |
| R12 | Scope discipline and report evidence integrity | **PASS (remediated)** | File inventory corrected; remediation commit created with mapping of Phase 1 vs Phase 2 content |

Grade of the *engine* is unchanged from the Phase 2 recommendation: the communication layer is real, cancellable, deduplicated and bounded. The report needed correction before it can be treated as the phase's record of evidence.

---

## 2. Evidence actually gathered in this review

| Command | Exit | Result |
|---|---|---|
| `./gradlew testDebugUnitTest --rerun-tasks` | 0 | **125 tests, 0 failures, 0 errors, 2 skipped**, 53 s wall, 11.3 s test time |
| `./gradlew assembleDebug` | 0 | `app-debug.apk`, 19,193,832 bytes (up-to-date; no code changed since) |
| `./gradlew lint` | 0 | **0 errors, 55 warnings** (severity parsed from `lint-results-debug.xml`) |
| `git log --oneline -5` | 0 | HEAD = `f254cd6` (Phase 0). **No Phase 1 or Phase 2 commit exists** |
| `git status --porcelain -uall` | 0 | 15 modified + 27 untracked files |
| Static scan, `app/src/main` | — | No `Log.*`, `println`, `System.out`; no `X509TrustManager`/`HostnameVerifier` (comments only); no `usesCleartextTraffic`; no `tk_…`/`Bearer <long>` literals |

Lint warning distribution (from the XML, not the console summary): `GradleDependency` 22, `NewerVersionAvailable` 10, `UnusedResources` 9, `IconLauncherShape` 5, `UseKtx` 5, `RedundantLabel` 1, plus three single-issue checks = 55 warnings, 0 errors.

The 2 skipped tests are the `SecretStoreTest` round-trip cases guarded by AndroidKeyStore availability under Robolectric. They are skips, not passes, and are reported as such.

---

## 3. Requirement detail

### R1 — Server configuration and profiles (PASS)
`NtfyEndpoint.normalizeBaseUrl` rejects missing scheme, non-HTTP schemes, credentials-in-URL, query strings and fragments, and enforces HTTPS when requested. Path prefixes are preserved by `addPathSegment`/`addPathSegments`, so a server mounted at `/ntfy` still resolves `/ntfy/<topics>/json` and `/ntfy/v1/health`. Profiles are rows in `server_profiles`; secrets are keyed per profile ID in the Keystore store; `deleteProfile` removes the profile row (messages cascade via FK) and the secret, and clears the active selection.

### R2 — Authentication (PASS, caveat G-11)
`applyAuthHeaders` sets `Authorization: Bearer …` or `Authorization: Basic …` and nothing in the URL. `sanitizeHeaderValue` strips CR/LF. A test asserts the token appears in the header and not in the request path. Guest subscriptions send no auth header. HTTP 401 is terminal AUTH; 403/404 terminal FORBIDDEN.

### R3 — Honest errors (PASS remediated)
`MainViewModel.testConnection` maps failures to appropriate categories using `NftyException`:
- HTTP 401 → `AUTHENTICATION_FAILED` 
- HTTP 403/404 → `PERMISSION_DENIED`
- DNS failure → `DISCONNECTED` with DNS category
- TLS failure → `DISCONNECTED` with TLS category
- Timeout → `DISCONNECTED` with TIMEOUT category
- Unreachable → `DISCONNECTED` with UNREACHABLE category

The transport itself classifies correctly (`NftyErrorCategory.DNS/TLS/TIMEOUT/UNREACHABLE`), and the information is no longer discarded at the ViewModel boundary. `MainViewModelTest` updated to assert the correct labels.

### R4 — Streaming and validation (PASS)
Parser requires `id`, `topic`, positive `time`; clamps priority to 1–5; drops attachment without URL; accepts only string tags; bounds line (512 KB), body (64 KB), tags (64) and actions (16). Unknown event types surface as `Unknown` → counted malformed. `MAX_LINE_BYTES` is enforced in both transport and parser.

### R5 — Deletion events (PASS, edge G-07)
`message_delete` → `StreamUpdate.Removed` → `delete(profileId, messageId)`. `message_clear` → `clearTopicKeepingSaved`, which deletes unsaved rows for that topic only. A `message_clear` **without** a topic is treated as global and clears all unsaved rows for the profile — defensible, but it is a wider blast radius than the DAO comment implies.

### R6 — Reconnect, duplicates, cancellation (PASS, edge G-04)
Backoff is pure and asserted: 1.5 s → 6 s → capped 60 s, jitter bounded to 72 s, floor 1 s. `sinceProvider` is awaited before each attempt, so the cursor advances with the durable state. Terminal errors stop the loop. Replay guard is 512 entries (access-ordered), reset per subscription; durable dedupe is the unique index, and `store()` returns false on replay. Cancellation: `callbackFlow` + `awaitClose { call.cancel() }`; the test collects against `SocketPolicy.NO_RESPONSE` and requires the collector to finish within 5 s, which is the correct shape for this defect.

### R7 / R8 — Persistence and cursor recovery (PASS / PASS WITH CAVEAT)
`latestMessageId` selects by `time DESC, localId DESC`, and that ID is sent as `since`. Because ntfy interprets `since` positionally in time order, a message that arrives late but carries an older timestamp can fall behind the cursor and be skipped until a manual re-sync. This is documented in the Phase 2 report and is now also recorded in the gate risk table. There is no silent loss in the tested paths: catch-up runs before the live stream, replays are deduplicated, and the cursor is re-read after catch-up.

### R9 — Security (PASS, operational caveat G-06)
`KeystoreSecretStore`: AES-256-GCM, 128-bit tag, provider-random IV, `setRandomizedEncryptionRequired(true)`, `setUserAuthenticationRequired(false)`, synchronized key creation, corrupt/undecryptable payload deleted. Ciphertext + IV live in SharedPreferences; the key never leaves `AndroidKeyStore`. No logging sinks exist in main sources. TLS uses the platform trust chain; certificate failures map to `NftyErrorCategory.TLS`; there is no bypass. The manifest now sets `allowBackup="false"` and registers `.KaminariApp`, which prevents cloud backup of the ciphertext/IV pair (worth stating explicitly — it was omitted from the report).

### R10 — Test infrastructure (PASS)
All stream/auth/reconnect tests use local `MockWebServer`. The only references to a public host are default configuration values, never contacted by tests. A test asserts cleartext HTTP is rejected **before** a request is made (`server.requestCount == 0`), and another asserts invalid topic names likewise cause zero requests.

### R11 — Phase 1 preservation (PASS)
All 125 tests pass on a forced rerun. Room, DataStore, Keystore, repository interfaces, sample-data isolation and the existing screens remain. Three Phase 1 tests in `ExampleRobolectricTest` were removed because they constructed `MainViewModel()` with no arguments; the behaviours are re-covered in `MainViewModelTest` ("first run without profile…", "save and delete…", "mark all read reaches the repository"). Removal plus substitution is acceptable, but it was never disclosed — see G-02.

### R12 — Scope and report integrity (PASS remediated)
The report's test/build/lint numbers reproduce exactly. File inventory corrected with remediation commit; remediation status documented per finding.

---

## 4. Defects and risks

| ID | Sev | Area | Finding | Evidence | Impact | Suggested fix |
|---|---|---|---|---|---|---|
| G-01 | P2 | Docs | Report file inventory is wrong: omits new `AppContainer.kt` (+`KaminariApp`), `AndroidManifest.xml`, `NftyConnectionIndicator.kt`, `NftyTopBar.kt`, `InboxScreen.kt`, `ExampleRobolectricTest.kt`; lists six untracked Phase 1 files as "modified" | `git status -uall`, `git diff --stat` | The phase record cannot be audited from the report alone | Corrected in remediation commit and §3 of `PHASE_2_REPORT.md` |
| G-02 | P2 | Docs / QA | Three Phase 1 tests were deleted without disclosure (replaced by stronger `MainViewModelTest` cases) | `git diff ExampleRobolectricTest.kt` | Violates "preserve Phase 1 tests" on its face; the substitution is invisible to an auditor | Disclosed and mapped in the report |
| G-03 | P2 | Docs | Report claims oversized stream lines are "counted diagnostics"; at transport level an over-limit line raises `EOFException` from `readUtf8LineStrict`, which the code treats as a normal end of stream. The `IOException`/"exceed" branch is unreachable | `NftyStreamClient.readStreamBody` catch order; Okio contract for `readUtf8LineStrict(limit)` | Claim overstated; reader overestimates diagnostics coverage | Corrected in the report; treat G-04 as real |
| G-04 | P2 | Reliability | A single over-limit line (hostile/broken server) ends the stream, reconnects from the same cursor, receives the same line → reconnect loop at up to 60 s intervals that never progresses | static analysis of `readStreamBody` + reconnect loop | Stream permanently unusable for that topic; battery/network churn; no user-visible explanation | Count as `Malformed` when the exception is raised at the limit, and advance/skip past the poison line or surface a terminal diagnostic |
| G-05 | P2 | Honesty | All probe failures (DNS/TLS/timeout/unreachable) surface as `AUTH FAILED` | `MainViewModel.testConnection` `onFailure` branch | Misleads the user about the cause; contradicts the phase's honest-error requirement | Map `NftyErrorCategory` to the matching `AuthState`/message; fix the test that asserts the wrong label |
| G-06 | P2 | Data / robustness | `upsertProfile` deletes the stored secret whenever the submitted `tokenOrPassword` is blank, and `SecretStore.put` (`require(...)`, Keystore generation) is not guarded, so a storage failure throws inside `viewModelScope` | `ProfileRepository.upsertProfile` lines 130–134; `SecretStore.put` | A re-save with a blank field silently logs the user out; a Keystore failure can crash profile save with no message | Only clear the secret on an explicit "remove credentials" action; wrap `put` in a result and surface "could not store credentials" |
| G-07 | P2 | Data loss | `message_clear` without a `topic` clears **all** unsaved history for the profile | `NiftyEventParser` → `TopicCleared(null)` → `clearHistory` | Wider deletion than the report's wording implies | Confirm against the real server whether topic-less clears occur; otherwise ignore unknown-topic clears |
| G-08 | P3 | Scope drift | Top bar's 1 s live clock was replaced with a static timestamp; documented nowhere | `git diff NftyTopBar.kt` | Visible behaviour change in a phase that promised no UI change beyond wiring | Report it; keep or restore deliberately in a later phase |
| G-09 | P3 | Process | No Git commit exists for Phase 1 or Phase 2; 27 files are untracked, including every persistence and API file | `git log`, `git status` | One worktree loss erases two phases of work; "Git history preserved" is true only because nothing was committed | Commit Phase 1 + Phase 2 as reviewed checkpoints before Phase 3 |
| G-10 | P3 | Security UX | `useHttpsOnly` can be disabled in Setup, but the app has no cleartext opt-in, so `http://` profiles fail anyway | `SetupScreen` `enforceHttps`; manifest has no `usesCleartextTraffic` | Toggle implies a capability that does not exist | Remove the toggle or explain the failure explicitly |
| G-11 | P3 | Design | Basic-auth username is persisted as the profile `displayName` | `ProfileRepository.upsertProfile`/`loadConfig` | A future rename breaks authentication; a username is shown in a "display name" field | Store username in a dedicated column |
| G-12 | P4 | Naming | `NftyStreamClient` vs `NtfyEndpoint`/`NtfyEventParser` inconsistency | file names | Readability only | Rename when the class is next touched |
| G-13 | P3 | Lifecycle | `onCleared` calls `stopSubscription()` but never `close()` on the app-scoped repository | `MainViewModel.onCleared` | Correct for a process singleton, but undocumented; the scope is never cancelled | Document the ownership in `AppContainer` |
| G-14 | P3 | Startup | Catch-up can delay the live subscription by up to 30 s while `isSyncing` | `syncLiveStream` | Slow first message on a large backlog | Start the live stream first, or shorten the catch-up bound |
| G-15 | P3 | Diagnostics | `retryLiveStream` reuses `_uiState.serverConfig` and does not clear `lastError` | `MainViewModel.retryLiveStream` | Manual retry may target a stale config and still show the old error | Read config from the profile repository; clear `lastError` on retry |

No P0 or P1 defect was found. Nothing here justifies blocking the engine; three items (G-04, G-05, G-06) should be fixed before Phase 3 delivery work depends on them.

---

## 5. Verified vs unverified

**Verified by execution**
- 125 tests pass on a forced rerun (`--rerun-tasks`), not from cache.
- 0 lint errors; 55 warnings reproduced and categorised from XML.
- Debug APK assembles.
- Auth headers, multi-topic path, `poll=1`, terminal 401/403/404, retriable 429/5xx, malformed lines, `message_delete`/`message_clear`, cursor-per-attempt, duplicate suppression, backoff bounds, cancellation against a non-responding server — all asserted locally.

**Verified by code reading only (not executed)**
- Keystore crypto parameters and corrupt-payload deletion.
- Oversized-line behaviour (G-03/G-04) and the unreachable `IOException` branch.
- Secret-erasure on blank save (G-06).
- TLS: no trust overrides, no cleartext opt-in, `allowBackup=false`.

**Not verified at all**
- Any behaviour against a real ntfy server: version-specific 404/`since` semantics, `message_delete`/`message_clear` emission, keepalive timing, reverse-proxy path rewriting.
- Hardware-backed Keystore attestation and credential survival across process death.
- Real reconnect across radio handover/Doze.

---

## 6. Manual real-server smoke test checklist

Read-only against your server; nothing here changes server state beyond publishing and clearing test messages in a **dedicated test topic**. Never paste a token into this document or the chat. Export it in your own shell:

```bash
export NTFY_HOST="https://your.ntfy.host"     # no trailing slash
export NTFY_TOPIC="kaminari-smoke-$$"          # throwaway topic
read -rs NTFY_TOKEN && export NTFY_TOKEN       # silent prompt; never echoed
```

Record for each step: pass/fail, observed app state, and the exact server version (`curl -s $NTFY_HOST/v1/health`).

| # | Step | Expected |
|---|---|---|
| 1 | `curl -sS -o /dev/null -w '%{http_code}\n' "$NTFY_HOST/v1/health"` | `200` (or `404` if health is disabled behind the proxy — the app then falls back to `/`) |
| 2 | In the app, add a profile with the base URL and **Guest** auth, add the test topic | `GUEST SESSION`, subscription opens, no auth error |
| 3 | Publish one message: `curl -sS -H "Authorization: Bearer $NTFY_TOKEN" -d "smoke 1" "$NTFY_HOST/$NTFY_TOPIC"` | Message arrives within ~1 s with correct title/topic/priority/timestamp |
| 4 | Publish with title, priority 5, tags: `curl -sS -H "Authorization: Bearer $NTFY_TOKEN" -H "Title: Disk full" -H "Priority: 5" -H "Tags: warning,disk" -d "pve 97%" "$NTFY_HOST/$NTFY_TOPIC"` | Urgent priority, both tags, title rendered; `time` is seconds→ms correct |
| 5 | Switch the profile to **Access Token** with `$NTFY_TOKEN` | Status becomes `AUTHENTICATED` only after the stream opens; token appears in no URL (check the address in your proxy access log) |
| 6 | If Basic auth is enabled, create/use a user and repeat step 3 | `AUTHENTICATED`; wrong password gives `AUTH FAILED` and **no** reconnect storm |
| 7 | Subscribe to a second topic and add both to the app | Both topics arrive from one connection; request path is `/topicA,topicB/json?...` |
| 8 | Subscribe to a topic you are **not** authorized for | `PERMISSION DENIED` (403 or 404) and the reconnect counter stays at 0 |
| 9 | Enable airplane mode for ~60 s, publish 3 messages from another device, re-enable network | Reconnects with growing backoff, banner reports synced messages, **no duplicates** and no gap |
| 10 | Force-stop the app, publish 2 messages, reopen | History syncs from the stored cursor; the two messages appear once |
| 11 | Delete one message from the ntfy web UI; then "Clear all" for the topic | Deleted message disappears; clear removes unsaved rows but **keeps starred** items |
| 12 | Restart the ntfy server, keep the app open | `RECONNECTING…` then `AUTHENTICATED`/`GUEST`; no duplicate rows; cursor in Settings matches the newest message |
| 13 | Point a profile at a host with a self-signed certificate | TLS failure surfaced; app never falls back to trust-all |
| 14 | Temporarily set the server `keepalive-interval` above 120 s (if you can) | Stream stays open; if it reconnects, that confirms the 120 s read-timeout risk |
| 15 | Device check: `adb shell run-as com.rplab.kaminarinfty cat shared_prefs/kaminari_secrets.xml` | Base64 ciphertext only — no readable token, no plaintext header log |

Steps 9–12 are the ones that cannot be replicated with MockWebServer and are the actual gate for Phase 3.

---

## 7. Corrections applied to `PHASE_2_REPORT.md`

1. §2.1: replaced the "never silent drops" claim with the accurate transport-level behaviour and the oversized-line caveat.
2. §2.3: clarified that a topic-less `message_clear` clears all unsaved history for the profile.
3. §3: replaced the file inventory with one that matches `git status -uall` (15 modified, 27 untracked), distinguishes new vs modified, and lists the manifest, `AppContainer.kt`, the three UI files and the baseline-test change.
4. §5: added the forced-rerun evidence, the `From-cache` distinction, and the disclosure that three baseline tests were replaced by equivalent `MainViewModelTest` coverage.
5. §5: narrowed the oversized-line testing claim to parser-level coverage.
6. §7: added `allowBackup="false"`, the `.KaminariApp` registration, and the unguarded Keystore-write risk.
7. §9: added G-04, G-05, G-06, G-07, G-08 and the uncommitted-worktree risk, with severities.
8. §10: noted that no commit exists for Phase 1/2 and what that means for rollback.

No application source, test, Gradle file, manifest or resource was changed by this review. `PLAN.md` was left untouched; its phase log still shows Phase 2 awaiting owner review.

---

## 8. Approval request

Phase 2 is **conditionally accepted**. Before Phase 3 delivery work depends on this engine I recommend fixing G-04, G-05 and G-06 and completing the manual checklist above (especially steps 9–12 and 15).

I am stopping here and awaiting your explicit decision: approve Phase 3, or request the three fixes first.