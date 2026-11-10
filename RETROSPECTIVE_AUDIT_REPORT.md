# Kaminari Nfty — Retrospective Audit Report

**Audit window:** Phase 5 → Phase 7 (commits `dff5641`, `be4f9f3`, `2dc60dd`).
**Audited against:** `2dc60dd`.
**Date:** 2026-11-10
**Auditor context:** independent code inspection + automated test/build evidence from
this environment.

## 1. Baseline established

- Git HEAD: `2dc60dd` (Phase 7).
- Working tree status when audit started: clean (no uncommitted edits in
`app/src/main`, `gateway/`, or root docs after restore of `settings.gradle.kts`).
- Previous phase commits verified present:
  - Phase 5: `dff5641`
  - Phase 6: `be4f9f3`
  - Phase 7: `2dc60dd`

### 1.1 Traceability matrix

| Phase | Requirement | Actual implementation | Test evidence | Result |
|---|---|---|---|---|
| 5 | Optional E-Ink profile exists and is invokable | `ThemeSetting.EINK` enum value in `MainViewModel.kt`; SettingsScreen renders a selectable "E-Ink" option; MainActivity reads `themeSetting == ThemeSetting.EINK`; `MyApplicationTheme(eink = isEink)` used app-wide | Settings UI is wired; no dedicated E-Ink unit test in suite | **PASS WITH CONDITIONS** (see §2) |
| 5 | E-Ink palette/tokens are real, not placeholder | `Color.kt` defines E-Ink colors; `Theme.kt` defines `EinkColorScheme` and `KaminariEinkTokens`; `MyApplicationTheme` swaps to them | No UI regression test for palette application in current suite | **PASS WITH CONDITIONS** (see §2) |
| 5 | Profile persists across restart | `AppPreferences` stores `ThemeSetting` via DataStore; `MainViewModel` restores `themeSetting` from `AppPreferences` | Persistence is covered by DataStore preferences tests for the preferences system; no end-to-end theme-persistence test in suite | **PASS WITH CONDITIONS** (see §2) |
| 5 | Standard profiles untouched | Day/Night/System logic still present; E-Ink is additive in `when` branches | Existing theme tests pass; theme switching flow is unchanged aside from extra branch | **PASS** (implementation-level) |
| 6 | PVE gateway present and secured | `gateway/` FastAPI app with auth, rate limiting, request size limit, replay protection, mutation guard; Phase 7 added node/VMID validation for read endpoints | Gateway suite: 9 passed, 1 warning | **PASS WITH CONDITIONS** (see §3) |
| 6 | Real gateway executor in Android | `GatewayCommandExecutor` implemented; Phase 7 added 1 MiB response size limit; Phase 6+ wiring uses executor target in `CommandRepository` | Android test: `GatewayCommandExecutorTest` present and part of 354-test suite | **PASS WITH CONDITIONS** (see §3) |
| 6 | ntfy administration client | `NtfyAdminClient` for list/create/delete user and grant access; Phase 7 added explicit HTTP timeouts | Android test: `NtfyAdminClientTest` present in suite | **PASS WITH CONDITIONS** (see §3) |
| 6 | Command Centre backend label | `CommandUiState.backendLabel` + `CommandViewModel(backendLabel = …)` + `CommandScreen` subtitle uses `state.backendLabel` | Part of Phase 6 change; no separate regression test beyond command suite | **PASS** (implementation-level) |
| 7 | Gateway read-endpoint validation | `gateway/app/main.py`: `NODE_PATTERN`, `VMID_PATTERN`, `validate_node`, `validate_vmid` applied to `/qemu`, `/lxc`, `/status`, `/storage`, `/tasks` | Not yet exercised by a dedicated negative gateway test in current suite | **PASS WITH CONDITIONS** (see §4) |
| 7 | Android gateway response-size limit | `GatewayCommandExecutor` checks `Content-Length` and then actual body length against `maxResponseBytes = 1_048_576` | Not yet exercised by a dedicated large-response test in current suite | **PASS WITH CONDITIONS** (see §4) |
| 7 | ntfy administration HTTP timeouts | `NtfyAdminClient` default client now builds `OkHttpClient` with connect/read/write timeouts | Not yet exercised by a dedicated timeout test in current suite | **PASS WITH CONDITIONS** (see §4) |

## 2. Phase 5 — E-Ink and compatibility

### 2.1 What was verified in source

