# Kaminari Nfty

Android chat application with AI-assisted features, built with Jetpack Compose and clean architecture.

## Build & Installation

### Prerequisites
- Android Studio Arctic Fox or newer
- JDK 17
- Gradle 8.4+

### Building the Project
```bash
# Debug build
./gradlew assembleDebug --no-daemon --rerun-tasks

# Release build (test-only signing)
# Requires environment variables:
#   KAMINARI_RELEASE_STORE_PASSWORD - keystore password
#   KAMINARI_RELEASE_KEY_PASSWORD - key password
./gradlew :app:packageRelease --no-daemon --rerun-tasks -PreleaseKeystorePath=/home/pushpal/KaminariNfty-test-signing-key.jks -PreleaseStorePassword=<REDACTED> -PreleaseKeyPassword=<REDACTED>
```

## Test Results
- Android unit tests: 32 passed
- Gateway tests: passed
- Lint: 0 errors

## Signing Configuration
- Debug: `debug.keystore` with default credentials
- Release: Test-only keystore at `/home/pushpal/KaminariNfty-test-signing-key.jks` (alias `upload`)
- Credentials supplied via environment variables at build time - never committed to repository
- No production signing key generated

## Repository Security
- Signing passwords removed from documentation
- Environment-variable placeholders used: `KAMINARI_RELEASE_STORE_PASSWORD`, `KAMINARI_RELEASE_KEY_PASSWORD`
- Fresh test-only keystore generated outside Git
- `.gitignore` excludes `*.jks`, `*.properties`, `debug.keystore`, `release.properties`, `.env`

## APK Artifacts
- `KaminariNifty-debug.apk` - debug build SHA-256: `a3d29f0557b0f247e6246439d1346bc2396f26c9c8a8178f6f955f2dea3b17ac`
- `KaminariNifty_final_release.apk` - test-only signed release SHA-256: `98276170c654c646472e32cda2f76da5aa3c67cf406cafb9cd9e0bc07367286f`

## Configuration
- Self-hosted ntfy instance recommended at `https://nfty.rplab.qzz.io`
- Gateway configuration available in `gateway/` directory
- Proxmox integration guides in documentation

## License
MIT License - see LICENSE file for details.
