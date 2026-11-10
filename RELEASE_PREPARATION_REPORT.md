# Kaminari Nfty — Release Preparation Report

**Stage:** Phase 8, Stage 2.
**Start commit:** `84e6c3f`
**Report commit:** to be committed after this file.
**Date:** 2026-11-10

## 1. Release configuration inspected

### 1.1 Application identity
- `applicationId`: `com.rplab.kaminarinfty`
- `versionCode`: `1`
- `versionName`: `1.0`
- `minSdk`: `24`
- `targetSdk`: `36`
- `compileSdk`: `36` with `minorApiLevel = 1`
- Manifest version: not declared separately in `AndroidManifest.xml`; version is
  declared in `app/build.gradle.kts` only.

### 1.2 Build types
- `debug`: built with `debugConfig` signing using the repo-local
  `debug.keystore`; `isMinifyEnabled = false`.
- `release`: configured to use a `release` signing config that reads from
  environment variables or Gradle project properties; `isMinifyEnabled = false`;
  `isCrunchPngs = false`; ProGuard files referenced but not active because
  minification is off.

### 1.3 Signing configuration
- Debug signing keystore path: `${rootDir}/debug.keystore` (git-ignored).
- Release signing keystore path: from `KEYSTORE_PATH`, or Gradle project property
  `releaseKeystorePath`, else `${rootDir}/my-upload-key.jks`.
- Release store password: from `STORE_PASSWORD`, or Gradle project property
  `releaseStorePassword`.
- Release key password: from `KEY_PASSWORD`, or Gradle project property
  `releaseKeyPassword`.
- Release key alias: `upload`.

### 1.4 ProGuard/R8
- `app/proguard-rules.pro` exists but is effectively inactive because release
  minification is disabled.
- Current release config does not run R8/ProGuard.

### 1.5 Network security and backup
- No `networkSecurityConfig` declared in the manifest.
- `android:allowBackup="false"` is set.
- `data_extraction_rules.xml` and `backup_rules.xml` exist under
  `app/src/main/res/xml/` but are essentially placeholder/sample files with no
  active include/exclude rules.

### 1.6 Manifest permissions and exported components
- Declared: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`,
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `RECEIVE_BOOT_COMPLETED`.
- Components reviewed for `exported`: launcher `MainActivity` is exported; other
  components reviewed are not exported.

### 1.7 `.gitignore`
- Already ignores `debug.keystore` and `*.jks`.
- Extended in Stage 2 to also ignore `release.properties` and common local secret
  property filenames.

## 2. Version and release identity decision

The current repository is shipping `versionCode = 1` and `versionName = "1.0"`.
That is the first production-facing identity in this repository.

Recommendation:
- First production release: `versionName = "1.0.0"`, `versionCode = 1`.

Rationale:
- `1.0` becomes `1.0.0` for a clearer semantic release identity without changing
  the existing version code.
- Version code `1` is already used and should not be reused or reset.
- This is a cosmetic/versioning clarification, not a functional change.

Status:
- Version decision documented here.
- Version bump not applied automatically because the current
  `versionName = "1.0"` already resolves cleanly for a first release and any
  change should be made explicitly by the owner/release step.

## 3. Production build configuration preparation

### 3.1 What is ready
- Release build type exists with a non-hardcoded signing config fallback path.
- Debug and release signing are distinct.
- No debug credentials are embedded in source.

### 3.2 What is not ready and why
- Release minification is disabled.
- ProGuard/R8 keep rules exist but are not active.
- Backup/data extraction XML files are placeholders.
- Network security config is not declared.
- Production signing keystore and passwords are not available in this repository.

These are release-configuration maturity items, not build blockers.

## 4. Signing readiness

### 4.1 Existing key material
- Repo-local debug keystore exists:
  - `debug.keystore`
  - git-ignored
  - used only for debug builds
- No production upload keystore exists in the repository or environment.

### 4.2 Production signing status
- No production key is present.
- No production-signed APK can be produced in this environment without owner keys.
- The release signing config is prepared to accept owner-supplied credentials
  via environment variables or Gradle project properties.
- The release signing config does not embed secrets in source.

### 4.3 Owner-operated key generation procedure
1. Decide on a production keystore password and a key password, and store both
   outside the repository.
2. Generate the upload keystore with `keytool`, for example:
   ```
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
3. Store the keystore and passwords securely and back them up separately.
4. Point the release build at the keystore using environment variables or Gradle
   project properties, never by committing secrets.