- `ThemeSetting` is a real enum with four values including `EINK("E-Ink (High Contrast)")` (`MainViewModel.kt`).
- The enum is stored and loaded from DataStore-backed preferences (`AppPreferences`), so it survives process restart as a DataStore value.
- SettingsScreen exposes the value through a real selectable UI row for each `ThemeSetting` including `EINK`, and forwards selection to `onThemeChange`.
- MainActivity converts the enum into a concrete theme decision:
  - `isEink = uiState.themeSetting == ThemeSetting.EINK`
  - `isDarkTheme` for E-Ink is forced to `false`
  - The entire app is composed inside `MyApplicationTheme(darkTheme = isDarkTheme, eink = isEink)`
- Theme.kt contains a dedicated E-Ink color scheme and an E-Ink token set:
  - `EinkColorScheme` is a real light `colorScheme` with black primary on white.
  - `KaminariEinkTokens` is a real `KaminariColors` set applied through `LocalKaminariColors`.
- `MyApplicationTheme` branches on `eink` before `darkTheme`, so E-Ink is not just a tint but a distinct theme path.

### 2.2 What Phase 5 actually does

Phase 5 is not "just an enum". It is a real, functional theme path:
- selection
- persistence
- app-wide consumption
- a dedicated palette and token set

### 2.3 What Phase 5 does not do

- It does not include Onyx Boox device-specific tuning.
- It does not include device/emulator verification in this environment.
- It does not include a dedicated Compose UI test for E-Ink palette application in the current suite.

### 2.4 Phase 5 verdict

**PASS WITH CONDITIONS**.

Implementation is genuine and coherent. The remaining conditions are:
- no physical E-Ink/Onyx device validation
- no dedicated functional UI test asserting the E-Ink palette is actually applied at the screen level in the current suite

These are owner/hardware-gated validation gaps, not implementation defects.

## 3. Phase 6 — Gateway and administration

### 3.1 What was verified in source

- Gateway service exists under `gateway/` and is a FastAPI app.
- Authentication is enforced for non-health endpoints via bearer token.
- Rate limiting, request size limits, replay protection, typed command validation, mutation gating, and read-only Proxmox endpoints are present.
- Android has a real gateway executor `GatewayCommandExecutor` and a real ntfy administration client `NtfyAdminClient`.
- `CommandRepository` reads the executor target and uses it for envelope target/expected-target instead of hardcoding the mock target.
- `CommandScreen` subtitle is driven by `state.backendLabel`, so the UI reflects whether the active backend is mock or gateway.

### 3.2 Security-relevant source findings

- No Proxmox credentials appear in app source. Credentials are not embedded.
- TLS is enforced by default in `GatewayCommandExecutor` (`requireHttps = true`) and in `NtfyAdminClient` (`requireHttps = true`).
- Plain `OkHttpClient` is not used as final default anymore; Phase 7 changed `NtfyAdminClient` default client to a builder with explicit timeouts.
- Path construction for Proxmox read endpoints is now validated in Phase 7.
- There is no shell, SSH, or unrestricted URL proxy in the app or gateway.

### 3.3 Phase 6 verdict

**PASS WITH CONDITIONS**.

The implementation matches the approved Phase 6 intent. Conditions:
- live Proxmox/ntfy server validation not done here
- gateway HTTPS deployment and least-privilege Proxmox ACL setup not executed here
- mutations remain disabled by default and were not run against production

These are operational/deployment gaps, not code defects.

## 4. Phase 7 — Integration and hardening

### 4.1 What was verified in source

- `gateway/app/main.py` now contains node/VMID validation before path construction.
- `GatewayCommandExecutor` now contains a 1 MiB response-size guard.
- `NtfyAdminClient` now contains explicit HTTP timeouts in its default client.
- Phase 7 report, security audit report, and integration test report exist and were committed.

### 4.2 What Phase 7 claims vs what automated evidence shows

- The Phase 7 report says the gateway response-size limit and ntfy timeouts were added. Verified: yes.
- The Phase 7 report says the gateway tests passed. Verified: yes, 9 passed / 1 warning.
- The Phase 7 report says Android tests were not rerun in the previous environment. Verified: that limitation still applies in part here; the full Android suite was recovered in this audit and is reported in §5.

### 4.3 Phase 7 verdict

**PASS WITH CONDITIONS**.

Hardening is real and matches the report. Conditions:
- Phase 7 gateway hardening is not yet backed by a dedicated negative gateway test for invalid node/VMID in the current suite.
- Phase 7 Android hardening is not yet backed by a dedicated large-response/timeout test in the current suite.
These are coverage gaps, not correctness failures.

