# Owner device testing checklist

Record the actual result for each row. Do not mark a row passed without observing it.

## Device and build

| Field | Value |
|---|---|
| Device model | |
| Android version / API | |
| APK variant | debug / release |
| APK SHA-256 | |
| Install date | |
| Tester | |

## Core tests

| # | Test | Expected | Actual | Pass |
|---|---|---|---|---|
| 1 | Fresh install | App opens without crash | | |
| 2 | First launch / setup | Setup screen or empty Inbox | | |
| 3 | Server URL `https://ntfy.rplab.qzz.io` | Health 200, no TLS error | | |
| 4 | Guest authentication | Status shows guest/connected, not authenticated-admin | | |
| 5 | Add `KAMINARI-FUJI-Homelab` | Topic appears; existing messages load | | |
| 6 | Incoming stream message | Message appears in Inbox | | |
| 7 | System notification | Notification appears (permission granted) | | |
| 8 | Notification tap | Opens the correct message detail | | |
| 9 | Publish to `kaminari-onboarding-test` | Outbox `ACCEPTED`; message read back | | |
| 10 | Publish validation | Empty topic/body is rejected before network | | |
| 11 | Outbox manual retry | Only manual; no duplicate automatic send | | |
| 12 | Ambiguous retry | Explicit duplicate-risk confirmation | | |
| 13 | Search | Bounded results; saved filter works | | |
| 14 | Mark read/unread, save, delete | State persists after app restart | | |
| 15 | Process death / restart | History and state survive | | |
| 16 | Instant mode | Foreground notification; stream stays while permitted | | |
| 17 | Battery Saver mode | Periodic catch-up; no “instant” claim | | |
| 18 | Mode switch | No duplicate stream; old service stops | | |
| 19 | Reboot | Mode restored after first unlock | | |
| 20 | Offline → online | Reconnect and catch-up; honest status | | |
| 21 | Standard profile | Normal Kaminari day/night UI | | |
| 22 | E-Ink profile | Monochrome, no motion/gradient/shadow; manual refresh | | |
| 23 | Large font / landscape | No clipped primary actions | | |
| 24 | Command Centre | Mock banner; no infrastructure change | | |
| 25 | Logout | Stored credential cleared; no secret shown | | |
| 26 | Guest read of production topic | Works; no publish test to it | | |
| 27 | Live gateway (if deployed) | Read-only endpoints only | | |
| 28 | APK update debug↔release | Expected failure without uninstall | | |

## Bug report fields

- App version and APK SHA-256.
- Device/Android version.
- Exact steps, expected vs actual.
- Redacted logcat (never include tokens, passwords or message contents).
- Screenshot if useful (redact notification contents).

## Not yet tested by the project

Physical notification rendering, Doze, OEM battery managers, Android 15 `dataSync` timeout, Keystore round-trip, E-Ink ghosting and Boox vendor refresh APIs. The owner must complete these tests; the project does not claim them.
