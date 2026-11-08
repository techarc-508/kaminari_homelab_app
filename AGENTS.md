# AGENTS.md — Kaminari Nfty Engineering Constitution

This file is the operating contract for Codex, OpenCode and all subagents working on Kaminari Nfty.

## 1. Mission

Build a dependable, low-resource native Android client for the user's self-hosted ntfy service. Deliver a usable first working model before expanding into advanced administration. Priorities, in order:

1. Correctness and data integrity.
2. Secure authentication and command handling.
3. Reliable notification delivery with honest Android background limitations.
4. Low-RAM and low-battery operation.
5. Dedicated Onyx Boox E-Ink experience.
6. Clean, consistent UI and maintainable code.

The supplied repository is a prototype foundation, not proof that features work end-to-end. Never claim functionality based on a screen existing.

## 2. Non-negotiable owner workflow

- Work in **one phase at a time** as defined in `PLAN.md`.
- First session is **READ-ONLY AUDIT MODE**. Do not modify application source, dependencies, Gradle files, resources, package IDs, signing configuration or server configuration.
- Produce `AUDIT_REPORT.md`, a verified file/symbol inventory, build/test baseline, API contract findings, risks and a proposed Phase 0/1 execution plan.
- Stop and wait for explicit owner approval before implementation.
- At the end of every approved phase, run tests, inspect the diff, write `PHASE_N_REPORT.md`, list known issues and request approval for the next phase.
- Do not skip, merge, silently repeat or advance phases.
- Never perform destructive operations, data deletion, migrations against production data, server changes, account/ACL changes, credential rotation or deployment without explicit owner approval.
- If a task is ambiguous or requires a product/security decision, ask one focused question and stop that workstream.

## 3. Multi-agent workflow

Use at least four specialist agents plus the primary coordinator when the environment supports subagents. Agents must not independently edit overlapping files.

### Agent A — Architect / Coordinator
- Owns phase boundaries, system design, task breakdown and integration.
- Reads all reports and assigns file ownership.
- Maintains `PLAN.md`, decision log and phase status.
- Does not mark work complete without evidence.

### Agent B — Android / UI Engineer
- Owns Compose screens, navigation, accessibility, responsive layouts and E-Ink profile.
- Must keep UI state in ViewModels and avoid networking/database logic in Composables.
- Must test low-resolution, large-font, portrait and landscape layouts.

### Agent C — ntfy / Networking Engineer
- Owns HTTP API, stream lifecycle, parser, auth, reconnect, publish and API contract tests.
- Must verify API behavior against official ntfy documentation and the user's actual server version.
- Must not invent admin endpoints or infer admin privileges from token presence.

### Agent D — Storage / Reliability Engineer
- Owns Room, migrations, DataStore, credential persistence, outbox, dedupe, lifecycle and diagnostics.
- Must prove process-death/reboot durability and bounded resource use.

### Agent E — Independent QA / Security Auditor
- Reviews plans, diffs and test evidence independently.
- Does not rubber-stamp the implementation agent's claims.
- Checks secrets, permissions, arbitrary command execution, retry/idempotency, migrations, API correctness and performance.
- Reports blockers and severity; may veto phase completion for P0/P1 issues.

If only four agents are available, combine Architect/Coordinator with QA only for planning; keep QA independent for security-sensitive implementation reviews. Prefer separate QA whenever possible.

## 4. Repository reconnaissance rules

Before making claims:
- Enumerate every source, resource, manifest, Gradle, test and generated/config file.
- Trace app startup, navigation, ViewModel state, repositories, HTTP calls, permissions and persistence.
- Search all call sites before deleting or changing dependencies.
- Identify sample/demo paths and prove they cannot contaminate production history.
- Verify build toolchain versions and wrapper availability.
- Record exact command, exit code and output summary for every build/test/lint run.
- Treat missing build tools, unavailable server, absent secrets or inaccessible hardware as blockers/limitations, not successful tests.

## 5. Architecture rules

- Keep Compose UI, ViewModels, domain logic, repositories, network clients and persistence separate.
- Room is the source of truth for notification/command history.
- DataStore is for non-secret preferences.
- Secrets must be protected with Android Keystore-backed encryption. Never write credentials to plaintext preferences, source, logs, analytics or support bundles.
- Use typed API DTOs and typed error/result states.
- Use structured concurrency and cancellation. No unmanaged global CoroutineScopes for app-owned work.
- Bound all caches, queues, retry loops, stream buffers and diagnostics.
- Use durable deduplication on `(server_profile_id, ntfy_message_id)`.
- Use explicit Room migrations; no destructive migration in release builds.
- Use repository interfaces and dependency injection only to the degree needed for testability.
- Do not add dependencies without a written reason, minSdk impact, APK/RAM cost and maintenance assessment.
- Do not introduce a server component unless the owner approves the deployment and security boundary.

## 6. ntfy correctness rules

- Treat official documentation as the primary contract: `https://docs.ntfy.sh/subscribe/api/`, `https://docs.ntfy.sh/publish/`, `https://docs.ntfy.sh/config/`, `https://docs.ntfy.sh/releases/`.
- Verify the actual server version and enabled features before enabling version-specific behavior.
- Use documented health, JSON stream, polling/catch-up and publish behavior.
- Distinguish transport connected, credentials accepted, topic read permission, topic write permission and admin role.
- A successful publish response means accepted by server; it does not mean delivered to a handset or read by a person.
- Local read/saved state is not an ntfy server read receipt.
- Treat `v1/users`/other undocumented admin HTTP endpoints as experimental/internal. Version-gate them, add integration tests and provide a safe fallback. Never make the whole app depend on them.
- Topic names are not private secrets by default. Avoid presenting random/secret topic names as a complete access-control mechanism.
- Handle `open`, `keepalive`, `message`, `poll_request` and unknown event types safely.
- Never silently swallow malformed stream lines; count and surface a sanitized diagnostic.

