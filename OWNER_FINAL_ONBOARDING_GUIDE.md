# Kaminari Nfty — owner final onboarding guide

## 1. Where the APKs are

Both are on the Linux Mint Desktop:

- `/home/pushpal/Desktop/KaminariNfty/KaminariNfty-debug.apk`
- `/home/pushpal/Desktop/KaminariNfty/KaminariNfty_final_release.apk`

The release file is the normal one to install. The debug file is for development/diagnostics.

## 2. Which to install first

Install **`KaminariNfty_final_release.apk`** first. It is R8-minified and much smaller (2.49 MB vs 19.9 MB). Both are version `1.0.0`, package `com.rplab.kaminarinfty`.

## 3. Install from the Desktop

With the phone connected and USB debugging enabled:

```bash
adb uninstall com.rplab.kaminarinfty   # only if an older variant is installed
adb install -r /home/pushpal/Desktop/KaminariNfty/KaminariNfty_final_release.apk
```

Or copy the APK to the phone and open it in the file manager. Allow “Install unknown apps” for that file manager if prompted.

## 4. What the debug build is for

The debug build is debuggable, larger and signed with the Android debug key. Use it only when capturing logs or testing a change. Do not install it over the release build; the signatures differ and Android will reject the update.

## 5. What the test-only release is for

It is the same app, minified and signed with a **test-only** key (`CN=Kaminari Nfty Test Signing`). It is suitable for owner evaluation and daily use on your own device, but it is **not** a Play Store/production-signed release. Do not distribute it publicly as a production build.

## 6. Is a signing password needed to install?

No. The APK is already signed. Passwords are only needed if you rebuild it from source. They are in `~/.config/kaminari-nifty/.signing_pw` (600), never in the repository.

## 7. Enter the verified ntfy URL

Open the app → Setup/Settings → server profile:

```
https://ntfy.rplab.qzz.io
```

Do **not** use `https://nfty.rplab.qzz.io` — that spelling returns HTTP 502.

## 8. Enter authentication

Choose **Guest / Anonymous**. The live server currently has authentication disabled (`users=0`), so there is no username, password or token to enter.

If you enable auth later, create a non-admin `kaminari_app` user and use its token; never use an admin token in the app.

## 9. Subscribe to topics

**Topics → Add Topic**:

- `KAMINARI-FUJI-Homelab` — live PVE/watchtower/docker-events alerts. Add it as read-only; do not publish test messages here.
- `kaminari-onboarding-test` — safe test topic.

The names `homelab-alerts`, `iot-telemetry`, `backups`, `build-server` and `security` are demo data only; they are not verified live topics.

## 10. Approved reading and publishing

| Topic | Read | Publish |
|---|---|---|
| `KAMINARI-FUJI-Homelab` | yes | **no** (production alerts) |
| `kaminari-onboarding-test` | yes | yes |

## 11. Send a test notification

**Compose** → topic `kaminari-onboarding-test` → message “Kaminari onboarding test” → **Queue & Send**. Check **Outbox**: `ACCEPTED` means the server accepted it. Check **Inbox**: the message should arrive via the stream or Battery Saver catch-up.

## 12. Inbox, Topics, Compose, Outbox, Activity

- **Inbox** — paginated messages, search, saved filter, read/unread, delete, open links.
- **Topics** — add/remove subscriptions and see counts.
- **Compose** — title, body, priority, tags, click URL, attachment URL, delay.
- **Outbox** — durable send states; manual retry only; ambiguous retry requires confirmation.
- **Activity** — bounded publish/receive/retry/command events.

## 13. Instant delivery

**Settings → Delivery mode → Instant.** The app runs a foreground stream while Android permits it. It is best effort. Android 15+ limits `dataSync` foreground services; the app stops before the fatal deadline and moves to Battery Saver.

## 14. Battery Saver

**Settings → Delivery mode → Battery Saver.** WorkManager polls periodically. This is **not instant**; Android enforces a 15-minute minimum and Doze can delay it.

## 15. Standard and E-Ink profiles

**Settings → Theme.** Standard uses the Kaminari day/night palettes. E-Ink uses a complete monochrome presentation: solid borders, static status, larger touch targets, no gradients, shadows or continuous animation. It is not a grayscale filter.

## 16. What Command Centre can do

It creates typed `rplab.command.v1` envelopes and runs them through a **mock executor**. It shows a clear “MOCK — no real infrastructure changes” banner. It does not execute shell, SSH or Proxmox commands and does not call the gateway.

## 17. Which features need the gateway

A real Proxmox read-only view or future approved command execution would need the separate FastAPI gateway. It is **not deployed** in this delivery. The least-privilege PVE identity is ready; see `docs/GATEWAY_DEPLOYMENT.md`.

## 18. Configure the gateway (later)

Read `docs/GATEWAY_DEPLOYMENT.md`. Use an existing always-on host, HTTPS/private network, `GATEWAY_MUTATIONS_ENABLED=false`, the `<gateway-user>@pve!<token-id>` token, and the PVE CA certificate. Never put the token in the APK.

## 19. Troubleshooting

- 502 → wrong hostname; use `ntfy.rplab.qzz.io`.
- Cleartext/HTTP → use the public HTTPS URL.
- No notifications → grant POST_NOTIFICATIONS; check Instant/Battery Saver; OEM battery restrictions.
- Outbox `AMBIGUOUS` → check the topic in another client before retrying; retry requires confirmation.
- Install over the other variant → uninstall first; different signatures.
- More: `docs/TROUBLESHOOTING.md`.

## Android package/signing compatibility

Both APKs share `com.rplab.kaminarinfty`. Debug and release use different certificates, so Android will not install one as an in-place update over the other. Switching may require uninstalling the current variant, which deletes local app data. Back up anything important first. Do not change the application ID without owner authorization.

## Passwords and private files

- No password is needed to install or use the APKs.
- Rebuild passwords and the test keystore are in `~/.config/kaminari-nifty/` (700/600).
- The signing private info file is `~/.config/kaminari-nifty/OWNER_PRIVATE_SIGNING_INFO.txt`.
- The ntfy onboarding file is `~/.config/kaminari-nifty/NTFY_ONBOARDING_PRIVATE.txt`.
- The PVE gateway file is `~/.config/kaminari-nifty/PVE_GATEWAY_PRIVATE_INFO.txt`.
