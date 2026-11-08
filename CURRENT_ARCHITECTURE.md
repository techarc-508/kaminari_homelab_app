# CURRENT_ARCHITECTURE.md — actual, evidence-based architecture of the prototype

Read-only companion to AUDIT_REPORT.md (same audit session). All citations `file:line`. Target architecture only at the end, clearly separated from facts.

## 1. Package map (backend → frontend flow)

```
com.example
├─ MainActivity.kt (248ln)        ONE activity, edge-to-edge, renders Theme + KaminariApp
├─ MainViewModel.kt (396ln)       MONOLITH: state + nav + stream + topics + theme + URL gate (refs below)
├─ data
│  ├ api/NftyStreamClient.kt(291) OkHttp GETs: /v1/health, /, /{topics}/json?since=12h + NDJSON parser
│  ├ repository/NftyRepository.kt(132) backoff loop (1.5s→30s, expo ×32 cap, ±20% jitter), dedupe set, SharedFlow×64
│  └ SampleDataProvider.kt(179)   8 fake messages/5 fake topics (sample flag never enforced)
├─ model: ServerConfig, NftyMessage, NftyTopic, NftyPriority, NftyErrorCategory (data classes+enums)
└─ ui: theme/{Theme,Color,Type} · navigation/BottomNavigationBar (no NavHost) · components(9) · screens(7)
```

**State ownership:** one `MutableStateFlow<MainUiState>` (`MainViewModel.kt:81`); repository flows (`connectionState`, `lastError`, `incomingMessages`) collected into it in `init` (`:94-131`). Compose is a pure function of this state; actions call ViewModel methods. **Navigation is hand-rolled** enum switch on `AppDestination` (`MainActivity.kt:106`); `androidx.navigation.compose` is declared (`app/build.gradle.kts:105`) but imported nowhere.

## 2. Startup & runtime traces

1. `onCreate` → `enableEdgeToEdge()` → `setContent` → `viewModel()` → theme by `themeSetting` (`MainActivity.kt:39-61`).
2. `MainViewModel.init`: seed sample msgs/topics (`:81-86`), register sample ids for dedupe (`:89-91`), collect repo flows (`:94-131`), **`restartLiveStream()` immediately** (`:134-148`) → connection against default `https://nfty.rplab.qzz.io` as GUEST for the 5 sample topics (`:139-141`).
3. Stream: `repository.startSubscription` → `client.openJsonStream(config, topics, since="12h")` → blocking `execute()` (`NftyStreamClient.kt:140`) inside a `Flow` on `Dispatchers.IO`.
4. **No lifecycle handling.** Stream lives until `onCleared()`; backgrounding does NOT stop networking; `collectAsStateWithLifecycle` only pauses recomposition (`MainActivity.kt:44`).

## 3. Data flow on live message

`NftyStreamClient.parseJsonLine` → `NftyStreamEvent.MessageReceived` → repo dedupe set + emit → `MainViewModel` prepends to sample list, flips `isSampleData=false` once (`:119-131`) → InboxScreen recomposes the whole hand-rolled date-grouping (`InboxScreen.kt:79-98`).

## 4. Restart trigger table (single connection, always full `since=12h` replay per restart)

Call sites of `restartLiveStream()` (9): init, `updateServerConfig` (**per Setup keypress**), `testServerConnection` success, `retryLiveStream`, `setSimulationState(NORMAL)`, `toggleTopicSubscription`, `toggleTopicPause`, `addTopic`, `resetSampleData` (`MainViewModel.kt:134,240,257,274,291,312,322,346,385`).

## 5. Persistence truth table

| Store | Managed code | Declared dep | Declared/unused? |
|---|---|---|---|
| Room | 0 hits (0 @Database/@Dao/@Entity) | `app/build.gradle.kts:106-107,142` | declared, unused |
| DataStore | 0 hits | `app/build.gradle.kts:101` | declared, unused |
| SharedPreferences | 0 writes | — | none |
| Files | 0 writes | — | none |
| In-memory `MainUiState` | ONLY store | — | destructed on death |

`allowBackup="true"` + template-only backup rules (`backup_rules.xml`/`data_extraction_rules.xml` are commented templates): today harmless (nothing persists), **later highly dangerous** when credentials get stored.

## 6. Authentication & authorization model (current)

