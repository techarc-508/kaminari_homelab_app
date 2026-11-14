# Release and signing

## Current artifacts

Current release: **`v1.2.0`, versionCode 3**.

| Artifact | Source | Size | SHA-256 |
|---|---|---|---|
| `app-release.apk` (v1.2.0) | `app/build/outputs/apk/release/app-release.apk` | 2,510,585 B | `9b953b064e28f8d7e761fdc23654f6508af39c1ae9945b8b3e9470ea10d71027` |

The release APK is distributed as a GitHub release asset. Debug builds are development-only and are not published; their size changes with the debug toolchain, so no fixed checksum is recorded here.

Superseded artifacts from earlier builds (`1.0.x`, `1.1.0`) have been removed so exactly one current APK is offered.

## Package metadata

- applicationId: `com.rplab.kaminarinfty`
- versionCode: `3`
- versionName: `1.2.0`
- minSdk: 24
- targetSdk: 36
- compileSdk: 36.1

## Signing

| Variant | Certificate | SHA-256 |
|---|---|---|
| Debug | `CN=Android Debug, O=Android, C=US` | `0fcc0c8194dc885a9b731582e0b4e9231a6680f088e8f0386a78fc379d900b67` |
| Release | `CN=Kaminari Nfty, OU=RPLab, O=RPLab, C=IN` | `a8a88887ddb0ad5f0045806860670c6c08481fe8a95ebcdbbaa05846a61910b8` |

The release key is the **established Kaminari Nfty identity** and must be preserved: changing it breaks upgrade installation for every existing install. It is stored outside Git at `~/.kaminari-signing/release.jks` (alias `upload`, mode 600) with its password in `~/.kaminari-signing/password.txt` (600). Do not publish, regenerate or commit this key, and do not weaken the container signature algorithm.

## Build commands

```bash
# Debug
./gradlew assembleDebug

# Release with R8 and resource shrinking
export KEYSTORE_PATH="$HOME/.kaminari-signing/release.jks"
export STORE_PASSWORD="$(cat "$HOME/.kaminari-signing/password.txt")"
export KEY_PASSWORD="$STORE_PASSWORD"
./gradlew assembleRelease
```

Release config: `isMinifyEnabled=true`, `isShrinkResources=true`, `isCrunchPngs=false`, R8 mapping at `app/build/outputs/mapping/release/mapping.txt`.

## Verification

```bash
aapt2 dump badging app-release.apk
apksigner verify --print-certs app-release.apk
sha256sum app-release.apk
```

`apksigner` was available in the final environment (`~/opt/android-sdk/build-tools/36.0.0`). Earlier VM tool limitations are historical and no longer apply.

## Production signing

No production key was generated. Before a Play Store or long-term distribution release:

1. Create a production keystore offline.
2. Store it and its passwords outside the repository, with backups.
3. Update `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`.
4. Rebuild and verify.
5. Keep the test-only artifact clearly labelled.
