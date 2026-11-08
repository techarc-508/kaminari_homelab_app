# AUDIT_REPORT.md — Kaminari Nfty Prototype Deep Audit (Read-Only)

**Date:** 2026-11-08 · **Mode:** READ-ONLY audit (no app source, deps, Gradle, resources, package IDs, signing or server config modified)
**Repo audited:** `KaminariNfty_V1_Plan/repo/kaminari_homelab_app-main/` (extracted 1:1 from `KaminariNfty_V1_Starter_Repo.zip`; original ZIP untouched)
**Evidence base:** coordinator + four independent read-only specialist agents (Android/UI, ntfy/API, Storage/Reliability, QA/Security). Every code claim below carries `file:line` citations. External ntfy claims cite official docs (`docs.ntfy.sh`), current stable **server v2.37.2 (2026-11-03)**.

---

## 1. Executive summary

1. **The prototype is a UI shell with a real read-only stream connection — and nothing durable underneath.** All state lives in one `MutableStateFlow` in RAM (`MainViewModel.kt:81-86`); process death loses messages, read/saved flags, topics, filters, **and credentials**.
2. **It cannot be built on this VM as shipped.** No JDK, no Gradle binary, no Android SDK; and the ZIP itself has no `gradlew` script and no `gradle-wrapper.jar` (only `gradle-wrapper.properties`, `gradle/wrapper/` dir listing). A debug keystore file is also missing while `debugConfig` points at it (`app/build.gradle.kts:35-40`).
3. **The only network operations in the entire codebase are three GETs**: `GET /v1/health` (+ root fallback) and `GET /{topics}/json?since=12h` (`NftyStreamClient.kt:48-178`). **There is no publish, no notification posting, no background delivery, no persistence, no admin capability, no command layer — none exists at any depth.**
4. **Auth decisions are simulated:** HTTP 200 on `/v1/health` plus a *non-blank token field* ⇒ "AUTHENTICATED" (`NftyStreamClient.kt:66-72`), rendered as "SUPERUSER" (`model/ServerConfig.kt:12`). A 200 health response accepts credentials regardless of validity; the test even skips sending them when username is blank.
5. **Sample data contaminates production by design:** default UI state is 8 fake alerts + 5 fake topics, all `isSubscribed=true` (`data/SampleDataProvider.kt:12-61`), and `init` immediately streams the last 12 h **on the owner's real server for these invented topic names** (`MainViewModel.kt:134-148`). The `isSample` flag is never enforced anywhere; the UI card literally claims "Room SQLite" (`InboxScreen.kt:146`) and "38.4 ms" latency (`InboxScreen.kt:133`) — both hardcoded fakes.
6. Per AGENTS.md §2, this report + the three companion documents are the deliverables. **No implementation has begun. Owner approval is required for everything below.**

## 2. Build environment verification (facts + exact commands)

Environment probe on the Mint VM (2026-11-08), commands run verbatim:

| Probe | Command | Result |
|---|---|---|
| java | `command -v java; java -version` | not found (exit 1) |
| JDK roots | `ls /usr/lib/jvm 2>/dev/null; ls ~/.jdks 2>/dev/null` | both absent |
| Gradle | `command -v gradle; gradle --version` | not found |
| Android SDK | `ls /usr/lib/android-sdk 2>/dev/null; command -v sdkmanager adb` | none |
| apt packages | `dpkg -l \| grep -iE '\bopenjdk\b\|gradle'` | none installed |
| Repo git | `git -C . rev-parse --is-inside-work-tree` | not a git repository |
| Wrapper | `ls gradlew gradle/wrapper/gradle-wrapper.jar` | **both absent** (only `gradle-wrapper.properties` with `distributionUrl=gradle-9.3.1`) |