`ServerConfig{authMethod,username,tokenOrPassword}` — three modes are built at `applyAuthHeaders` (`NftyStreamClient.kt:263-280`): GUEST sends no header, Basic sends `Credentials.basic(username, tokenOrPassword)`, Bearer sends `Bearer <token>`. Values live only inside `MainUiState` (RAM). Test-connection `testServerConnection` → `GET /v1/health` (fallback `GET /`), **200/204 ⇒ AUTHENTICATED when tokenOrPassword non-blank** (`:66-72`) regardless of whether those creds are valid; and AUTHENTICATED is rendered "SUPERUSER" (`ServerConfig.kt:12`). **Transport-connected / authenticated / authorized are conflated** — authority should be *server-stated*, never local inference (docs.ntfy.sh/subscribe/api: auth via Basic or Bearer; no read-admin implication).

## 7. Scheduled/keepalive behavior (facts from docs.ntfy.sh)

- Server keepalive every ~45 s on `/json`; Android-app pattern ⇒ 77 s (`docs.ntfy.sh/subscribe/api/`).
- `since=` accepts a duration, a Unix timestamp **or a message ID** (`docs.ntfy.sh/subscribe/api/`). Release notes v2.31+ warn that reconnecting with `since=<id>` can re-deliver messages from the same second — clients must dedupe by id.

## 8. Deployed dependency reality

| group | declared | actually used |
|---|---|---|
| Compose UI/M3 + BOM 2024.09 | yes | yes (whole UI) |
| OkHttp 4.10, coroutines, activity-compose, lifecycle | yes | yes |
| material-icons-extended | yes | yes (Screens) |
| **Firebase BOM 34.17 + firebase-ai + AppCheck ×2** | yes | **NOT USED, no google-services.json; survives WARN/passthrough** (`app/build.gradle.kts:87` etc. + `gradle.properties:28`) |
| **Retrofit+Moshi+KSP codegen, logging-interceptor** | yes | NOT USED |
| **Room + KSP compiler, DataStore** | yes | NOT USED (awaiting Phase-1 use) |
| navigation-compose | yes | NOT USED (manual enum) |
| espresso / compose-ui-test / turbine / mockwebserver | no/unused | absent |

## 9. Sample data — how fake meets real

`SampleDataProvider` marks items `isSample=true`, but no code reads that flag (`isSample` grep: only provider+model). So: default inbox shows fake alerts; fake "ADMIN" topic permission labels (`NftyTopic.kt:17`); fake latency/cache codes; **and the app auto-subscribes to the fake topic names against the real server.** Owner decision D5 in AUDIT_REPORT covers de-contamination in Phase 1.

## 10. Target architecture (Phase 1+, what ought to exist)

1. **Storage:** Room as source of truth for messages+topics; DataStore for non-secret preferences; secrets encrypted with Android Keystore-backed crypto (exact library decided in Phase 1) — never plaintext DataStore.
2. **Stream removal of blocking reads:** OkHttp call cancellation bridge (`call.cancel()` on `awaitClose`), separate `readTimeout` for health vs stream, proper per-request timeouts; never blocking `execute()` inside a plain `flow{}`.
3. **Backoff policy:** cap attempts, error categories **never** reset by backoff loop; 401/403 → require user action before retry (stop state-flap).
4. **Notification layer:** POST_NOTIFICATIONS permission + runtime prompt; channel set (Alarm/Info/Service/Debug); Instant mode = foreground service stream (reconnect-tolerant); Battery Saver mode = periodic WorkManager poll router (with `since=last-stored-id` catch-up). Channels are universally available on minSdk 24; battery-optimization exemption stays optional, explained, never assumed.
5. **Publish layer** (later, per approved roadmap): `POST /{topic}` with JSON payload, only ever with an outbox retry queue.
6. **E-Ink profile** built as a *first-class profile* (not grayscale): static counters, no timers, no animation, no color-only states; manual refresh key; text-first layout (per AGENTS §9).
7. **Connection/integrity diagnostics screen:** bound event ring buffer (N entries max) + exported sanitized bundle; **not** `Log.d` noise.
8. **Testing:** unit tests (JSON fixtures, URL/auth builders), MockWebServer contract tests, Robolectric for ViewModels+DAO, Compose UI tests for Inbox/Setup flows; instrumented device tests for notification paths.
