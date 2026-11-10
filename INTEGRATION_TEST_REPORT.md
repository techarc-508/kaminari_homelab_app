# Integration Test Report — Phase 7

**Status:** Review complete.
**Last updated:** 2026-11-10

## Automated Tests

- Gateway: 9 passed, 1 warning.
  - Command: `PYTHONPATH=. python3 -m pytest -q`
  - Coverage includes health, auth, host health, replay rejection, mutation
    approval, invalid VMID, and rate limiting.
- Android: not rerun in this environment due to offline Gradle dependency
  resolution failure. Phase 6 baseline: 354 passed, 0 failures, 0 errors, 2
  pre-existing skips.

## Integration Areas Reviewed

- Gateway command envelope validation and replay cache.
- Mutation gating and approval token handling.
- Proxmox path construction for read endpoints.
- Android gateway executor response handling.
- ntfy administration client HTTPS and timeout posture.

## Unverified Areas

- Live Proxmox gateway end-to-end command execution.
- Real ntfy administration workflows.
- Android app integration with a configured gateway.
- Device/emulator runtime behavior.
- Release build and lint in the current environment.
