# ntfy Android onboarding

This is the exact configuration for the live owner server on 2026-11-11.

## Values

| Field | Value |
|---|---|
| Server URL | `https://ntfy.rplab.qzz.io` |
| Authentication method | **Guest / Anonymous** |
| Token | **none** — the server has authentication disabled |
| Read topic | `KAMINARI-FUJI-Homelab` |
| Safe publish topic | `kaminari-onboarding-test` |
| Enforce HTTPS | on |

Do **not** use `https://nfty.rplab.qzz.io` (HTTP 502) and do not publish to `KAMINARI-FUJI-Homelab`.

## In the app

1. **Setup / Settings → server profile.**
2. Enter the URL above.
3. Select **Guest / Anonymous**.
4. Leave username/password/token empty.
5. Tap **Test Link** — expect health 200.
6. Tap **Connect**.
7. **Topics → Add Topic** → `KAMINARI-FUJI-Homelab`; optionally add `kaminari-onboarding-test`.
8. **Compose** → select `kaminari-onboarding-test` → send a harmless test like “Kaminari onboarding test”.
9. Confirm the row appears in **Outbox** as `ACCEPTED` and in **Activity**.
10. Open **Inbox** and confirm the message arrived through the stream (or Battery Saver catch-up).

## What the owner should see

- `ACCEPTED` in Outbox = the server accepted the publish.
- The same message in Inbox = the stream/catch-up read it back.
- A system notification requires Android notification permission and either Instant or Battery Saver delivery; without a physical device this has not been verified.

## Admin

No ntfy admin account exists because auth is disabled. Do not put administrative credentials in the app. See `OWNER_NTFY_ADMIN_PRIVATE.txt` outside Git for the current status and the safe future enablement procedure.
