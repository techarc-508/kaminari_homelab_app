# Phase 6 Report — Secure Administration & Real Proxmox Gateway

**Status:** Complete — PASS WITH CONDITIONS.
**Last updated:** 2026-11-10

## Summary

Phase 6 adds a separate FastAPI Proxmox gateway, an Android gateway command executor, an ntfy administration client, and backend identification in the Command Centre. The existing mock executor remains the default and functional.

## Gateway

- Location: `gateway/`
- FastAPI service with HTTPS deployment guidance, bearer-token auth, rate limiting, request size limits, replay protection, typed command validation, read-only Proxmox endpoints, and mutation endpoints disabled by default.
- Mutations require `GATEWAY_MUTATIONS_ENABLED=true` and an approval token matching the command nonce.
- Proxmox API token remains server-side.

## Android

- `GatewayCommandExecutor` implements `CommandExecutor` over HTTPS with platform TLS validation, timeouts, and no automatic retries.
- `NtfyAdminClient` supports documented ntfy admin endpoints: list/create/delete users and grant access.
- Command Centre UI now shows the active backend label.
- Mock executor remains available for tests/offline development.

## Verification

- Gateway tests: 9 passed.
- Android tests: 354 passed, 0 failures, 0 errors, 2 pre-existing skips.
- `assembleDebug --rerun-tasks`: BUILD SUCCESSFUL.
- `lintDebug --rerun-tasks`: BUILD SUCCESSFUL, 0 errors, 56 warnings.

## Conditions / Pending Validation

- No live Proxmox/ntfy server validation was performed.
- Gateway HTTPS deployment and real Proxmox ACL provisioning are documented but not executed.
- Mutations remain disabled by default and were not executed against production.
- ntfy admin UI integration is client-level; broader Settings UI workflows remain for later phases.