Declared toolchain (`gradle/libs.versions.toml`, `app/build.gradle.kts`): **Gradle 9.3.1 · AGP 9.1.1 · Kotlin 2.2.10 · KSP 2.3.5 · compileSdk 36.1 · minSdk 24 · targetSdk 36 · Java toolchain 11 · Compose BOM 2024.09.00** (risk: very new AGP/Gradle combined with an old Compose BOM 2024.09.00 — unverified without a build).

**Blockers (exact fix commands — owner approval required):**

```bash
# B-1 JDK: install JDK 21 (Gradle 9.3.1 needs Java ≥17)
sudo apt update && sudo apt install -y openjdk-21-jdk-headless

# B-2 Android SDK cmdline tools + platform (public download; run as owner user)
mkdir -p ~/opt/android-sdk/cmdline-tools && cd ~/opt/android-sdk/cmdline-tools
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip commandlinetools-linux-13114758_latest.zip && mv cmdline-tools latest
yes | ~/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
~/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
# local.properties (ignored by .gitignore:4): sdk.dir=/home/pushpal/opt/android-sdk

# B-3 Wrapper jar + gradlew: fetch Gradle 9.3.1 distro, regenerate wrapper
curl -LO https://services.gradle.org/distributions/gradle-9.3.1-bin.zip && unzip gradle-9.3.1-bin.zip
cd ./repo/kaminari_homelab_app-main && ../gradle-9.3.1/bin/gradle wrapper --gradle-version 9.3.1

# B-4 Debug keystore that debugConfig expects at ${rootDir}/debug.keystore (build.gradle.kts:35-40)
keytool -genkeypair -v -keystore debug.keystore -alias androiddebugkey \
  -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

**Assumption (unverified):** VM has internet for Maven Central / google() / gradle distributions / Robolectric android-all jars. None of the above was run — no claim "build passes" is possible yet.

## 3. Feature status inventory (what the user actually gets)

| Capability | Status | Evidence |
|---|---|---|
| View 7 Compose screens (Inbox/Topics/Saved/Search/Detail/Setup/Settings) | **WORKING** (renders) | `MainActivity.kt:39-113`, screen files exist |
| Manual navigation (no Nav-Compose) | WORKING (state enum) | `AppDestination` switch `MainActivity.kt:106`; nav dep declared-unused `app/build.gradle.kts:105` |
| Receive NDJSON stream (message/keepalive events) | **WORKING (foreground only, read-only)** | `NftyStreamClient.kt:116-178`; parse `180-261` |
| `poll_request` handling / scheduled / attachments download | **NOT IMPLEMENTED** (silently ignored, `parseJsonLine:256`) | ntfy docs subscribe/api |
| Publish/send | **NOT IMPLEMENTED** (0 POST builders) | grep verified by API agent |
| OS notifications (permission, channels, service) | **NOT IMPLEMENTED** (only INTERNET + ACCESS_NETWORK_STATE perms) | `AndroidManifest.xml:5-6` |
| Background delivery (Instant/Battery Saver) | **NOT IMPLEMENTED** ("Background Delivery" is a demo text, `SettingsScreen.kt:285-299`) | |
| Persistence (Room/DataStore/state) | **NOT IMPLEMENTED** (deps declared, 0 usage) | storage agent grep table |
| Dedupe | in-memory only, unbounded, dies with process | `NftyRepository.kt:43` |
| Read/saved/delete/clear/search/subscribe/toggle | **LOCAL-ONLY** (list mutations, no side effects) | UI agent table |
| Test-connection ("Test Link") | WORKING GET + **overclaimed success states** (see §4) | `MainViewModel.kt:243-269` |
| Share/clipboard/open-URL confirm gate | WORKING real intents | `MainActivity.kt:230-247`, `NftyConfirmationSheet.kt:119-150` |
| ntfy administration (users/tokens/ACL/etc.) | **NOT IMPLEMENTED, undocumented endpoints would be required** | API agent |
| PVE/homelab commands | **NOT IMPLEMENTED, no executor, no schema, no UI** | — |
| E-Ink profile | **NOT IMPLEMENTED** (SYSTEM/NIGHT/DAY only) | `MainViewModel.kt:40-44` |
| Fake telemetry | FAKE values: "38.4 ms" (`InboxScreen.kt:133`), "Room SQLite" (`InboxScreen.kt:146`), "TLS_AES_256_GCM_SHA384" (`SettingsScreen.kt:369-377`), fake retention "30 Days" (`SettingsScreen.kt:311-321`) | |

**Dead controls (wired to nothing):** Settings "Client Notifications"/"Sound & Vibration" switches; Setup "Enforce HTTPS Only"/"Auto Reconnect" switches (local `remember`, never read — `SetupScreen.kt:431-455`); the `"/"` search hint (no key handler anywhere); "Reload Sample Data" (only flips sim state).

## 4. Top-10 risks (severity-ordered)

| # | Severity | Risk | Evidence |
|---|---|---|---|
| 1 | **P0** | **Not buildable on THIS VM:** missing JDK/SDK/gradlew/wrapper-jar/debug-keystore; AGP 9.x + Compose-BOM-2024.09 compatibility unverified | §2 table |
| 2 | **P0** | **Zero durability:** process death loses conversations, state, credentials; `isSample` flag never enforced; fake "Room SQLite" label | `NftyRepository.kt:43`; `MainViewModel.kt:81-86`; `InboxScreen.kt:146` |
| 3 | **P0** | **No notification pathway** (no POST_NOTIFICATIONS, channels, foreground service, FCM/UnifiedPush) — the core product promise "receive/persist/present" only exists on-screen | `AndroidManifest.xml:5-6` |
| 4 | **P0** | **Auth semantics simulated** ("200 + non-blank token ⇒ AUTHENTICATED/SUPERUSER"; blank-username hole; root 301/302 counted as success) | `NftyStreamClient.kt:66-72, 97-102`; `ServerConfig.kt:12` |
| 5 | **P1** | **Retry-policy bugs:** 401/403 retry forever, state overwritten back to `RECONNECTING` each backoff (auth-flap); `readTimeout(0)` on health GET (can hang forever); blocking `execute()` in flow, no `call.cancel()`; cleartext `http://` URL ⇒ infinite "Stream Interrupted" retry | `NftyRepository.kt:64-120`; `NftyStreamClient.kt:39, 140, 50` |
| 6 | **P1** | **Connection churn:** Setup text field fires `restartLiveStream()` per keystroke (full 12 h replay each char) | `SetupScreen.kt:172-184→285-296`→`VM:238-241` |
| 7 | **P1** | **In-memory everything + unbounded growth:** message list never capped (`VM:119-131`), dedupe set grows forever (`NftyRepository.kt:43`), every reconnect replays fixed `since=12h` (older history unreachable forever) | — |
| 8 | **P1** | **Transport security gaps:** no `networkSecurityConfig`; minSdk 24 → default cleartext allowed on API 24-27; HTTPS-only switch dead; `allowBackup=true` with template backup rules (harmless now, **high** once secrets land) | `AndroidManifest.xml:9`; `backup_rules.xml`/`data_extraction_rules.xml` (empty) |
| 9 | **P2** | **Dependency/config debt:** Firebase BOM+AI+AppCheck compiled in (0 usage, no google-services.json); Retrofit/Moshi/Room/DataStore/logging-interceptor declared-unused; hollow google-services + secrets plugin 2.0.1 against `.env`; `my-upload-key.jks` not gitignored; AI Studio leftovers (`metadata.json`, GEMINI placeholder) | `app/build.gradle.kts:87-128`; `.gitignore:18` |
| 10 | **P2** | **UX/a11y/E-Ink gaps:** 1 s tick loop in TopBar (`NftyTopBar.kt:69-76`); sub-48 dp targets ×5; dot-only color states; strings hardcoded (`strings.xml` has only `app_name`); no E-Ink profile; `ModalBottomSheet`+`Snackbar`+`AnimatedVisibility` motion on e-ink hostile paths | UI agent table |

