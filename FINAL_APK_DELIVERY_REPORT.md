# Final APK delivery report

Date: 2026-11-11 UTC.

## Artifacts

| Item | Debug | Release |
|---|---|---|
| Source | `app/build/outputs/apk/debug/app-debug.apk` | `app/build/outputs/apk/release/app-release.apk` |
| Desktop | `/home/pushpal/Desktop/KaminariNfty/KaminariNfty-debug.apk` | `/home/pushpal/Desktop/KaminariNfty/KaminariNfty_final_release.apk` |
| Size | 19,918,197 B | 2,492,137 B |
| SHA-256 | `0a9bc2fed9bc23f105c83e55e35f5030807dd37465976ae2b56d2cfa9f43ad55` | `5eb29b07647566e79c1e03ff1ee5e873ada4ed4aeffc7db7c366429e20cb55be` |
| Package | `com.rplab.kaminarinfty` | same |
| Version | `1.0.0` (1) | same |
| min/target SDK | 24 / 36 | same |
| Signing | Android debug | test-only release key |
| Certificate SHA-256 | `0fcc0c8194dc885a9b731582e0b4e9231a6680f088e8f0386a78fc379d900b67` | `dc9c381cb83052ff3df384e968cf6ea6569ec643e24be6bfff0bfce0b88210d1` |

Desktop copies were verified byte-for-byte against their build outputs (`sha256sum`). No stale APK was used.

## Build evidence

```bash
./gradlew --no-daemon testDebugUnitTest   # see final test report: 361 passed, 0 failed, 2 skipped
./gradlew --no-daemon assembleDebug       # BUILD SUCCESSFUL
./gradlew --no-daemon lintDebug           # 0 errors, 56 warnings
./gradlew --no-daemon assembleRelease \
  -PreleaseKeystorePath="$HOME/.config/kaminari-nifty/kaminari-test-signing-2026-11-11.jks" \
  -PreleaseStorePassword=... -PreleaseKeyPassword=...   # BUILD SUCCESSFUL
```

Verification:

```bash
aapt2 dump badging app-release.apk
apksigner verify --print-certs app-release.apk
sha256sum app-release.apk
```

## Install

```bash
adb uninstall com.rplab.kaminarinfty   # required if the other variant is installed
adb install -r /home/pushpal/Desktop/KaminariNfty/KaminariNfty_final_release.apk
```

## What was not claimed

The APKs build and pass automated tests, but physical notification delivery, Doze, reboot, OEM battery behavior, Android 15 `dataSync` timeout, Keystore round-trip and Boox E-Ink behavior are not verified. The release is test-only signed, not production-signed.
