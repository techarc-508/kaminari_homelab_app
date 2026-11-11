# Kaminari Nfty — final owner handover

## What is complete

- Real self-hosted ntfy client: receive, history, search, saved/read, publish, outbox, activity.
- Both delivery modes: Instant (best effort) and Battery Saver (periodic, inexact).
- Room persistence with explicit v1→v2 migration and deduplication.
- Android Keystore-backed credential storage; no plaintext secrets.
- Generic E-Ink profile (complete monochrome presentation).
- Mock-only Command Centre; no shell/SSH/PVE execution.
- Separate FastAPI gateway with read-only Proxmox routes and mutation guard.
- Live ntfy audited: canonical `https://ntfy.rplab.qzz.io`, ntfy 2.28.0, health 200, auth disabled.
- PVE least-privilege identity: `<gateway-user>@pve!<token-id>`, `KaminariRead`, read-only API verified.
- Public GitHub repository with full history and documentation.
- Debug and test-only signed release APKs on the Desktop, checksum-verified.
- Public repository: https://github.com/techarc-508/kaminari_homelab_app (branch `main`; history reconciliation and scan in `STAGE_6_PUBLICATION_AND_DOCUMENTATION_REPORT.md`).

## What is not complete

- Physical device validation (notifications, Doze, reboot, OEM behavior, Android 15 timeout, Keystore, E-Ink ghosting).
- Production signing key.
- Live gateway deployment/TLS.
- ntfy admin UI: the server is unauthenticated, and the app does not expose user/ACL/token management. The admin client is a boundary only.
- ntfy authentication enablement and app token (server currently open).
- Binary attachment upload (URL/metadata only by design).
- Boox vendor-specific refresh tuning (device not arrived).

## Security notes

- The live ntfy server is unauthenticated; anyone with a topic name can read/publish. Enable auth only after updating all three producers.
- The old `nfty` hostname is a 502 typo; use `ntfy`.
- The test signing key and passwords are outside Git at `~/.config/kaminari-nifty/` (700/600).
- The owner-supplied PVE root and Mint passwords from earlier chat are compromised; rotate them.
- The PVE gateway token is read-only and not in the APK.

## Owner files

| File | Contents |
|---|---|
| `~/.config/kaminari-nifty/NTFY_ONBOARDING_PRIVATE.txt` | URL, auth status, topics, enablement/revocation |
| `~/.config/kaminari-nifty/PVE_GATEWAY_PRIVATE_INFO.txt` | PVE token ID/secret, ACL, gateway env names, revocation |
| `~/.config/kaminari-nifty/OWNER_PRIVATE_SIGNING_INFO.txt` | test keystore path, aliases, certificate hashes, build vars |
| `~/.config/kaminari-nifty/OWNER_NTFY_ADMIN_PRIVATE.txt` | admin account status (none; auth disabled) |

## Next step

Install `KaminariNfty_final_release.apk`, configure Guest + `https://ntfy.rplab.qzz.io`, add the two approved topics, and complete `OWNER_DEVICE_TESTING_CHECKLIST.md`. Report bugs with the checklist fields.
