# Release and signing

## Current artifacts

| Artifact | Source | Size | SHA-256 |
|---|---|---|---|
| `KaminariNfty-debug.apk` | `app/build/outputs/apk/debug/app-debug.apk` | 19,918,197 B | `0a9bc2fed9bc23f105c83e55e35f5030807dd37465976ae2b56d2cfa9f43ad55` |
| `KaminariNfty_final_release.apk` | `app/build/outputs/apk/release/app-release.apk` | 2,492,137 B | `5eb29b07647566e79c1e03ff1ee5e873ada4ed4aeffc7db7c366429e20cb55be` |

Both are copied to `/home/pushpal/Desktop/KaminariNfty/` and match their source checksums.

## Package metadata

- applicationId: `com.rplab.kaminarinfty`
- versionCode: `1`
- versionName: `1.0.0`
- minSdk: 24
- targetSdk: 36
- compileSdk: 36.1

## Signing

| Variant | Certificate | SHA-256 |
|---|---|---|
| Debug | `CN=Android Debug, O=Android, C=US` | `0fcc0c8194dc885a9b731582e0b4e9231a6680f088e8f0386a78fc379d900b67` |
| Release | `CN=Kaminari Nfty Test Signing, OU=RPLab, O=Kaminari, L=Local, ST=Local, C=US` | `dc9c381cb83052ff3df384e968cf6ea6569ec643e24be6bfff0bfce0b88210d1` |

The release key is **test-only**. It is stored outside Git at `~/.config/kaminari-nifty/kaminari-test-signing-2026-11-11.jks` with mode 600. Passwords are in `.signing_pw` (600). Do not publish this key.

## Build commands

```bash
# Debug
./gradlew assembleDebug

# Release with R8 and resource shrinking
export KEYSTORE_PATH="$HOME/.config/kaminari-nifty/kaminari-test-signing-2026-11-11.jks"
export STORE_PASSWORD="$(cat "$HOME/.config/kaminari-nifty/.signing_pw")"
export KEY_PASSWORD="$(cat "$HOME/.config/kaminari-nifty/.signing_pw")"
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
