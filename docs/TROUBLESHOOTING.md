# Troubleshooting

## `https://nfty.rplab.qzz.io` returns 502

That hostname is not the canonical endpoint. Use `https://ntfy.rplab.qzz.io`. The old spelling still returns Cloudflare HTTP 502.

## Health works in `curl` but the app fails

- The app enforces HTTPS by default.
- `http://<private-ntfy-lan>:8201` is a LAN origin for CLI clients; the app rejects plaintext HTTP and Android denies cleartext by default.
- Use the public HTTPS URL. Check certificate validity and DNS from the phone.

## `401`/`403`

- The live server currently has auth disabled; Guest is correct.
- If auth is enabled later, verify the token belongs to a non-admin user with read/write ACLs for the topic.
- A transport connection does not prove topic permission; check the sanitized status in Settings.

## No notifications

- Android 13+: grant POST_NOTIFICATIONS.
- Check Settings → notification status.
- Instant is best effort; Android 15+ limits `dataSync` foreground services.
- Battery Saver is periodic and inexact; Doze and OEM battery managers can delay it.
- Reboot restores the mode only after first unlock.

## Outbox stuck in `SENDING`

The app recovers orphaned `SENDING` rows to `AMBIGUOUS` on next startup, without resending. Retry manually and confirm the duplicate risk.

## Publish says `AMBIGUOUS`

A timeout does not prove the server rejected it. ntfy has no idempotency key; retry only after checking the topic in another client. The app requires explicit confirmation.

## Release APK will not install over debug

Expected: different signing certificates under the same `applicationId`. Uninstall the old variant first; back up data you need. Debug and release cannot update each other in place.

## Build fails with Gradle distribution download

This VM’s JVM cannot TCP-connect to `services.gradle.org` even though `curl` works. The wrapper is pinned to the local distribution `file:///home/pushpal/opt/gradle-9.3.1-bin.zip`. Use that wrapper; do not change the distribution URL without fixing the JVM network issue.

## Gateway `readyz` fails

- Verify `GATEWAY_PROXMOX_BASE_URL` is HTTPS.
- Install the PVE CA certificate for `GATEWAY_PROXMOX_VERIFY_TLS=true`.
- Confirm the `<gateway-user>@pve!<token-id>` token is enabled and has `KaminariRead` on `/`.
- Do not disable TLS verification as a quick fix.

## Secret scan reports a test fixture

The repository intentionally contains no live tokens. If a scanner flags a test string, confirm it is a placeholder such as `tk_test` and document the suppression; never add a real credential.
