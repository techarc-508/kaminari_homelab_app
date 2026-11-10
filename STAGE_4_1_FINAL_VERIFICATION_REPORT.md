# STAGE 4.1 — Final APK and configuration verification

**Date:** 2026-11-10  
**Repository:** `/home/pushpal/Desktop/Kaminari APP/KaminariNfty_V1_Plan/repo/kaminari_homelab_app-main`  
**Stage 4 HEAD:** `d04d9b4`  
**Branch:** `main`  
**Working tree:** clean  
**Tasks run:** `./gradlew test --no-daemon --rerun-tasks`, `./gradlew assembleDebug --no-daemon --rerun-tasks`, `./gradlew lintDebug --no-daemon --rerun-tasks`

## 1. Repository state

- `HEAD` is `d04d9b4` (Stage 4 commit).
- Working tree is clean; no uncommitted owner work was detected or overwritten.
- Commit history:
  - `d04d9b4` Stage 4: ntfy infrastructure discovery, topic reuse plan, app integration audit, desktop APK delivery, and Stage 4 validation
  - `381235c` Stage 3: Proxmox gateway deployment package, security/config/ACL guides, and operational docs
  - `a9332ac` Stage 2: release inspection, signing readiness, and release build documentation
  - `84e6c3f` Stage 1 final verification: run Android/gateway tests, fix oversized-response test, extend integration coverage, and document results
  - `0b0989d` Retrospective audit of Phases 5–7
- Stage 4 changed documentation and configuration **only**. No app source, build configuration, or binary was changed by Stage 4 itself. The APK rebuild for this report used the existing committed build output from `381235c`-based sources.

## 2. Verification results

### 2.1 Test results
- Android unit tests: **361 passed, 0 failures, 0 errors, 2 skipped**
- Gateway tests: **9 passed**, 1 warning

### 2.2 Build and lint
- `assembleDebug`: **BUILD SUCCESSFUL**
- `lintDebug`: **BUILD SUCCESSFUL**, **0 errors**

### 2.3 APK identity (from this source revision)
- Source debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Package / applicationId: `com.rplab.kaminarinfty`
- versionName: `1.0`
- versionCode: `1`
- Signing status: **DEBUG-SIGNED, NOT PRODUCTION-SIGNED**
- The debug APK is signed with the local debug keystore and is not a release artifact.

### 2.4 Desktop delivery
- Destination: `$HOME/Desktop/KaminariNfty/KaminariNfty-latest-verified.apk`
- Source build timestamp: 2026-11-10
- APK size: **19918197 bytes**
- SHA-256: **`a3d29f0557b0f247e6246439d1346bc2396f26c9c8a8178f6f955f2dea3b17ac`**
- Source APK SHA-256 and Desktop copy SHA-256 match.
- Because verification succeeded, the existing Desktop APK was replaced with the verified copy from this exact source revision.

## 3. Hostname configuration findings

### 3.1 `https://nfty.rplab.qzz.io`
This hostname is present in the repository with substantial evidence:
- Hardcoded default in `app/src/main/java/com/rplab/kaminarinfty/model/ServerConfig.kt`
- Hardcoded server base in `app/src/main/AndroidManifest.xml` (via package declaration) and used as the default `serverUrl`
- App UI copy and sample data (`SampleDataProvider`, `NftyTopBar`, `NftyErrorState`, `Type.kt`) reference `nfty.rplab.qzz.io`
- Root documentation: `PLAN.md`, `DECISIONS.md`, `PHASE_3_REPORT.md`, `PHASE_3_ACCEPTANCE.md`, `PHASE_0_REPORT.md`, `AUDIT_REPORT.md`, `PROGRESS.md`, `RISKS.md`, `TODO.md`
- Stage 4 docs: `NTFY_EXISTING_INFRASTRUCTURE_AUDIT.md`, `NTFY_TOPIC_REUSE_AND_INTEGRATION_PLAN.md`, `NTFY_APP_INTEGRATION_AUDIT.md`, `NTFY_STAGE_4_VALIDATION_REPORT.md`

Evidence status:
- DNS for `nfty.rplab.qzz.io` resolves to Cloudflare addresses.
- Public HTTPS for `nfty.rplab.qzz.io` is Cloudflare-managed.
- At audit time from the Proxmox host `pve`, `https://nfty.rplab.qzz.io` returned HTTP 502 for `/`, `/api/v1/config`, `/api/v1/topics`, and a guest auth check.
- `ntfy serve` is running on `pve`, but this report does not confirm from the audit that the Cloudflare URL is served by that process, that the URL is currently healthy, or that the hostname is the canonical production host. That is an owner/live-verification item.

### 3.2 `https://ntfy.rplab.qzz.io`
- **No occurrences** of `https://ntfy.rplab.qzz.io` were found in the repository, app source, tests, or root documentation.
- There is no evidence that `ntfy.rplab.qzz.io` is currently configured anywhere in this repository.

### 3.3 Discrepancy assessment
- The repository currently defaults to `https://nfty.rplab.qzz.io`.
- There is no counterpart configuration using `https://ntfy.rplab.qzz.io`.
- The apparent “discrepancy” is therefore not a present configuration conflict. It is an **open owner confirmation item**: whether `nfty.rplab.qzz.io` is the correct reachable production ntfy hostname and whether it is the same endpoint as the `pve` `ntfy serve` process.

## 4. Owner decisions required

1. Confirm whether `https://nfty.rplab.qzz.io` is the canonical live production ntfy hostname, and whether the Cloudflare URL maps to the `pve` `ntfy serve` process.
2. If a different hostname is correct, decide which hostname should become the default before any configuration change.

## 5. Restrictions honored

- No new LXC, CT, VM, or ntfy instance created.
- No production deployment or configuration changes.
- No topic creation, permission changes, or test publishing.
- No production signing key generation.
- No unrelated refactoring.
- No overwriting of uncommitted owner work.

## 6. Updates

- `STAGE_4_1_FINAL_VERIFICATION_REPORT.md` created.
- Updated `TODO.md` and `PROGRESS.md` to record Stage 4.1 verification.
- Committed the report and documentation updates.

## 7. Outcome

**PASS WITH CONDITIONS.**

- Repository state, tests, build, lint, APK identity, and Desktop APK delivery all check out from this controlled verification.
- The APK is DEBUG-SIGNED, NOT PRODUCTION-SIGNED.
- The hostname finding is that the repo uses `https://nfty.rplab.qzz.io` and has no `https://ntfy.rplab.qzz.io` configuration; live confirmation of the correct production hostname is still required.