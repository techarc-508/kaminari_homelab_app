# Kaminari Nfty

A low-resource Android client for a self-hosted [ntfy](https://ntfy.sh) server, built with Kotlin, Jetpack Compose, Room, DataStore and Android Keystore-backed credential storage. It is the Kaminari/RPLab homelab client for receiving, publishing and reviewing ntfy messages, with a separate read-only Proxmox gateway.

![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-minSdk%2024%20%C2%B7%20target%2036-3DDC84?logo=android&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09.00-4285F4?logo=jetpackcompose&logoColor=white)
![Tests](https://img.shields.io/badge/Android%20tests-361%20passed%20%C2%B7%202%20skipped-2EA043)
![Version](https://img.shields.io/badge/version-1.0.0-blue)
![Signing](https://img.shields.io/badge/release-test--only%20signed-orange)

> **Release status:** `v1.0.0` is a test-only-signed build for owner installation and evaluation. It is not production-signed and has not been validated on physical hardware. See [Release and signing](docs/RELEASE_AND_SIGNING.md).

## What it does

Kaminari Nfty is a real ntfy client, not a mock-up:

- **Receive:** authenticated JSON stream (`GET /{topics}/json`) with reconnect, catch-up cursor, keepalive handling and bounded diagnostics.
- **History:** Room is the source of truth. Messages survive process death and restart; deduplication is on `(server_profile_id, ntfy_message_id)`.
- **Publish:** documented ntfy publish API with title, priority, tags, click URL, attachment URL and delay. Binary attachment upload is intentionally not implemented.
- **Outbox:** durable `QUEUED → SENDING → ACCEPTED / FAILED / AMBIGUOUS` state machine with manual retry only. `ACCEPTED` means the server accepted the request; it is not a handset delivery or read receipt.
- **Inbox, Topics, Search, Saved, Read/Unread, Delete:** profile-scoped, paginated and searchable.
- **Delivery modes:** Instant foreground stream (best effort) and Battery Saver WorkManager polling (periodic and inexact).
- **Standard and E-Ink profiles:** the E-Ink profile is a complete monochrome presentation with no motion, gradients or shadows, not a grayscale tint.
- **Activity timeline:** bounded, profile-scoped publish/receive/retry/command events (90 days / 5,000 per profile).
- **Command Centre:** typed `rplab.command.v1` envelopes, allowlist, five-minute expiry, single-use nonce and destructive confirmation — with a **mock executor only**. It never executes shell, SSH or Proxmox commands.
- **Separate gateway:** an independent FastAPI service can expose read-only Proxmox data. The APK never contains Proxmox credentials.

## Verified environment

The live self-hosted server was audited on 2026-11-11:

| Item | Value |
|---|---|
| Canonical public URL | `https://ntfy.rplab.qzz.io` |
| Old/mistyped URL | `https://nfty.rplab.qzz.io` — returns HTTP 502; do not use |
| Server | ntfy **2.28.0**, Docker container `ntfy` on CT 104 (`docker-stack`) |
| Health | `200 {"healthy":true}` from the Mint VM and from Proxmox |
| Authentication | **Disabled** (`users=0`, `enable_login=false`); use Guest mode |
| Existing production topic | `KAMINARI-FUJI-Homelab` (PVE, watchtower, docker-events) — read-only from the app |
| Dedicated test topic | `kaminari-onboarding-test` |

The public hostname works; the LAN origin is `http://192.168.0.104:8201` for other clients, but the Android app enforces HTTPS by default.

## Screens

No device screenshots are committed because this development host has no emulator or connected Android device. The UI is covered by Robolectric Compose tests; screenshots should be captured from the first physical-device test. The app has seven destinations:

`Inbox · Topics · Compose · Outbox · Activity · Command · Settings`

## Architecture

```
Compose UI (MainActivity, seven screens)
        │
        ▼
MainViewModel / PublishViewModel / ActivityViewModel / CommandViewModel
        │
        ▼
Repositories (messages, profiles, outbox, activity, commands)
        │            │
        │            ├── Room (v2, six tables, explicit migrations)
        │            ├── DataStore (non-secret preferences)
        │            └── Android Keystore SecretStore (credentials)
        ▼
NtfyStreamClient / NtfyPublishClient / NtfyAdminClient (OkHttp)
        │
        ▼
Self-hosted ntfy server
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full dependency and data-flow description.

## Build

### Prerequisites

- JDK 21
- Android SDK platform 36 and build-tools 36
- Gradle 9.3.1 (the wrapper is pinned to a local distribution because JVM access to `services.gradle.org` times out on this VM; see [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md))

### Debug APK

```bash
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

### Test-only release APK

The release keystore is **outside Git**. Supply it at build time; never commit it.

```bash
export KEYSTORE_PATH="$HOME/.config/kaminari-nifty/kaminari-test-signing-2026-11-11.jks"
export STORE_PASSWORD="$(cat "$HOME/.config/kaminari-nifty/.signing_pw")"
export KEY_PASSWORD="$(cat "$HOME/.config/kaminari-nifty/.signing_pw")"
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

The release variant has R8 minification and resource shrinking enabled, with the ProGuard rules in `app/proguard-rules.pro`. The resulting APK is test-only signed; it is not a Play Store key.

### Tests

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
cd gateway && PYTHONPATH=. python3 -m pytest -q
```

Verified on 2026-11-11: **361 Android tests, 0 failures, 0 errors, 2 skipped** (the two skips are the pre-existing Robolectric Keystore round-trip cases), **9 gateway tests passed**. Lint: **0 errors, 56 warnings**.

## Install

```bash
adb install -r /home/pushpal/Desktop/KaminariNfty/KaminariNfty-debug.apk
# or
adb install -r /home/pushpal/Desktop/KaminariNfty/KaminariNfty_final_release.apk
```

Both APKs share `com.rplab.kaminarinfty` but use **different signing identities**. Android will not install one over the other; uninstall the old variant first. Back up any data you need before switching.

### First configuration

1. Open **Settings → server profile** (or the first-run Setup screen).
2. Server URL: `https://ntfy.rplab.qzz.io`
3. Authentication: **Guest / Anonymous** (the server currently has auth disabled).
4. Enforce HTTPS: **on**.
5. Add the topic `KAMINARI-FUJI-Homelab` for read-only homelab notifications, or `kaminari-onboarding-test` for safe publish tests.
6. Choose **Instant** or **Battery Saver** in Settings.
7. Choose **Standard** or **E-Ink** in Settings.

Do not publish test messages to `KAMINARI-FUJI-Homelab`; it is the live production alert topic. Use `kaminari-onboarding-test`.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Android setup](docs/ANDROID_SETUP.md) · [Android usage](docs/ANDROID_USAGE.md)
- [ntfy setup](docs/NTFY_SETUP.md) · [ntfy integration](docs/NTFY_INTEGRATION.md) · [Android onboarding](docs/NTFY_ANDROID_ONBOARDING.md)
- [Proxmox setup](docs/PROXMOX_SETUP.md) · [Gateway deployment](docs/GATEWAY_DEPLOYMENT.md)
- [Security](docs/SECURITY.md) · [Configuration reference](docs/CONFIGURATION_REFERENCE.md)
- [Release and signing](docs/RELEASE_AND_SIGNING.md) · [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Project history](docs/PROJECT_HISTORY.md) · [APK user guide](docs/APK_USER_GUIDE.md)

Owner-only live-access documents are kept outside Git at `~/.config/kaminari-nifty/` (mode 700; files 600). They are intentionally not in this repository.

## Security model

- Credentials are encrypted with an Android Keystore-backed key; only the encrypted envelope is stored.
- No token, password, PVE secret or signing key is in the APK, source, docs or Git history.
- The public app identity is non-admin; the PVE gateway uses a dedicated read-only `kaminari-gateway@pve` token with `KaminariRead` and privilege separation.
- The gateway has no mutation permission; `GATEWAY_MUTATIONS_ENABLED` stays `false`.
- The Android Command Centre uses a mock executor and never sends shell, SSH or Proxmox commands.
- Release signing is test-only; rotate it before production distribution.

## Limitations

- No physical device or emulator is available on the development VM. Notification rendering, Doze, reboot, OEM battery behaviour and the Android 15 `dataSync` timeout are **not verified on hardware**.
- Instant delivery is best effort; Battery Saver is periodic and inexact.
- ntfy authentication is currently disabled on the owner’s server, so the app runs in Guest mode. No access token exists to issue or revoke.
- The E-Ink profile is generic; vendor-specific Onyx Boox refresh APIs are deferred until the device arrives.
- The Proxmox gateway is not deployed; only the least-privilege service identity and read-only API access were provisioned.
- Binary attachment upload is out of scope for v1; URL/metadata attachments are supported.

## Project history

The project was built in audited phases: read-only audit, Phase 0 toolchain/identity, Phase 1 persistence and credentials, Phase 2 ntfy transport, Phase 3 delivery/remediation, Phase 4 publish/outbox/activity/mock commands, Phase 5 E-Ink, Phase 6 gateway and admin surfaces, Phase 7 integration hardening, and the final live-infrastructure/publication pass. See [docs/PROJECT_HISTORY.md](docs/PROJECT_HISTORY.md).

## License

No license file is included in this repository. No license is granted for redistribution; contact the owner for terms.

## Contributing

This is a personal RPLab project. Open an issue with the exact app version, APK SHA-256, Android version and a redacted log. Do not paste tokens, passwords or notification contents.