## 5. Tests & verification baseline

**No build/test/lint has ever run on this VM** (toolchain absent) — none can be claimed passed and none are claimed so.

Static test inventory: 3 test files, **8 tests / 12 assertions**. `ExampleUnitTest.kt:14` is the stock 2+2; `ExampleInstrumentedTest.kt:20` asserts package identity; `ExampleRobolectricTest.kt:28-74` smoke-tests sample data state toggles, config defaults and enum mapping — *these tests currently depend on sample-data contamination* (`:36-39`). MockWebServer, Turbine, coroutines-test, Compose-test are **declared-absent or unused**; **zero coverage**: stream parsing, reconnect/backoff, auth semantics, publish, persistence, notifications, Doze/process-death, Compose UI semantics.

## 6. Prior-audit claim verification (REPOSITORY_AUDIT.md)

Every assigned claim from the pre-existing `REPOSITORY_AUDIT.md` revisited by the matching agent, with the *actual* ZIP as ground truth: **all CONFIRMED or PARTIAL+extras, none refuted.** Highlights: (a) no gradlew/jar; (b) `namespace="com.example"` + `applicationId="com.aistudio.kaminarinfty.rzxqpt"` (`build.gradle.kts:12,16`); (c) Firebase/AppCheck present + hollow google-services; CameraX/Coil/gms-location commented out, zero usages; (d) release signing resolved from `KEYSTORE_PATH/STORE_PASSWORD/KEY_PASSWORD` env vars with default keystore path, debug signing conventional;`my-upload-key.jks` not gitignored; (e) tests example-level; (f) manifest only INTERNET + ACCESS_NETWORK_STATE; (g) no publish; (h) no persistence; (i) 12 h fixed replay; (j) `parseJsonLine` swallow pattern. Refinements: `readTimeout(0)` applies to **health probes too** (permanent hang risk), and 401/403 flap by overwriting state at `NftyRepository.kt:117`.

