# Decision Log — Kaminari Nfty V1

Append-only record of owner decisions and the technical choices that follow from them.

## Owner-locked

| ID | Decision | Source |
|---|---|---|
| D-01 | Delivery supports **both Instant and Battery Saver**; do not silently choose one and never call Battery Saver instant. | `AGENTS.md` owner-locked decisions |
| D-02 | Admin scope includes ntfy administration and PVE/homelab operations; PVE goes through a separately deployed trusted gateway, never credentials or arbitrary shell in the APK. | `AGENTS.md` |
| D-03 | UI provides two complete profiles: Standard Kaminari and dedicated E-Ink (not a grayscale tint). | `AGENTS.md` |
| D-04 | Onyx Boox model is unknown until the device arrives; keep E-Ink generic, no invented vendor APIs. | `AGENTS.md` |
| D-05 | Notification actions beyond "open message" (mark read/save) are deferred; not required for Phase 3. | Owner review, 2026-11-09 |
| D-06 | The `NftyTopBar` live-clock fix belongs in Phase 3, with regression evidence. | Owner review, 2026-11-09 |
| D-07 | Instant is best-effort live delivery subject to Android/OEM restrictions; Battery Saver is inexact periodic catch-up. No guarantee of instant delivery. | Owner review, 2026-11-09 |
| D-08 | Phase 4 is **not authorised** until explicit owner approval. | Owner checkpoint instruction |
| D-09 | Phase 3 is **ACCEPTED — PASS WITH CONDITIONS** (2026-11-09). The original FAIL and remediation history are preserved. No hardware validation may be claimed. | Owner instruction, 2026-11-09; `PHASE_3_ACCEPTANCE.md` |
| D-10 | The repository-root `PLAN.md` is authoritative; the plan-folder copy is a mirror. Phase 1/2 reports are mirrored into the repository for a self-contained record. | Owner instruction, 2026-11-09 |
| D-11 | Phase 4 default scope: publish with URL/metadata attachments (binary upload deferred), durable outbox, activity timeline, typed command envelope with **mock** executor, Room-backed inbox/topic UX, multiple profiles with safe switching and a migration/URL-change preview. Proposed, not authorised. | Owner instruction, 2026-11-09 |

## Technical decisions (Phase 3 remediation, 2026-11-09)

| ID | Decision | Rationale |
|---|---|---|
| T3-01 | Only `INSTANT` and `BATTERY_SAVER` are selectable delivery modes; legacy persisted `SAVED` migrates to `BATTERY_SAVER`. | Owner-locked two-mode requirement; migration preserves delivery rather than silently removing it. Saved *messages* are unrelated. |
| T3-02 | Fresh installs default to `BATTERY_SAVER`. | Least-surprise: background catch-up works without the user finding Settings, without a permanent foreground notification. |
| T3-03 | A process-wide `DeliveryStatusStore` is the source of Instant-mode connection state; the service is the only stream owner. | Fixes the false "offline" UI without opening a duplicate subscription. |
| T3-04 | On Android 15+ `dataSync` timeout, record `TIMED_OUT`, stop the service within the deadline, persist Battery Saver and schedule catch-up. | Prevents the fatal `RemoteServiceException`; the budget is a platform limit and is not extended. |
| T3-05 | Terminal auth/forbidden/retry-exhaustion stops the foreground service and posts the real reason. | A dead stream must never keep a "live delivery" notification. |
| T3-06 | Notification deep links resolve by message ID from Room after the profile loads; the in-memory window is only a cache. | Cold starts begin with an empty window; Room is the source of truth. |
| T3-07 | Keep `BOOT_COMPLETED` only (no `LOCKED_BOOT_COMPLETED`); use the app-owned scope and a tested `BootDeliveryRestorer`. | Credential-encrypted DataStore is unavailable before first unlock; the unmanaged scope violated the architecture rules. |
| T3-08 | Test-only `androidx.work:work-testing:2.10.1` is allowed. | Constructs the real worker; matches production WorkManager; not packaged in the APK. |

## Open decisions

1. Whether to add notification actions (mark read/save) in Phase 4.
2. Whether to pursue an FCM/UnifiedPush fallback (optional in PLAN).
3. E-Ink profile specifics once the Boox model/firmware is known.
4. Phase 4: approve JSON-root publish, `view`-only actions, manual-only outbox
   retry, mock-only command centre and the profile URL-change preview
   (`PHASE_4_ARCHITECTURE.md` §16).
5. Phase 4: confirm whether Saved remains an Inbox filter (recommended) or a
   bottom destination.
6. Phase 6: real allowlisted PVE actions and gateway trust boundary.

## Documentation reconciliation (2026-11-09)

- Two divergent `PLAN.md` copies existed. The repository-root copy is canonical;
  the plan-folder copy is now a mirror (cycle 2).
- `PHASE_1_REPORT.md`, `PHASE_2_REPORT.md` and `PHASE_2_GATE_REPORT.md` were
  mirrored into the repository root; no duplicate copies are maintained in the
  root.
- The handoff's phase-scope wording conflated Phase 4 with E-Ink and
  administration. `PLAN.md` is authoritative: E-Ink is Phase 5, ntfy admin and
  the PVE gateway are Phase 6.
