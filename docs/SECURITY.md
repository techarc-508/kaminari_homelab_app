# Security

## Threat model

The app is a client for a self-hosted ntfy server. It must assume:

- The network is untrusted; TLS is required by default.
- A notification is **data**, never an executable instruction.
- A topic name is not a secret; anyone with the name can publish unless the server enforces auth.
- A successful publish is a server receipt, not proof of delivery or human read.

## Credentials

- Android Keystore AES/GCM key encrypts the credential envelope before it is written.
- Only the encrypted envelope is persisted; plaintext exists in memory for a request.
- Stored secrets are never rendered in the UI or logged.
- Logout removes the stored secret for the active profile.
- No token, password, PVE secret or signing key is in the APK, source, docs or Git history.
- The live ntfy server has authentication disabled, so the app uses Guest mode; there is no token to leak.

## Network

- HTTPS is enforced by default (`useHttpsOnly=true`).
- The Android manifest does not opt into global cleartext traffic; target SDK 36 defaults to cleartext denied.
- The app does not follow publish redirects and does not automatically retry an ambiguous publish.
- The gateway URL is a separate trust boundary; the APK does not hold Proxmox credentials.

## Command safety

- `rplab.command.v1` is a typed envelope, not free text.
- Fixed allowlist of actions; unknown action IDs are rejected.
- Five-minute expiry and single-use nonce.
- Destructive actions require a second confirmation bound to the nonce.
- The wired executor is `MockCommandExecutor`; it has no shell, SSH, file or network execution.
- A future gateway must independently re-validate authorization, expiry and replay; the app is not trusted.

## Proxmox least privilege

- Dedicated service account `<gateway-user>@pve`.
- Token `<token-id>` with privilege separation.
- ACL `KaminariRead` on `/` (audit only).
- No power, configuration, user or storage mutation privilege.
- Token secret only in the owner-private file; not in the APK, source, docs or Git.
- Gateway mutations disabled; no unrestricted shell or SSH endpoint.

## Secret handling and history

- The public Git history was scanned with gitleaks 8.30.1: **0 findings** in history and **0 findings** in the current tree.
- Old test-signing passwords that appeared in earlier unpublished commits were removed by rebuilding the unpublished local history; no remote commit was rewritten.
- The current test keystore and passwords are outside Git at `~/.config/kaminari-nifty/` (700/600).
- The old owner-supplied infrastructure passwords are considered compromised and must be rotated by the owner. They are not used by this project.
- No `.env`, token file, keystore or private key is committed.

## Release signing

- The release APK is **test-only signed** by `CN=Kaminari Nfty Test Signing, OU=RPLab, O=Kaminari`.
- It is not a Play Store/production identity.
- Debug and release APKs use different certificates, so they cannot be installed over each other without uninstalling.
- Generate a new production key only when the owner is ready; never commit it.

## Reporting a vulnerability

Contact the owner privately. Include the app version, APK SHA-256, Android version and a redacted reproduction. Do not include tokens, passwords or notification contents.