## 7. Secrets & privacy scan

Tree-wide case-insensitive scan (agent QA/E): **no real secrets.** `.env.example:7` has only a commented `GEMINI_API_KEY` placeholder; `metadata.json:5` confirms AI-Studio `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API`. **Real infra disclosure:** default `https://nfty.rplab.qzz.io` hardcoded (`ServerConfig.kt:21`) and repeated in sample data/topic URLs (`SampleDataProvider.kt:78-136`). Debug keystore in-tree would be committed if not for `.gitignore:18` (present, OK); `my-upload-key.jks` absent and **not** gitignored.

## 8. Decisions the owner must make before Phase 1

| # | Decision | Options (recommendation bold) |
|---|---|---|
| D1 | Approve toolchain install commands of §2 | **yes** / no (no build ⇒ no Phase 1) |
| D2 | `applicationId` keep `com.aistudio.kaminarinfty.rzxqpt` or rebrand | **rebrand later, keep for Phase 1** (changing once installed loses state) |
| D3 | Server version check permission: run `ntfy version` via SSH on nfty.rplab.qzz.io, or approved `curl https://nfty.rplab.qzz.io/v1/health` from the VM | **SSH CLI (safe, read-only)** |
| D4 | git init at repo root (needed for change discipline per AGENTS §12) | **yes** |
| D5 | Accept Phase 1 will remove/disable sample-mode-as-default UI (demo data goes behind an explicit toggle) | **yes** |
| D6 | Defer vendor-Boox tuning (per locked decision) and defer publish/admin phases until core receive/persist/notify done | **yes** |

---
**Recommendation: PROCEED to Phase 0 after D1–D6 approval.** No Phase 1 work, no source modification, no server interaction happened during this audit. Companion docs: `CURRENT_ARCHITECTURE.md`, `API_CAPABILITY_MATRIX.md`, `PHASE_0_EXECUTION_PLAN.md`.
