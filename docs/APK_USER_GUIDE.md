# APK user guide

## Which file do I install?

| File | Use it when |
|---|---|
| `KaminariNfty-debug.apk` | You want the most debuggable build or are developing. Larger (19.9 MB). |
| `KaminariNfty_final_release.apk` | Normal evaluation/use. R8-minified (2.49 MB), test-only signed. |

Both are `com.rplab.kaminarinfty` version `1.0.0`. They use different certificates and cannot be installed over each other.

## Install

```bash
# Release (preferred for evaluation)
adb uninstall com.rplab.kaminarinfty   # only if a different variant is installed
adb install -r /home/pushpal/Desktop/KaminariNfty/KaminariNfty_final_release.apk

# Debug
adb uninstall com.rplab.kaminarinfty
adb install -r /home/pushpal/Desktop/KaminariNfty/KaminariNfty-debug.apk
```

On the phone, enable “Install unknown apps” for the file manager/browser if installing from Downloads.

## First run

1. Server URL `https://ntfy.rplab.qzz.io`.
2. Auth `Guest / Anonymous`.
3. Enforce HTTPS on.
4. Add `KAMINARI-FUJI-Homelab` (read) and `kaminari-onboarding-test` (test).
5. Choose Instant or Battery Saver.
6. Choose Standard or E-Ink.
7. Grant notification permission.

## Basic use

- **Inbox:** read, search, filter, save, mark read and delete.
- **Topics:** add/remove subscriptions.
- **Compose:** publish to the test topic; check Outbox for `ACCEPTED`.
- **Outbox:** manual retry only; ambiguous retries require confirmation.
- **Activity:** recent events; no message bodies.
- **Command:** mock only; nothing touches infrastructure.
- **Settings:** profiles, mode, theme, logout.

## Data and switching

Messages are local to the app. Uninstalling deletes the local database. If you switch between debug and release, back up anything you need first. Profile switching inside the app does not delete data.

## Not verified on this build

Physical notification rendering, Doze/reboot/OEM behavior, Android 15 `dataSync` timeout, Keystore on hardware, E-Ink ghosting and Boox vendor refresh behavior. Test these on the device and report exact results.
