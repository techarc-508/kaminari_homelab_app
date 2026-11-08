# Kaminari Nfty — Changelog

Format: `date · phase/commit · fact` (append-only).

- 2026-11-08 · audit · READ-ONLY audit delivered: `AUDIT_REPORT.md`, `CURRENT_ARCHITECTURE.md`, `API_CAPABILITY_MATRIX.md`, `PHASE_0_EXECUTION_PLAN.md`; `ctx_note #2` decision trace completed with `OWNER_DECISION_REVIEW.md`.
- 2026-11-08 · decision · **Owner approval received** (chat): `Approved: D1-D6, R-1, C3, C4, applicationId com.rplab.kaminarinfty — start Phase 0.` Sudo access supplied for D1 toolchain install; password kept session-only, never persisted.
- 2026-11-08 · phase-0 · Ordered changes per approved decisions (now executing):
  - D1 toolchain: `apt install openjdk-21-jdk-headless` → OpenJDK 21.0.12.b1 live (exit 0); cmdline-tools + Gradle 9.3.1 downloads in flight into `~/opt`.
  - D4 git: repo initialized (`main`), baseline commit `dac123d` + tag `audit-baseline`.
  - D2 identity: namespace + applicationId → `com.rplab.kaminarinfty` (commits `a8a03b8`, fixup `5a44fac`; zero old-ID references remain in app sources).
  - C3 doc amendment: `PLAN.md` pipeline event list → now includes `message_delete`, `message_clear` per docs (server v2.37.2).
  - B-4 keystore: `${rootDir}/debug.keystore` created via `keytool` (alias `androiddebugkey`, storepass/keypass `android` per `app/build.gradle.kts:35-40`); path already git-ignored in baseline `.gitignore`.
  - Pending in phase 0: T0.2 wrapper restore (`gradle wrapper --gradle-version 9.3.1`), T0.5 baseline `assembleDebug`/`testDebugUnitTest`/`lintDebug`/`:app:dependencies` evidence, T0.6 AGP×BOM compat repair if needed, T0.7 reachability probe `curl https://nfty.rplab.qzz.io/v1/health` (owner-supplied SSH `ntfy version` still open from owner), T0.8 `PHASE_0_REPORT.md`.
- 2026-11-08 · risk · open from audit (unchanged): no SSH probe result yet for exact ntfy server version; device is not connected (adb blocked); E-Ink vendor tuning intentionally deferred until the Boox arrives.
- 2026-11-08 · phase-0/T0.5 · Baseline evidence complete: `assembleDebug` SUCCESS 3m47s → `app-debug.apk` 23,492,596 B; `testDebugUnitTest` SUCCESS 1m40s, **7 tests / 0 failures** (6 Robolectric + 1 JUnit); `lintDebug` SUCCESS 1m16s, **0 errors / 47 warnings** (22 GradleDependency, 9 NewerVersionAvailable, 7 UnusedResources, 5 IconLauncherShape, 4 misc); `:app:dependencies` exit 0 with `firebase-ai`+appcheck+retrofit+moshi confirmed in runtime classpath (A-1 prune list). **T0.6 compat repair not required** (AGP 9.1.1 × BOM 2024.09.00 compiled clean). T0.7 curl probe: `https://nfty.rplab.qzz.io/v1/health` → `error code: 502` (TLS OK, origin down). `PHASE_0_REPORT.md` finalized → **PASS**, awaiting owner approval for Phase 1.
