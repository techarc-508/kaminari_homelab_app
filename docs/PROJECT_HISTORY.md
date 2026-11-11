# Project history

Kaminari Nfty was built as a sequence of owner-approved phases. The repository preserves the real commit history; earlier audits and reports are kept rather than rewritten.

| Phase | Outcome |
|---|---|
| Read-only audit | Full repository, ntfy API, storage, security and E-Ink analysis; audit reports and Phase 0 plan |
| Phase 0 | JDK/SDK/Gradle toolchain, package rename to `com.rplab.kaminarinfty`, baseline build and tests |
| Phase 1 | Room persistence, DataStore, Keystore credentials, profiles, deduplication, pagination |
| Phase 2 | ntfy JSON stream, health, reconnect, publish client, contract tests |
| Phase 3 | Instant/Battery Saver delivery, notifications, boot restore, process-death recovery, remediation audit |
| Phase 4 | Compose publishing, outbox state machine, activity timeline, five destination UX, typed mock commands |
| Phase 5 | Generic E-Ink presentation profile |
| Phase 6 | FastAPI gateway, ntfy admin client boundary, backend labels |
| Phase 7 | Integration hardening and security review |
| Final pass | Live PVE/ntfy audit, canonical URL, least-privilege PVE token, documentation library, public GitHub publication, final APK verification |

Notable verified facts:

- Android test suite: **361 passed / 0 failed / 2 skipped** (Robolectric Keystore round-trip skips).
- Gateway tests: **9 passed**.
- Lint: **0 errors / 56 warnings**.
- Live ntfy: `https://ntfy.rplab.qzz.io`, version 2.28.0, health 200.
- PVE: 9.2.11, read-only `<gateway-user>@pve!<token-id>` token verified against `/version`, `/nodes`, `/qemu`, `/lxc`, `/status`.

The original audit found the prototype was not buildable and had no persistence or notifications; those gaps were closed in later phases. Earlier reports remain available for traceability.
