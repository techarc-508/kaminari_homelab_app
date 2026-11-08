# Owner-locked product decisions — read before starting

These choices are already made; do not ask the owner to choose them again:
- Delivery: **both Instant and Battery Saver modes**.
- Admin: **both ntfy administration and PVE/homelab administration through a separate trusted command gateway**.
- UI: **two complete profiles, Standard Kaminari and dedicated E-Ink**.
- Onyx Boox model: **not yet known; device has not arrived**. Keep generic E-Ink behavior model-agnostic and defer vendor-specific tuning.

Prioritize reliable core receive/publish/persistence first, then both delivery modes, dual UI profiles, verified ntfy administration, and finally secure PVE gateway integration. Device-specific Boox tuning is gated on receiving the device. Never weaken security to accelerate admin features.

---

# START PROMPT — Kaminari Nfty Repository Deep Audit (READ-ONLY)

You are the lead Android architect coordinating a minimum four-specialist-agent review of the existing Kaminari Nfty repository. Your goal is to deeply understand the actual codebase and produce an evidence-based audit and first-working-model implementation plan for Codex/OpenCode execution on the owner's Linux Mint VM hosted on Proxmox.

## Read first

1. Read `AGENTS.md` completely. It is the binding engineering and safety contract.
2. Read `PLAN.md` completely. It defines the product target, phase boundaries and acceptance tests.
3. Inspect the entire repository, not just the files listed in the plan. The ZIP currently contains a Kotlin/Jetpack Compose Android app with `MainActivity`, `MainViewModel`, `NftyStreamClient`, `NftyRepository`, sample data and screens for Inbox, Topics, Setup, Saved, Search, Detail and Settings. Verify every statement against the current checkout; do not assume the ZIP has remained unchanged.

## Current task: READ-ONLY AUDIT ONLY

Do not implement features. Do not edit app source, Gradle files, manifest, resources, package identifiers, signing configuration, server settings, credentials or production data. You may create audit documentation only: `AUDIT_REPORT.md`, `CURRENT_ARCHITECTURE.md`, `API_CAPABILITY_MATRIX.md` and a proposed `PHASE_0_EXECUTION_PLAN.md`.

Do not start Phase 1. Do not create a working implementation or make cosmetic UI changes. Stop after presenting the report and request owner approval.

## Required specialist agents

Spawn at least four agents with non-overlapping review responsibilities:

- **Android/UI:** startup, navigation, Compose structure, screen completeness, accessibility, E-Ink feasibility, low-end performance.
- **ntfy/API:** stream implementation, endpoint correctness, auth, publish gaps, server version/API compatibility, background delivery options.
- **Storage/reliability:** persistence, Room/DataStore usage, lifecycle, dedupe, retries, process death, outbox and diagnostics.
- **QA/security:** manifest/permissions, secrets, admin boundary, command injection/replay risks, dependency audit, test gaps and build reproducibility.

The primary coordinator must reconcile conflicting findings and an independent reviewer must challenge the proposed architecture. Agents must cite exact file paths and line ranges or symbol names for all code claims. Do not let agents edit files during this audit.

## Investigation checklist

### A. Repository and build baseline
- Inventory every file and module, identify generated/untracked files and inspect Git status/history.
- Determine whether `gradlew` and `gradle-wrapper.jar` exist. Verify JDK, Gradle, Android SDK, AGP and Kotlin compatibility on the actual Mint VM.
- Run build, unit tests, lint and dependency report only if the environment supports them. Record exact command, exit code and important output. Never claim a command passed if it was not run.
- Identify release signing assumptions, applicationId, namespace, target/min SDK and build flavors.
- Inspect all dependencies and determine actual references before recommending removal.

### B. Trace current runtime behavior
- Trace application startup through `MainActivity`, theme, navigation, `MainViewModel`, repositories and all screens.
- Identify exactly when network requests start and stop, what triggers reconnect, how topic changes behave and how app background/foreground is handled.
- Trace sample data from provider to UI and determine whether it can appear in a live session.
- Inventory every button/action and mark each as working, local-only, simulated, incomplete or dead.
- Determine what data survives process death and app restart.