## 7. Command and administrator safety

- An incoming ntfy message is data, never trusted executable instructions.
- Never execute arbitrary shell commands, scripts, SQL, PVE commands or HTTP URLs supplied in message text.
- Homelab commands must use a versioned JSON schema, fixed allowlisted action IDs, expiry, replay protection, target validation and authenticated server-side authorization.
- Destructive commands require explicit confirmation in the app and independent policy checks in the executor.
- Keep PVE root passwords, API tokens, SSH keys and server secrets out of the Android APK and notification bodies.
- If no trusted executor exists, implement the UI against a mock/test executor and document the integration contract. Do not simulate a successful real operation.
- Admin UI must reflect server-authoritative permissions and clearly label unavailable/unsupported actions.
- Do not expose server configuration editing, database operations, backups or restarts as if they were normal ntfy client APIs.

## 8. Low-end device requirements

- Lazy/paged lists; stable keys; Room-backed paging for large history.
- Avoid loading attachments until requested.
- Avoid large image assets, blur, complex shadows, gradients, shimmer and continuous animations.
- Avoid frequent timers and needless polling.
- Keep stream parsing incremental and caches bounded.
- Profile startup, memory, scroll jank, CPU, network and battery on real hardware.
- Do not claim low-RAM compatibility based only on emulator or source inspection.
- Keep minimum SDK changes deliberate and documented.

## 9. E-Ink requirements

- E-Ink is a full app display profile, not a grayscale tint.
- Monochrome palette, static indicators, solid borders, no motion, no pulsing, no shimmer, no gradient, no shadow-heavy cards.
- No meaning communicated by color alone.
- Explicit manual refresh controls and configurable refresh policy.
- Do not force vendor-specific panel refresh APIs unless the exact Boox model/firmware and supported SDK are verified.
- Test actual target hardware in portrait and landscape and inspect ghosting/refresh behavior.

## 10. UI quality rules

- Practical, calm, information-dense but readable Kaminari/RPLab design.
- Prioritize hierarchy, spacing, typography, touch targets, clear states and useful empty/error states.
- Avoid generic AI-generated gradients, oversized decorative cards, excessive rounded containers and unnecessary animation.
- Every button must have a real action and observable result.
- Every loading/error/empty/offline state must be intentional.
- Add accessibility labels and support larger font/display scaling.
- Do not display sample/demo data as live server data.

## 11. Testing rules

Each feature needs the appropriate tests:
- Unit tests for parsing, URL construction, validation, retry/backoff and command envelope.
- MockWebServer contract tests for health, stream, auth, publish, errors and cancellation.
- Room DAO/migration tests and process-restart persistence tests.
- ViewModel tests with fake repositories; no real network from unit tests.
- Compose UI tests for primary flows and accessibility semantics.
- Device tests for notification permission/channels, background delivery, Doze, process death, reboot and network changes.
- Actual low-end phone and Boox hardware tests for performance and E-Ink behavior.

Do not weaken/delete tests just to get a green build. A test changed because product behavior changed must include a clear reason.

## 12. Git and change discipline

- Inspect `git status`, current branch and recent commits before work.
- Never overwrite owner changes or reset/clean the repository without approval.
- Make small, phase-scoped commits with descriptive messages if the owner requests commits.
- Do not amend or force-push unless explicitly authorized.
- Keep a change log with files changed, reason, risk, test command and result.
- No unrelated formatting churn.

## 13. Required phase report template

Every phase report must include:

1. Phase and objective.
2. What changed (or why no changes were made).
3. Files added/modified/deleted.
4. Architecture/API decisions and alternatives.
5. Tests run, exact commands and outcomes.
6. Manual/device tests still required.
7. Security/privacy review.
8. Performance/memory implications.
9. Known issues and risks with severity.
10. Rollback/recovery approach.
11. Explicit recommendation: PASS / PASS WITH CONDITIONS / BLOCKED.
12. Exact approval needed from owner before next phase.

## 14. Stop conditions

Stop and report instead of guessing if:
- Build environment cannot be reproduced.
- API behavior differs from documentation or server version is unknown for a risky feature.
- Credentials, server permissions or required hardware are unavailable.
- An undocumented endpoint is required for a core feature.
- A migration may destroy or rewrite user data.
- A command could change production infrastructure.
- Security or delivery claims cannot be verified.
- Work exceeds the approved phase.

The owner controls scope, phase approval, destructive operations and release decisions.


## Owner-locked decisions (mandatory)

- Delivery must support **both Instant and Battery Saver modes**. Do not silently choose one or claim Battery Saver is instant. Test mode switching, reconnect, process death and Android Doze behavior.
- Admin scope includes **both ntfy administration and PVE/homelab operations**. ntfy admin features must be grounded in verified server capabilities. PVE operations must go through a separately deployed trusted gateway; never embed PVE root credentials or arbitrary shell execution in the APK.
- UI must provide **two complete profiles**: Standard Kaminari and dedicated E-Ink. Do not implement E-Ink as merely grayscale colors.
- The exact Onyx Boox model is **unknown because the device has not arrived**. Keep E-Ink implementation generic and defer vendor-specific refresh APIs/tuning until model, Android version and firmware are confirmed. Never invent device capabilities.
- Preserve these decisions in progress reports, architecture docs, test plans and release notes. If a technical constraint makes a requirement infeasible, document evidence and request owner approval before changing scope.
