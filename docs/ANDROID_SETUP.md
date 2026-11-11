# Android setup

## Requirements

- Android 7.0 (API 24) or newer; target API 36.
- A reachable ntfy server (HTTPS recommended).
- Notification permission on Android 13+ for visible alerts.

## Install

From the Desktop copies:

```bash
adb install -r /home/pushpal/Desktop/KaminariNfty/KaminariNfty-debug.apk
```

For the release variant, uninstall the debug app first because the signing identities differ:

```bash
adb uninstall com.rplab.kaminarinfty
adb install -r /home/pushpal/Desktop/KaminariNfty/KaminariNfty_final_release.apk
```

Both have package `com.rplab.kaminarinfty`, versionCode `1`, versionName `1.0.0`.

## First run

1. Open the app. If no profile exists, the Setup screen appears.
2. **Server URL:** `https://ntfy.rplab.qzz.io`
3. **Authentication method:** `Guest / Anonymous` (the owner server currently has authentication disabled).
4. **Enforce HTTPS Only:** keep on.
5. Tap **Test Link** to check health, then **Connect**.
6. Open **Topics → Add Topic** and add `KAMINARI-FUJI-Homelab` (read-only production alerts) or `kaminari-onboarding-test` (safe test topic).
7. Open **Settings** and choose **Instant** or **Battery Saver** and **Standard** or **E-Ink**.
8. Grant notification permission when prompted.

## Profiles

Use **Settings → server profiles** to create additional profiles. Each profile has its own server URL, credentials, topics, messages and outbox. Switching profiles never deletes the old profile’s data. Changing a profile URL opens a preview showing the affected message/topic/outbox counts; the recommended choice is to create a new profile, because keeping the old profile and changing its URL mixes data from two servers under one identity.

## Credentials

- Guest mode needs no secret.
- Username/password and access-token modes store the secret with an Android Keystore-backed key.
- **Logout** removes the stored secret for the active profile; it does not delete message history unless you explicitly delete messages.
- Stored secrets are never rendered back into the form.

## LAN note

The app enforces HTTPS by default. `http://<private-ntfy-lan>:8201` works from `curl` on the LAN but is rejected by the app’s HTTPS enforcement and by Android’s default cleartext policy. Use the public HTTPS URL for the app.

## Permissions

| Permission | Why |
|---|---|
| INTERNET, ACCESS_NETWORK_STATE | ntfy stream/publish and connection status |
| POST_NOTIFICATIONS | Android 13+ visible notifications |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC | Instant stream while the app is allowed to run it |
| RECEIVE_BOOT_COMPLETED | Restore delivery mode after reboot |