## 5. Verification evidence

### 5.1 Android tests

- Command run: `./gradlew testDebugUnitTest --no-daemon`
- Result: `BUILD SUCCESSFUL`
- Parsed from XML test results: `tests=354 failures=0 errors=0 skipped=2`

Notes:
- 2 pre-existing skips are still present.
- No new failures introduced across Phases 5–7 in this environment.

### 5.2 Debug build

- Command run: `./gradlew assembleDebug --no-daemon --rerun-tasks`
- Result: `BUILD SUCCESSFUL`
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- APK size: `19918197` bytes
- APK signing: debug-signed via `debug.keystore` in repo root (git-ignored); no production/release key present or configured in this repository.

### 5.3 Lint

- Command run: `./gradlew lintDebug --no-daemon --rerun-tasks` (first attempt failed only because the Android test compile classpath could not resolve `com.google.errorprone:error_prone_annotations:2.30.0` due to DNS/network; second attempt with `--no-configuration-cache` completed)
- Result: `BUILD SUCCESSFUL`, 0 errors
- Warnings present: outdated dependency/AGP versions, `UnusedResources`, `IconLauncherShape`, `DataExtractionRules`, and similar informational/correctness warnings.
- No new lint errors introduced by Phases 5–7.

### 5.4 Gateway tests

- Command run: `PYTHONPATH=. python3 -m pytest -q` from `gateway/`
- Result: 9 passed, 1 warning

### 5.5 Network/dependency limitation

- `lintDebug` first failed only because `repo.maven.apache.org` was not resolvable in this environment at that moment; that failure was a dependency-resolution/DNS issue, not a source or build configuration failure.
- `assembleDebug` and `testDebugUnitTest` succeeded, showing the affected path is not a hard project-wide block.

## 6. Security findings

### 6.1 Confirmed controls

- Mock executor is default in `AppContainer`; real gateway is not the default without explicit wiring.
- Proxmox credentials are not in app source.
- TLS enforcement is present in both gateway executor and ntfy admin client.
- Gateway enforces auth, rate limits, size limit, replay protection, mutation gating.
- Phase 7 added input validation on gateway read paths.
- Android gateway executor limits response size and does not retry ambiguous mutations.

### 6.2 Residual / outstanding items

- Gateway chunked-request handling is not separately hardened here beyond the existing content-length path; production reverse-proxy limits should also be enforced.
- No dedicated gateway negative tests for Phase 7 validation were added in this audit.
- No live security/penetration testing or device validation performed.
- Secrets handling depends on deployment hygiene; not validated live.

## 7. Regressions discovered

None in this environment.

- Android tests: 354 / 0 / 0 / 2 — same shape expected from Phase 6 baseline.
- Debug build: successful.
- Lint: 0 errors.
- Gateway tests: 9 passed.

No regression in compilation, unit tests, debug packaging, or lint was observed after Phases 5–7 in this environment.

## 8. Fixes applied during audit

None.

No Phase 5–7 defect requiring a code change was found during this independent audit. Observed items were either genuine implementation gaps in coverage/validation only, or operational/deployment gaps, not broken features.

## 9. Remaining conditions

### 9.1 Verification conditions

- No live Proxmox or ntfy server available in this environment.
- No physical device/emulator available in this environment.
- No production signing key available in this repository or environment.

### 9.2 Test coverage conditions

- E-Ink: no dedicated Compose unit test asserting palette application.
- Phase 7 gateway validation: no dedicated negative-node/VMID gateway test.
- Phase 7 Android hardening: no dedicated large-response/timeout tests.

### 9.3 Documentation conditions

- Some Phase 7 reports were already produced in Phase 7; this audit does not replace them but reconciles them with source.

## 10. Final verdicts

- Phase 5 — **PASS WITH CONDITIONS**
- Phase 6 — **PASS WITH CONDITIONS**
- Phase 7 — **PASS WITH CONDITIONS**

Overall retrospective audit result: **PASS WITH CONDITIONS**.

The phases are implemented as described, the automated evidence in this environment is clean, and no critical implementation defect was found. Remaining items are validation, deployment, hardware, signing, and test-coverage conditions rather than broken functionality.

## 11. Stop / continue guidance

This audit does not block continuation to Stage 1 verification, but it does require honest handling of the remaining conditions in every downstream report and in the final release assessment. No mock behavior has been misrepresented as production behavior in the current source; that claim is supported by the source inspection in §3.

