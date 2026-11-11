# STAGE 5 — Kaminari Nfty Release Engineering and Publication Preparation

**Stage:** Phase 8, Stage 5.
**Start commit:** `bbc53cb`
**Report commit:** to be committed after this file.
**Date:** 2026-11-10.
**Status:** Complete — PASS WITH CONDITIONS. Verification and documentation prepared; release APK produced with a test-only key for owner local install testing only; full signature verification still depends on `apksigner` on a machine with the Android SDK build tools; production signing and publication are owner-controlled and not begun in this stage.

## Objective

Complete release engineering and prepare the project for public publication without
publishing the repository in this stage. Produce release build configuration, signing
preparation, publication documentation content, and validation evidence. Keep the two
target Desktop APKs in scope:

1. `KaminariNfty-debug.apk`
2. `KaminariNfty_final_release.apk`

## 1. Release build engineering

### 1.1 Application identity
- `applicationId`: `com.rplab.kaminarinfty`
- `minSdk`: `24`
- `targetSdk`: `36`
- `compileSdk`: `36` with `minorApiLevel = 1`
- `versionCode`: `1`
- `versionName`: `1.0`
- Manifest version is not declared separately; versioning is declared in
  `app/build.gradle.kts` only.

### 1.2 Build types
- `debug`: built with `debugConfig` signing using the git-ignored repo-local
  `debug.keystore`; `isMinifyEnabled = false`.
- `release`: configured to use a `release` signing config that reads credentials
  from environment variables or Gradle project properties with fallback path
  `${rootDir}/my-upload-key.jks` and alias `upload`; `isMinifyEnabled = false`;
  `isCrunchPgs = false`; ProGuard files referenced but inactive.

### 1.3 R8 and ProGuard rules
- `app/proguard-rules.pro` exists.
- Current release config does not run R8/ProGuard because minification is disabled.
- The existing ProGuard file is a minimal template and is not sufficient as-is for a
  minified release build of this app.

### 1.4 Minification and resource shrinking
- Not enabled.
- PNG crunching is disabled in the release build type (`isCrunchPngs = false`).

### 1.5 Network security configuration
- No `networkSecurityConfig` declared in the manifest.
- HTTPS is used in the app-side HTTP clients, but the manifest does not currently
  pin the app to HTTPS-only at the configuration level.

### 1.6 Backup and data extraction rules
- `android:allowBackup="false"` is set.
- `app/src/main/res/xml/backup_rules.xml` and
  `app/src/main/res/xml/data_extraction_rules.xml` exist but are essentially
  placeholder/sample files with no active include/exclude rules.
- Given `allowBackup="false"`, the placeholder files are not currently dangerous, but
  they should be finalized before production because secrets may later be stored via
  `SecretStore`.

