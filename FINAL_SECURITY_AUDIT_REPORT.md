# Final security audit report

Date: 2026-11-11 UTC.

## Secret scan

Tool: **gitleaks 8.30.1**.

| Scan | Findings |
|---|---|
| Full Git history (`gitleaks detect`) | **0** |
| Current tree (`gitleaks detect --no-git`) | **0** |

Commands:

```bash
gitleaks detect --source "$PWD" --redact --report-format json --report-path /tmp/gitleaks_history.json
gitleaks detect --no-git --source "$PWD" --redact --report-format json --report-path /tmp/gitleaks_tree.json
```

Earlier unpublished history contained literal test-signing passwords in Stage 5 docs. Those commits were rebuilt before publication; the remote was never force-pushed. The current tree and every published commit are clean.

## Repository checks

- No `.env`, token file, keystore, private key, APK or build cache is committed.
- `.gitignore` excludes keystores, `.env`, build outputs and local SDK paths.
- The current test keystore and password are outside Git at `~/.config/kaminari-nifty/` (700/600).
- Owner-supplied infrastructure passwords from earlier chat are considered compromised and are not used. The owner must rotate PVE root and Mint passwords.
- No Proxmox token, ntfy token or GitHub PAT appears in the repository, docs or final response.

## Android security

| Control | State |
|---|---|
| Credential storage | Android Keystore AES/GCM envelope |
| Plaintext secret logging | none found |
| HTTPS enforcement | default on |
| Cleartext traffic | not enabled; target SDK 36 default deny |
| Publish redirects | disabled |
| Automatic publish retry | none |
| Command executor | mock only; no shell/SSH/network |
| Admin credentials in APK | none |
| Backup/debug exposure | debug-only build; release minified |

## Release signing

- Release APK is **test-only signed** (`CN=Kaminari Nfty Test Signing`).
- Not a production/Play Store key.
- Debug and release certificates differ; cross-install requires uninstall.
- Production key not generated, by instruction.

## Residual risks

- The live ntfy server is unauthenticated; topic names are the only control.
- The gateway is not deployed; its future TLS and network boundary must be reviewed before exposure.
- Physical-device notification/Doze/Keystore behavior is unverified.
- `dataSync` foreground service limits on Android 15+ make Instant best effort.

## Verdict

**PASS for public source publication and test-only APK delivery.** Production signing and live gateway deployment remain separate owner decisions.