### C. ntfy API and server compatibility
- Inspect all HTTP endpoints, methods, headers, query parameters, authentication schemes, response parsing and error mappings.
- Compare with current official docs: `https://docs.ntfy.sh/subscribe/api/`, `https://docs.ntfy.sh/publish/`, `https://docs.ntfy.sh/config/`, `https://docs.ntfy.sh/releases/`.
- Identify the exact ntfy server version on the user's instance only if safe, reachable and authorized. Do not attempt admin writes or publish test messages without approval.
- Separate documented API features from undocumented/internal admin endpoints.
- Identify whether the app can truly publish, manage users/ACL/tokens, send Android notifications, acknowledge commands or provide delivery receipts. Distinguish implemented, feasible, server-dependent and unsupported.
- Document self-hosted Android background delivery choices: persistent foreground stream, WorkManager polling, optional FCM server configuration and UnifiedPush. State the battery/latency trade-offs accurately.

### D. Low-end and E-Ink analysis
- Find expensive assets, fonts, recomposition risks, unbounded lists/sets, timers, polling, image work and heavy dependencies.
- Assess the current app against a 2 GB RAM Android phone and API 24+ constraints.
- Design a true monochrome E-Ink profile for Onyx Boox: no animation, no gradients, no color-only states, static status, manual refresh, portrait/landscape and refresh policy.
- Separate generic Android capabilities from vendor-specific Boox refresh APIs. Do not assume the Boox model or firmware.

### E. Admin and command model
- Produce a capability matrix: available via documented ntfy API, available only via undocumented/internal API, requires server CLI/config, requires separate homelab agent, or impossible from a normal client.
- Design safe command envelopes with fixed action IDs, expiry, replay protection, authorization, explicit confirmation for destructive actions and correlated results.
- Never recommend arbitrary shell execution from message content or storing PVE root credentials in the APK.
- Explain what “end-to-end reporting” can prove: request queued, server accepted, receiver app received, executor completed and human read are different states.

### F. UX and first working model
- Propose an uncluttered navigation model and screen-by-screen responsibilities.
- Define empty/loading/offline/error/permission-denied states.
- Separate demo data from production.
- Define a first working model small enough to build, install and use before advanced administration.

## Required deliverables

Create the following Markdown files at repository root:

1. **`AUDIT_REPORT.md`** — executive summary, evidence-backed critical findings, severity, current feature status, build/test results, security/performance risks and blockers.
2. **`CURRENT_ARCHITECTURE.md`** — actual startup/runtime flow, package map, state ownership, network/data flow, lifecycle and proposed target architecture.
3. **`API_CAPABILITY_MATRIX.md`** — ntfy endpoint/method/auth/response coverage; documented vs undocumented status; app support; test requirements; server-side dependencies.
4. **`PHASE_0_EXECUTION_PLAN.md`** — a detailed ordered Phase 0/Phase 1 task breakdown, file ownership, dependencies, rollback plan and acceptance gates. Do not implement it.

Also provide in your final response:
- What the app genuinely does today vs what is only UI/sample/simulated.
- Top 10 risks ordered by severity.
- Exact first working model scope and deferred scope.
- Low-end device strategy.
- E-Ink strategy for Boox.
- Full ntfy receive/publish/command/reporting architecture.
- Admin capability limits and safe expansion options.
- Build environment blockers and exact commands to fix them.
- Recommended four-agent implementation assignment for Phase 1 onward.
- Any owner decisions needed before implementation.

## Evidence and quality requirements

- Every non-obvious code finding must cite file path and line number/symbol.
- Every external ntfy behavior claim must cite an official ntfy documentation URL and, where relevant, the documented server version.
- Distinguish facts, assumptions, recommendations and unknowns.
- Do not infer that a screen works because it compiles or exists.
- Do not infer administrator access because credentials were accepted.
- Do not claim end-to-end delivery/read receipts without an explicit receiver acknowledgement protocol.
- Do not claim battery savings or low-RAM compatibility without device measurements.
- Do not conceal failed tests or unavailable tools.
- Avoid generic AI-style advice. Give concrete changes, exact files, sequencing, test cases and risks.

## Final stop condition

After writing the audit documents and presenting a concise summary, STOP. Ask the owner to approve the audit and Phase 1 scope. No implementation begins until explicit approval is received.
