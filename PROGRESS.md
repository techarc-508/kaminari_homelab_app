# Progress — Kaminari Nfty V1

**Last updated:** 2026-11-09
**Repository:** `KaminariNfty_V1_Plan/repo/kaminari_homelab_app-main`
**Branch:** `main`
**HEAD before Phase 3 checkpoint:** `5d00827` — Phase 2 remediation
**Phase 4:** not started; requires explicit owner approval.

---

## Phase status

| Phase | Status | Evidence |
|---|---|---|
| Phase 0 — reproducible baseline / audit | Complete | `AUDIT_REPORT.md`, `CURRENT_ARCHITECTURE.md`, `API_CAPABILITY_MATRIX.md`, `PHASE_0_EXECUTION_PLAN.md`, `PHASE_0_REPORT.md` |
| Phase 1 — architecture and data foundation | Complete, approved | `PHASE_1_REPORT.md`; Room/DataStore/Keystore, demo isolation |
| Phase 2 — ntfy API correctness | Complete, locally verified; real-server smoke test still valuable | `PHASE_2_REPORT.md`, `PHASE_2_GATE_REPORT.md`; commit `5d00827` |
| Phase 3 — notification and background delivery | **Remediated after FAIL audit; PASS WITH CONDITIONS (device matrix pending)** | `PHASE_3_REPORT.md`, `PHASE_3_AUDIT_REPORT.md`, `PHASE_3_REMEDIATION_REPORT.md`, `PHASE_3_FINAL_AUDIT.md` |
| Phase 4 — Compose/activity/command centre | Not started | Awaiting owner approval |

## Phase 3 remediation outcome

- Android 15+ `dataSync` timeout handled (`onTimeout`), with Battery Saver fallback and no crash path.
- The foreground service stops on terminal auth/forbidden/retry-exhaustion failures and never keeps a
  "live delivery" notification over a dead stream.
- Instant connection status is owned by the service and surfaced truthfully in the UI; the ViewModel
  does not open a second stream.
- Cold-start notification deep links load the target from Room by ID.
- Delivery modes are exactly Instant and Battery Saver; legacy `SAVED` selections migrate to Battery
  Saver; fresh installs default to Battery Saver.
- The top-bar clock fix is preserved with a real regression test.

## Verification (final, each task run separately with `--rerun-tasks`)

```text
./gradlew testDebugUnitTest   # exit 0 — 173 tests, 0 failures, 0 errors, 2 pre-existing skips
./gradlew assembleDebug       # exit 0 — app-debug.apk, 19,557,690 bytes
./gradlew lint                # exit 0 — 0 errors, 56 warnings
```

No device or emulator was available (`adb` not installed); hardware validation remains outstanding.

## Git

- Phase 1 and Phase 2 commits are preserved (`f254cd6`, `e6cdba5`, `5d00827`).
- Phase 3 is checkpointed in a single commit after remediation; see the commit log for the hash.
- Never reset/clean the repository; do not rewrite history.

## Next

1. Owner review of `PHASE_3_FINAL_AUDIT.md` and the two-mode/migration decision.
2. Device test matrix in `PHASE_3_REPORT.md` §6.
3. Owner authorisation before Phase 4 (Compose/publish, activity timeline, command centre, E-Ink,
   ntfy admin, PVE gateway).