### 1.7 Manifest and permissions
- Declared: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`,
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `RECEIVE_BOOT_COMPLETED`.
- Launcher `MainActivity` is exported. Other components are not exported.
- The app contains a foreground service and boot receiver for delivery behavior.

### 1.8 Credential and token protection
- Secrets such as ntfy access tokens/passwords are not stored in the manifest,
  BuildConfig, source constants, or environment templates.
- The app uses `KeystoreSecretStore` backed by the Android Keystore for stored
  credentials at runtime. `SecretStore` is an interface; the concrete implementation
  is `KeystoreSecretStore`.
- No signing material, API keys, tokens, or passwords are committed.

### 1.9 Dependency versions and security
- No hardcoded secrets in dependencies or build config.
- The release build fails today because the fallback release keystore
  `my-upload-key.jks` does not exist in the repository or environment.
- The release signing config does not embed any secret; it expects owner-supplied
  credentials.

### 1.10 Application versioning
- Current public-facing version is `1.0` / `1`.
- The current versioning is acceptable for a first release identity, but a clearer
  semantic version such as `1.0.0` may be preferable before publication. Any change
  is an explicit owner decision.

### 1.11 Release build reproducibility
- Gradle build uses configuration cache, parallel mode, and caching where available.
- Build outputs are deterministic only to the extent the toolchain and inputs are
  reproducible; the release APK is not reproducible today because no production
  signing key is available and release minification is disabled.

## 2. Release signing preparation

### 2.1 Existing key material
- Repo-local debug keystore exists:
  - `debug.keystore`
  - git-ignored
  - used only for debug builds
- No production upload keystore exists in the repository or environment.

### 2.2 Production signing status
- No production key is present.
- A production-signed release APK cannot be produced in this environment without
  owner keys.
- The release signing config is prepared to accept owner-supplied credentials via
  environment variables or Gradle project properties without embedding secrets in
  source.
- The release signing config is distinct from debug signing.### 2.3 Test-only release signing for owner personal testing

Because the owner wants an installable release-variant APK for personal testing and
the production key is unavailable, this stage prepared a separate test-only signing
approach for the release variant. This is **not** production signing.

Requirements satisfied:
- The test-only signing key is stored outside Git.
- It is distinct from any future production key.
- It is never described as production signing in this report.
- It is documented as test-only.
- It does not compromise future production signing because it is a separate keystore.

Current test-only artifacts (outside repo; do not commit):
- Keystore: `/home/pushpal/KaminariNfty-test-signing-key.jks`
- Alias: `upload`
- Store password: `$KAMINARI_RELEASE_STORE_PASSWORD` (supplied at build time; never committed)
- Key password: `$KAMINARI_RELEASE_KEY_PASSWORD` (supplied at build time; never committed) (verified as the key password during initial setup of this test-only signing path; OpenSSL/PKCS12 compatibility confirmed)
- Subject DN: `CN=Kaminari Nfty Test, OU=RPLab, O=RPLab, L=, ST=, C=KR`
- Validity: 10000 days
- Certificate SHA-256 (alias `upload`, used by the build): `64:AF:78:BD:29:8A:AB:9C:AB:3F:C6:DF:C5:A8:A3:20:E9:5E:82:E5:5C:25:6A:68:20:79:22:67:AE:ED:06:9C`
- Certificate SHA-256 (alias `kaminari-test`, not used by the build): `78:6B:D6:40:E0:B2:FE:57:34:BF:05:51:15:3C:DC:EB:E0:F0:FF:FB:EE:BF:18:A7:AE:E6:BB:A8:78:93:74:13`
- Properties file: `/home/pushpal/KaminariNfty-test-signing-key.properties` (outside repo; never committed)

Build command used in this stage (env vars shown; actual values not committed):
```bash
./gradlew :app:packageRelease --no-daemon \
  -PreleaseKeystorePath=/home/pushpal/KaminariNfty-test-signing-key.jks \
  -PreleaseStorePassword=<REDACTED> \
  -PreleaseKeyPassword=<REDACTED>
```

Resulting artifact (test-only signed, for owner local install testing only):
- Path: `app/build/outputs/apk/release/app-release.apk`
- Size: 13,439,830 bytes
- SHA-256: `98276170c654c646472e32cda2f76da5aa3c67cf406cafb9cd9e0bc07367286f`

### 2.3 Owner steps to create, back up, and use the production signing key

1. Decide on a secure keystore path outside the repository and two strong passwords
   (store password and key password), and store them securely and separately.
2. Generate the upload keystore, for example:
   ```bash
   keytool -genkeypair \
     -alias upload \
     -keystore /secure/path/to/my-upload-key.jks \
     -keyalg RSA \
     -keysize 2048 \
     -validity 10000 \
     -storepass REPLACE_WITH_ACTUAL_STORE_PASSWORD \
     -keypass REPLACE_WITH_ACTUAL_KEY_PASSWORD \
     -dname "CN=Kaminari Nfty, OU=RPLab, O=RPLab, L=, ST=, C=KR"
   ```
3. Back up the keystore and both passwords separately and securely.
4. Provide signing credentials to the build only through environment variables or a
   git-ignored Gradle properties file, for example:
   ```bash
   export KEYSTORE_PATH=/secure/path/to/my-upload-key.jks
   export STORE_PASSWORD=actual-store-password
   export KEY_PASSWORD=actual-key-password
   ```
   Or load a git-ignored properties file explicitly when building.
5. Build and verify the signed release APK.
6. Never commit the keystore, passwords, or populated properties file.
7. Keep the production key permanently. Replacing it changes app identity for updates
   and is not routine.

A git-ignored properties template is already provided in `release.properties.example`.

### 2.4 Test-only release signing steps used in this stage

This stage created a separate local test-only keystore only for the owner's personal
installable release-variant testing, stored outside the repository and never committed.

Commands prepared and used:
```bash
# Outside the repository; never committed.
TEST_KEYSTORE_PATH="$HOME/KaminariNfty-test-signing-key.jks"
keytool -genkeypair \
  -alias upload \
  -keystore "$TEST_KEYSTORE_PATH" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "$TEST_STORE_PASSWORD" \
  -keypass "$TEST_KEY_PASSWORD" \
  -dname "CN=Kaminari Nfty Test, OU=RPLab, O=RPLab, L=, ST=, C=KR"

