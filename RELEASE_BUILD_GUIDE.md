# Kaminari Nfty — Release Build Guide

## Prerequisites

- JDK 21
- Android SDK with compile SDK 36
- Gradle wrapper: `./gradlew`
- For release signing: a production upload keystore and credentials available
  through environment variables or a git-ignored Gradle properties file.

## Build debug APK

```
./gradlew assembleDebug --no-daemon --rerun-tasks
```

Output:
- `app/build/outputs/apk/debug/app-debug.apk`
- Debug-signed using repo-local `debug.keystore`.

Current debug APK:
- Size: 19918197 bytes
- Signature: debug-signed

## Build release APK

```
./gradlew assembleRelease --no-daemon --rerun-tasks
```

Output when signing is configured:
- `app/build/outputs/apk/release/app-release.apk`

If release signing credentials are not available, the release build cannot be
signed and should not be treated as a production artifact.

## Provide signing credentials

### Option 1: environment variables
```
export KEYSTORE_PATH=/secure/path/to/my-upload-key.jks
export STORE_PASSWORD=actual-store-password
export KEY_PASSWORD=actual-key-password
```

### Option 2: git-ignored properties file
Use `release.properties.example` as a template, copy it to a git-ignored file,
fill in real values, and pass it to Gradle when building.

## Verify a release APK signature

```
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

If `apksigner` is unavailable, use `jarsigner -verify` or inspect signing in
Android Studio.

## Inspect APK metadata

```
aapt2 dump badging app/build/outputs/apk/release/app-release.apk
```

## Record size and checksum

```
ls -l app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

## Debug versus release

- Debug APK: debug-signed, for development and internal testing.
- Release APK: should be signed with the production key before distribution.
- Do not distribute a debug-signed APK as a production release.
- Do not claim a release APK is production-signed unless signature verification
  confirms it.
