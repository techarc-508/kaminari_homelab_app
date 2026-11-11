# Release Signing Final Guide

**Stage:** Phase 8, Stage 5.
**Date:** 2026-11-10.

## Purpose

This guide explains how release signing is configured today, what is missing for a
production-signed release APK, and how the owner can safely create, back up, and use a
production signing key. It also documents the separate test-only signing path used only
for the owner's personal release-variant install testing.

## Current signing model

### Debug signing
- Debug builds use the repo-local `debug.keystore`.
- `debug.keystore` is git-ignored.
- Debug signing is used only for development/debug builds.

### Release signing
- Release signing is not hardcoded with real credentials.
- `app/build.gradle.kts` reads signing credentials in this order:
  1. Environment variables:
     - `KEYSTORE_PATH`
     - `STORE_PASSWORD`
     - `KEY_PASSWORD`
  2. Gradle project properties:
     - `releaseKeystorePath`
     - `releaseStorePassword`
     - `releaseKeyPassword`
  3. Fallback path: `${rootDir}/my-upload-key.jks` with alias `upload`.
- If none of those are available, the release signing config does not have a valid
  keystore and a production-signed release APK cannot be produced.

## What exists now

- Debug keystore: present and git-ignored.
- Production keystore: not present.
- No secrets are committed.
- `.gitignore` already ignores `*.jks`, `release.properties`, and related patterns.

## Production signing key owner steps

### 1. Create the keystore outside the repository
Use `keytool` or Android Studio. Store the keystore outside the repository.

Example:
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

### 2. Back up the keystore and passwords
- Store the keystore and both passwords securely and separately.
- Back them up separately from each other and from the repository.
- This key is part of app identity for updates. Losing it can prevent future updates
  under the same identity.

### 3. Provide signing to the build
Choose one approach, but never commit real credentials:

- Environment variables:
  ```bash
  export KEYSTORE_PATH=/secure/path/to/my-upload-key.jks
  export STORE_PASSWORD=actual-store-password
  export KEY_PASSWORD=actual-key-password
  ```
- Or a git-ignored Gradle properties file loaded explicitly by the owner before building.
  A template is provided in `release.properties.example`.

### 4. Build and verify
- Build the release APK or App Bundle.
- Verify the signature with `apksigner` or Android Studio.
- Record size and checksum for the artifact.

## Test-only signing for personal release testing

Because the owner wants an installable release-variant APK for personal testing and the
production key is unavailable during this stage, this stage prepared a separate test-only
signing path.

Requirements met:
- The test-only keystore is stored outside Git.
- It is distinct from any future production key.
- It is never described as production signing.
- It is documented as test-only.
- It does not compromise future production signing.

Current test-only artifacts (outside repo; do not commit):

- Keystore: `/home/pushpal/KaminariNfty-test-signing-key.jks`
- Alias: `upload`
- Store password: `$KAMINARI_RELEASE_STORE_PASSWORD` (supplied at build time; never committed)
- Key password: `$KAMINARI_RELEASE_KEY_PASSWORD` (supplied at build time; never committed) (same as store password for this test-only keystore, as verified by `openssl pkcs12` and `keytool -list -v -alias upload` during the initial setup of this test-only signing path)
- Subject DN: `CN=Kaminari Nfty Test, OU=RPLab, O=RPLab, L=, ST=, C=KR`
- Validity: 10000 days
- Certificate SHA-256 (alias `upload`): `64:AF:78:BD:29:8A:AB:9C:AB:3F:C6:DF:C5:A8:A3:20:E9:5E:82:E5:5C:25:6A:68:20:79:22:67:AE:ED:06:9C`
- Certificate SHA-256 (alias `kaminari-test`): `78:6B:D6:40:E0:B2:FE:57:34:BF:05:51:15:3C:DC:EB:E0:F0:FF:FB:EE:BF:18:A7:AE:E6:BB:A8:78:93:74:13` (not used by the build)
- Properties file: `/home/pushpal/KaminariNfty-test-signing-key.properties`

Why the alias is `upload`: the release signing config in `app/build.gradle.kts` hardcodes
the alias `upload`. The test-only keystore therefore contains the alias `upload` so the
release build can sign the release variant for local install testing without changing the
release signing configuration or enabling minification.

Build command used in this stage (env vars shown; actual values not committed):

```bash
./gradlew :app:packageRelease --no-daemon \
  -PreleaseKeystorePath=/home/pushpal/KaminariNfty-test-signing-key.jks \
  -PreleaseStorePassword=<REDACTED> \
  -PreleaseKeyPassword=<REDACTED>
```

Resulting artifact (from this stage):

- Path: `app/build/outputs/apk/release/app-release.apk`
- Size: 13,439,830 bytes
- SHA-256: `98276170c654c646472e32cda2f76da5aa3c67cf406cafb9cd9e0bc07367286f`
- Signed with the test-only key; not production-signed.

Owner notes for the test-only path:
- This APK exists only for local install testing on the owner's Linux Mint Desktop.
- It should not be treated as the production identity.
- It should not be distributed as production-signed.
- If a real production key is later supplied, rebuild and reverify the release APK with
  that key and record the new size/checksum.
- The test-only keystore and properties file are outside the repository and are never
  added to Git.

## What this guide does not do

- It does not generate a production signing key.
- It does not embed credentials in source.
- It does not commit signing material.
- It does not replace the owner's responsibility to keep the production key safe.

## Status

- Release signing configuration: prepared.
- Production signing: not available in this environment.
- Test-only release signing: prepared for owner personal testing only.
- Production key generation: owner action.