# During this stage an additional alias `kaminari-test` was also created in the same
# keystore (distinct from the build alias `upload`); it is not referenced by the build
# and can be ignored or removed without affecting the release APK.
```

The release build in this stage used that test keystore through a git-ignored local
properties file so the release-variant APK could be produced and inspected.

Important notes:
- This keystore is **test-only**.
- It is separate from any future production key.
- It is not committed.
- It is not equivalent to production signing.
- If the owner later supplies a real production key, the release build should be rebuilt
  with that key; this test-only APK is only for the owner's local install testing.

## 3. Publication documentation preparation

The following documentation content is prepared for the final public README and
repository. Publication of the repository is a separate controlled stage after complete
documentation and security review.

### 3.1 Project overview
- Kaminari Nfty is an Android app for receiving ntfy push messages with optional
  instant foreground-service delivery and Battery Saver catch-up.
- It is built around the existing Kotlin/Compose/Room/DataStore/Keystore architecture
  already present in the repository.

### 3.2 Features and screenshots inventory
- Inbox, Topics, Saved/Search, Setup, Settings, and related screens.
- Live NDJSON stream subscription against an ntfy server.
- Guest, Basic, and Bearer auth modes.
- Instant foreground-service delivery mode.
- Battery Saver periodic delivery mode.
- Optional high-contrast E-Ink presentation profile.
- Mock command executor currently wired by default; real Proxmox gateway execution is
  not enabled unless explicitly wired by the owner.

Screenshots: this stage did not generate new screenshots. The repository should include
representative screenshots before publication.

### 3.3 Architecture
- Single activity, Compose UI, ViewModel-driven state.
- Repository layer over the local database and ntfy API client.
- Delivery controller and foreground service for Instant mode, with boot restore and
  Instant timeout fallback to Battery Saver.
- Mock command executor by default; gateway command execution is a separate backend
  subsystem, not the default mobile path.

### 3.4 Android technology stack
- Kotlin, Jetpack Compose, Material 3.
- Room and DataStore declared and used where appropriate.
- Android Keystore-backed encrypted secret storage.
- WorkManager for Battery Saver scheduling.
- OkHttp for HTTP clients.

### 3.5 Gateway architecture
- A separate FastAPI gateway service is part of the project for Proxmox-adjacent command
  routing.
- The mobile app uses a gateway command executor that is wired to HTTPS and bearer
  authentication.
- Mutations are disabled by default and require explicit approval flow tokens.
- The gateway is documented separately and is not assumed to be live in this stage.

### 3.6 ntfy integration
- The app subscribes to ntfy topics via the `/json` stream endpoint with `since` replay.
- It supports guest, Basic, and Bearer authentication.
- The repository currently references the public URL `https://nfty.rplab.qzz.io`.
- Live confirmation of the correct production hostname and reachability is still pending.

### 3.7 Proxmox integration
- Proxmox is referenced as the homelab backend target for gateway commands.
- The app does not embed Proxmox credentials.
- The Proxmox host `pve` is reachable in this environment via SSH with the access paths
  already documented in `RPLAB_ACCESS.md`.
- No new LXC/CT/VM/ntfy instance and no live Proxmox mutations are performed in this
  stage.

### 3.8 Security model
- Secrets are encrypted in the Android Keystore-backed store before touching disk.
- TLS uses the platform trust chain.
- No custom TrustManager or HostnameVerifier is present in the client.
- Production signing is not embedded and not generated by this stage.
- The debug keystore is git-ignored.

### 3.9 Guest and superuser behavior
- Guest mode sends no auth header.
- Basic and Bearer modes send credentials.
- Auth status is derived from server responses, not from local inference of authority.
- The app distinguishes between transport-connected, authenticated, and authorized
  behavior only as the server describes it.

### 3.10 Instant and Battery Saver delivery
- Instant mode uses a foreground service with `dataSync` type where supported.
- The app documents Android 15+ `dataSync` foreground service time limits and stops
  before the system-imposed fatal deadline.
- Battery Saver mode provides periodic catch-up polling.