5. Keep the production key permanently. Replacing it changes app identity for
   updates and is not a routine operation.

## 5. APK and release artifact strategy

### 5.1 Debug APK
- Location: `app/build/outputs/apk/debug/app-debug.apk`
- Size: 19918197 bytes
- Signature: debug-signed via `debug.keystore`

### 5.2 Release APK
- Path pattern: `app/build/outputs/apk/release/app-release.apk` when built with
  a valid release signing config.
- Status: configuration ready; signing pending owner action.

### 5.3 Build commands for the owner
- Debug:
  ```
  ./gradlew assembleDebug --no-daemon --rerun-tasks
  ```
- Release:
  ```
  ./gradlew assembleRelease --no-daemon --rerun-tasks
  ```
  with signing credentials supplied via environment variables or Gradle project
  properties.

### 5.4 Verification commands for the owner
- Inspect APK signature:
  ```
  apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
  ```
- If `apksigner` is unavailable, `jarsigner -verify` or Android Studio can be
  used.
- Inspect APK metadata:
  ```
  aapt2 dump badging app/build/outputs/apk/release/app-release.apk
  ```
- Record size and checksum:
  ```
  ls -l app/build/outputs/apk/release/app-release.apk
  sha256sum app/build/outputs/apk/release/app-release.apk
  ```

## 6. Release dependency and security review

### 6.1 Confirmed good
- No Proxmox credentials embedded in app source.
- No shell/SSH/unrestricted proxy in app or gateway.
- TLS enforcement present in gateway executor and ntfy admin client.
- Mutations disabled by default.
- Mock executor is default in `AppContainer`.
- `CommandViewModel` defaults to `"MOCK (no real execution)"` and
  `CommandScreen` shows mock-oriented messaging unless a real gateway is wired.

### 6.2 No production misrepresentation found
- Without explicit owner wiring, the release APK is not configured to present the
  mock executor as a real Proxmox connection.
- The default backend label is explicit about mock behavior.

### 6.3 Release config maturity items to finalize
- Release minification and R8 keep rules.
- Backup/data extraction rules appropriate for this app.
- Network security config if desired for production.
- Final dependency version baseline before release.

## 7. Stage 2 verification

### 7.1 Commands run
- `./gradlew test --no-daemon`
- `./gradlew assembleDebug --no-daemon --rerun-tasks`
- `./gradlew lintDebug --no-daemon --no-configuration-cache --rerun-tasks`

### 7.2 Results
- Android tests: `tests=361 failures=0 errors=0 skipped=2`
- Debug build: `BUILD SUCCESSFUL`
- Lint: `BUILD SUCCESSFUL`, 0 errors

### 7.3 Signed release build
- Not executed because no production signing key is available.

## 8. Documentation created

- `RELEASE_PREPARATION_REPORT.md`
- `SIGNING_SETUP_GUIDE.md`
- `RELEASE_BUILD_GUIDE.md`

## 9. Remaining blockers

- Production signing key not available.
- Release minification/R8 not enabled.
- Backup/data extraction/network security finalization pending owner review.
- Live server/device validation still blocked.
- First production release still requires owner key generation and signing.

## 10. Exact owner actions required

1. Decide final release version and version code.
   - Recommended: `versionName = "1.0.0"`, `versionCode = 1`.
2. If bumping version name, update `versionName` in `app/build.gradle.kts`.
3. Generate and securely back up a production upload keystore.
4. Provide signing credentials via environment variables or a git-ignored
   Gradle properties file.
5. Optionally enable release minification and finalize ProGuard/R8 keep rules.
6. Optionally finalize backup/data extraction/network security config.
7. Build and verify the signed release APK.
8. Decide on App Bundle vs APK distribution.
9. Perform live server/device validation before publishing.
