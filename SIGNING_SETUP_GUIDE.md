# Kaminari Nfty — Signing Setup Guide

This guide explains how Kaminari Nfty signing is configured and what the owner
must do before a production release can be built.

## Current signing model

### Debug signing
- Debug builds use a repo-local `debug.keystore`.
- That file is git-ignored.
- It is used only for development/debug builds.

### Release signing
- Release signing is intentionally not hardcoded.
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

If none of those are available, the release signing config will not have a valid
keystore and a production-signed release APK cannot be produced.

## What exists now

- Debug keystore: present and git-ignored.
- Production keystore: not present.
- No secrets are committed to the repository.

## What the owner must do

### 1. Generate a production upload keystore
Use `keytool` or Android Studio. Keep the keystore outside the repository.

Example:
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

### 2. Store credentials securely
- Do not commit the keystore.
- Do not commit the passwords.
- Store the keystore and passwords separately and back them up.

### 3. Make signing available to the build
Choose one:
- Export environment variables before building:
  ```
  export KEYSTORE_PATH=/secure/path/to/my-upload-key.jks
  export STORE_PASSWORD=actual-store-password
  export KEY_PASSWORD=actual-key-password
  ```
- Or use a git-ignored Gradle properties file and pass it explicitly during the
  release build.

A template for a git-ignored properties file is provided in
`release.properties.example`.

## Backup and recovery

- The production key is part of app identity for updates.
- If it is lost, you may not be able to publish updates under the same identity.
- Back up the keystore and both passwords securely and separately.

## Production-signed APK readiness

A production-signed APK can be built only after the owner supplies the signing
credentials. Without them, only debug-signed APKs can be produced.