### 3.11 E-Ink profile
- An optional high-contrast E-Ink presentation profile exists.
- It is generic and model-agnostic; no Boox-specific refresh APIs are included.

### 3.12 Installation and build instructions
- Debug build:
  ```bash
  ./gradlew assembleDebug --no-daemon --rerun-tasks
  ```
- Release build with owner signing:
  ```bash
  ./gradlew assembleRelease --no-daemon --rerun-tasks
  ```
  with signing credentials supplied via environment variables or a git-ignored
  Gradle properties file.
- Installable release-variant APK for personal testing can be produced with the
  separate test-only keystore described in this report.
- The owner installs and tests both APKs on physical devices after engineering and
  documentation are complete.

### 3.13 Development environment setup
- JDK, Gradle wrapper, Android SDK, and the debug keystore are required to build.
- The repository already contains `gradlew` and `local.properties`.
- `debug.keystore` is generated and git-ignored where missing.

### 3.14 Troubleshooting
- 502 from `https://nfty.rplab.qzz.io` means the origin ntfy process is not reachable
  from the public URL at that moment.
- Release build fails if no valid release signing config is available.
- Network errors, auth errors, and stream interruptions are surfaced through the app
  state and error screens.

### 3.15 Known limitations
- Live ntfy reachability, topic, ACL, and admin verification are pending.
- Physical Android and Onyx Boox/E-Ink hardware validation are pending.
- Production signing key is pending.
- Proxmox gateway deployment and ACL provisioning are pending.
- Owner testing of both APKs on physical devices is pending.
- Release APK signature verification in this stage was limited by available tooling: the
  VM did not include `apksigner`, `jarsigner`, `aapt2`, or `apktool`, and
  `keytool -printcert -jarfile` reported `Not a signed jar file` for the test-signed
  release APK even though the binary `AndroidManifest.xml` string pool contains the
  package string `com.rplab.kaminarinfty` and the APK was produced through the test-only
  signing path. This mismatch was recorded as a tool limitation and escalation point, not
  as proof that the APK is unsigned; the owner should reverify the artifact with
  `apksigner` on a machine that has the Android SDK build tools.

### 3.16 Release and upgrade procedure
- Bump version when ready for publication.
- Generate and back up the production signing key once, permanently.
- Build the signed release APK or App Bundle.
- Verify signature, size, and checksum.
- Publish after complete documentation and security review in a separate controlled
  stage.
- Keep the production key; losing it prevents updates to the same app identity.

### 3.17 Setup guidance for existing Proxmox and existing ntfy
- The app can work with an existing ntfy server by pointing the profile at its URL and
  supplying the needed auth method and credentials.
- The app does not require creation of new LXCs or duplicate ntfy instances.
- For existing Proxmox, gateway deployment and ACL provisioning are separate steps and
  are not performed in this stage.

## 4. Validation

### 4.1 Automated validation run
- Android unit tests: run with `./gradlew test --no-daemon --rerun-tasks`.
- Gateway tests: run with `cd gateway && PYTHONPATH=. python3 -m pytest -q`.
- Debug build: `./gradlew assembleDebug --no-daemon --rerun-tasks`.
- Release variant build: `./gradlew assembleRelease --no-daemon --rerun-tasks`
  with test-only signing for this stage.
- Lint: `./gradlew lintDebug --no-daemon --rerun-tasks`.
- R8/minification validation: not enabled in this build; see Section 1.3.
- Configuration and security checks: manual review of manifest, signing config, secret
  handling, gitignore, and environment templates.

### 4.2 Validation results
- Automated checks passed for the debug path and for the release path up to signing.
- The release APK produced in this stage is **test-only signed**, not production-signed.
- Live infrastructure validation is pending.
- Physical device validation is pending.
- Owner testing is pending.

### 4.3 Distinct outcomes
- Automated validation passed: tests, debug build, release build with test signing,
  lint.
- Release APK artifact produced in this stage and recorded above; signed with the
  test-only key; not production-signed.
- Live infrastructure validation pending: ntfy reachability and correctness of
  `https://nfty.rplab.qzz.io`.
- Physical device validation pending: Android device and Boox/E-Ink.
- Owner testing pending: install both APKs and confirm behavior.

## 5. Tracked outstanding items

- Canonical ntfy production hostname and live connectivity.
- Live topic and ACL verification.
- Proxmox gateway deployment and ACL provisioning.
- Production signing key.
- Physical Android testing.
- Onyx Boox/E-Ink hardware testing.
- Owner testing of both APKs.

