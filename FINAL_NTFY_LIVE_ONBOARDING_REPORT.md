# Final ntfy live onboarding report

Date: 2026-11-11 UTC.

## Canonical endpoint

| Item | Value |
|---|---|
| URL | `https://ntfy.rplab.qzz.io` |
| Server | ntfy 2.28.0 |
| Health | `200 {"healthy":true}` from Mint VM and PVE |
| DNS/TLS | valid public certificate; Cloudflare in front of cloudflared CT 103 |
| LAN origin | `http://192.168.0.104:8201` (CLI only) |
| Old URL | `https://nfty.rplab.qzz.io` → HTTP 502; not used |

## Authentication and token

The server currently has authentication **disabled** (`users=0`, `enable_login=false`). Therefore:

- There is no app token and no admin account to create, issue or revoke.
- The Android app uses **Guest / Anonymous**.
- No credential was written into the app, repository or chat.
- A future non-admin `kaminari_app` token can only be created after the owner enables auth and updates the three existing producers.

## Live tests performed

| Test | Result |
|---|---|
| `GET /v1/health` (public) | 200, `healthy:true` |
| `GET /v1/config` | 200; login disabled |
| Publish to `kaminari-onboarding-test` | 200; `event=message`, id `iC9Yb0cVwx0l` |
| Read-back from same topic | same message id |
| Unauthorized topic test | not applicable — auth is disabled; no token to test |
| App Bearer-token path | verified by unit tests with MockWebServer; no live token exists |
| App Guest path | supported and correct for the current server |
| Logout/credential clearing | covered by unit/ViewModel tests; no live credential to clear |

`ACCEPTED` in the app means the server accepted the request. It does **not** prove handset delivery, notification rendering or human read.

## Android configuration

1. Server URL `https://ntfy.rplab.qzz.io`.
2. Auth method **Guest / Anonymous**.
3. Enforce HTTPS on.
4. Topics: `KAMINARI-FUJI-Homelab` (read-only) and `kaminari-onboarding-test` (test).
5. Do not publish to the production topic.

## Private owner file

`$HOME/.config/kaminari-nifty/NTFY_ONBOARDING_PRIVATE.txt` (directory 700, file 600) contains the URL, auth status, approved topics and revocation/enablement procedure. It contains no token because none exists.

## Remaining blocker

The server is usable now, but it is unauthenticated. Anyone with a topic name can read/publish. The owner must decide whether to enable auth; this report does not change it.