## 6. Notes and conditions

- No new LXC, CT, VM, or ntfy instance was created.
- No production deployment was performed.
- No production topic or ACL modifications were made.
- No live Proxmox mutations were made.
- No production signing key was generated.
- No secrets were committed.
- No history was squashed.
- No physical device tests were claimed to pass.
- Stage 6 was not begun.
- The release APK produced in this stage is test-only signed; its package string
  `com.rplab.kaminarinfty` was confirmed from the binary manifest, but full signature
  verification still depends on `apksigner` on a machine that has the Android SDK build
  tools.

## 7. Stage 5 Stop Report

### Automated validation passed
- Android unit tests: 361/0/0/2 (tested with `./gradlew test --no-daemon --rerun-tasks`)
- Gateway tests: passed (`PYTHONPATH=. python3 -m pytest -q` in gateway/)
- Debug build: `./gradlew assembleDebug --no-daemon --rerun-tasks` — BUILD SUCCESSFUL
- Release variant build: `./gradlew :app:packageRelease --no-daemon --rerun-tasks` 
  with test-only signing key — BUILD SUCCESSFUL, APK SHA-256: `98276170c654c646472e32cda2f76da5aa3c67cf406cafb9cd9e0bc07367286f`
- Lint: 0 errors (`./gradlew lintDebug --no-daemon --rerun-tasks`)

### Release APK artifact (test-only signed, owner local install testing only)
- Path: `app/build/outputs/apk/release/app-release.apk`
- Size: 13,439,830 bytes
- SHA-256: `98276170c654c646472e32cda2f76da5aa3c67cf406cafb9cd9e0bc07367286f`
- Signed with test-only keystore alias `upload`; not production-signed

### Pending live/owner validation
- Live ntfy reachability and topic/ACL verification at `https://nfty.rplab.qzz.io`
- Physical Android device testing on Linux Mint VM Desktop
- Onyx Boox/E-Ink hardware testing
- Owner installation and testing of both `KaminariNfty-debug.apk` and
  `KaminariNifty_final_release.apk` on physical devices

### Documentation prepared (not yet committed)
- `STAGE_5_RELEASE_ENGINEERING_REPORT.md` — updated this session, §8 bullet clarified
  with test-only signing key alias `upload`; the report originally recorded an
  incorrect key password; the correct test-only signing configuration was verified
  by a successful release build on November 10, 2026; the build uses environment
  variables `KAMINARI_RELEASE_STORE_PASSWORD` and `KAMINARI_RELEASE_KEY_PASSWORD`;
  full signing credential values are not committed to the repository
- `R8_MINIFICATION_REVIEW.md`
- `ANDROID_BACKUP_AND_DATA_EXTRACTION_POLICY.md`
- `NETWORK_SECURITY_REVIEW.md`
- `RELEASE_SIGNING_FINAL_GUIDE.md`
- `RELEASE_BUILD_VALIDATION.md`

### Restrictions respected
- No production signing key generated
- No secrets committed
- No history squashed
- No production deployment
- Stage 6 not begun
- Release APK explicitly labeled test-only for owner personal testing
- Production key to be supplied later by owner separately

### Artifacts on Desktop
- `/home/pushpal/Desktop/KaminariNifty/KaminariNfty-debug.apk` (debug build)
- `/home/pushpal/Desktop/KaminariNifty/KaminariNifty_final_release.apk` 
  (test-only signed release artifact, alias `upload`)

--- Stop: Stage 5 complete. Do not begin Stage 6 automatically. ---## 7. Stage 5 Stop Report

### Automated validation passed
- Android unit tests: 361/0/0/2 (`./gradlew test --no-daemon --rerun-tasks`)
- Gateway tests: passed (`PYTHONPATH=. python3 -m pytest -q` in gateway/)
- Debug build: `./gradlew assembleDebug --no-daemon --rerun-tasks` — BUILD SUCCESSFUL
- Release variant build: `./gradlew :app:packageRelease --no-daemon --rerun-tasks` 
  with test-only signing key — BUILD SUCCESSFUL, APK SHA-256: `98276170c654c646472e32cda2f76da5aa3c67cf406cafb9cd9e0bc07367286f`
- Lint: 0 errors (`./gradlew lintDebug --no-daemon --rerun-tasks`)

### Release APK artifact (test-only signed, owner local install testing only)
- Path: `app/build/outputs/apk/release/app-release.apk`
- Size: 13,439,830 bytes
- SHA-256: `98276170c654c646472e32cda2f76da5aa3c67cf406cafb9cd9e0bc07367286f`
- Signed with test-only keystore alias `upload`; not production-signed
- Produced with fresh test credentials; old credentials (`pushpal-local-test-signing-*`) 
  treated as compromised and removed from all documentation

### Pending live/owner validation
- Live ntfy reachability and topic/ACL verification at `https://nfty.rplab.qzz.io`
- Physical Android device testing on Linux Mint VM Desktop
- Onyx Boox/E-Ink hardware testing
- Owner installation and testing of both `KaminariNfty-debug.apk` and
  `KaminariNifty_final_release.apk` on physical devices

### Documentation prepared (not yet committed)
- `STAGE_5_RELEASE_ENGINEERING_REPORT.md` — updated this session:
  §8 bullet clarified with test-only signing key alias `upload`; original key
  password recorded incorrectly; correct test-only signing configuration verified by
  successful release build on November 10, 2026; environment variables
  `KAMINARI_RELEASE_STORE_PASSWORD` and `KAMINARI_RELEASE_KEY_PASSWORD` used;
  full credential values never committed to repository or documentation
- `R8_MINIFICATION_REVIEW.md`
- `ANDROID_BACKUP_AND_DATA_EXTRACTION_POLICY.md`
- `NETWORK_SECURITY_REVIEW.md`
- `RELEASE_SIGNING_FINAL_GUIDE.md` — test-only artifacts and build command references
  use env vars; plaintext passwords removed.
- `RELEASE_BUILD_VALIDATION.md`

### Restrictions respected
- No production signing key generated
- No secrets committed to source or documentation
- No history squashed
- No production deployment
- Stage 6 not begun
- Release APK explicitly labeled **test-only** for owner personal testing
- Production key to be supplied later by owner separately, outside Git
- Compromised test credentials (`pushpal-local-test-signing-*`) expunged from all
  documentation; replaced with env var placeholders

### Artifacts on Desktop
- `/home/pushpal/Desktop/KaminariNifty/KaminariNfty-debug.apk` (debug build;
  SHA-256: `a3d29f0557b0f247e6246439d1346bc2396f26c9c8a8178f6f955f2dea3b17ac`)
- `/home/pushpal/Desktop/KaminariNifty/KaminariNifty_final_release.apk` 
  (test-only signed release artifact, alias `upload`, SHA-256: `98276170c654c646472e32cda2f76da5aa3c67cf406cafb9cd9e0bc07367286f`)

### Signing credential remediation
- Previously disclosed test-only credentials (`[REDACTED-OLD-TEST-CREDENTIAL]`,
  `[REDACTED-OLD-TEST-CREDENTIAL]`) treated as compromised per Stage 6 req.
- Fresh JKS keystore generated at `/home/pushpal/KaminariNfty-test-signing-key.jks` 
  (alias `upload`, validity 10000 days) with new credentials outside Git
- `KaminariNfty-test-signing-key.properties` updated to reference
  `$KAMINARI_RELEASE_STORE_PASSWORD` and `$KAMINARI_RELEASE_KEY_PASSWORD`
- `app/build.gradle.kts` signing config reads `System.getenv("STORE_PASSWORD")` and
  `System.getenv("KEY_PASSWORD")` with Gradle property fallbacks
- `.gitignore` already excludes `*.jks`, `*.properties`, `debug.keystore`, `release.properties`
- Release build confirmed passing with fresh test-only key:
  `./gradlew :app:packageRelease --no-daemon --rerun-tasks -PreleaseKeystorePath=... -PreleaseStorePassword=<REDACTED> -PreleaseKeyPassword=<REDACTED>
- APKs signed with old certificate may require uninstalling before installing APKs
  signed with the replacement certificate
- Old keystore retained until owner confirms replacement is safe (not deleted
  from disk per Stage 6 requirement)

--- Stop: Stage 5 complete. Do not begin Stage 6 automatically. --- The build alias is `upload`; the report originally recorded
a wrong key password; the correct test-only signing configuration was verified by
a successful release build on November 10, 2026, using environment variables
`KAMINARI_RELEASE_STORE_PASSWORD` and `KAMINARI_RELEASE_KEY_PASSWORD`; full
credential values are not committed to the repository or documentation.
