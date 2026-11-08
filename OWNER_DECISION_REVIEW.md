# OWNER_DECISION_REVIEW.md — decision register for Phase 0/1 authorization

**Date:** 2026-11-08 · **Mode:** READ-ONLY (this session modified nothing but this document; no installs, no builds, no app-code edits, no Phase 1 work)
**Sources reproduced verbatim:** `AUDIT_REPORT.md` §8, `PHASE_0_EXECUTION_PLAN.md` §2/T1.7, `PLAN.md`, `AGENTS.md`, `CURRENT_ARCHITECTURE.md`, `API_CAPABILITY_MATRIX.md`.

---

## 1. Exact audited decision texts (verbatim, no paraphrase)

From **AUDIT_REPORT.md §8** ("Decisions the owner must make before Phase 1", table rows copied whole):

```
| D1 | Approve toolchain install commands of §2 | **yes** / no (no build ⇒ no Phase 1) |
| D2 | `applicationId` keep `com.aistudio.kaminarinfty.rzxqpt` or rebrand | **rebrand later, keep for Phase 1** (changing once installed loses state) |
| D3 | Server version check permission: run `ntfy version` via SSH on nfty.rplab.qzz.io, or approved `curl https://nfty.rplab.qzz.io/v1/health` from the VM | **SSH CLI (safe, read-only)** |
| D4 | git init at repo root (needed for change discipline per AGENTS §12) | **yes** |
| D5 | Accept Phase 1 will remove/disable sample-mode-as-default UI (demo data goes behind an explicit toggle) | **yes** |
| D6 | Defer vendor-Boox tuning (per locked decision) and defer publish/admin phases until core receive/persist/notify done | **yes** |
```

(§2 of AUDIT_REPORT = the exact command block B-1..B-4: `apt install openjdk-21-jdk-headless`; SDK cmdline-tools to `~/opt/android-sdk`; `gradle wrapper --gradle-version 9.3.1`; `keytool` debug keystore. Nothing from it has been executed.)

From **PHASE_0_EXECUTION_PLAN.md**, task T1.7 (full verbatim row):

```
| **T1.7 Retry/throttle corrections** | C | **Deviation request R-1 to owner:** these bugs corrupt Phase-1 process-death
tests, so they land early: 401/403 stop-flap + honor fatal errors (`NftyRepository.kt:89-118`), separate
health/stream timeouts (`NftyStreamClient.kt:39,64`), Setup keystroke debounce (one restart per save, not
per char) | `NftyRepository.kt`, `NftyStreamClient.kt`, `SetupScreen.kt`, `MainViewModel.kt` | T0.8 |
```

---

## 2. Decision-by-decision analysis

### D1 — Build toolchain availability
- **Controls:** whether the audit's four blocker fixes may run (JDK 21, Android SDK, Gradle wrapper restoration, debug keystore).
- **Why it matters:** the VM today has no `java`, no Gradle, no SDK, and the ZIP ships no `gradlew`/`gradle-wrapper.jar`/`debug.keystore`. Every later phase ("no build ⇒ no Phase 1" per audit) depends on it; the final deliverable `dist/KaminariNfty-v1.0.0.apk` cannot exist without it.
- **Choices:** (a) approve as written (apt JDK + user-local SDK); (b) approve but fully user-space (JDK tarball, no sudo needed); (c) reject.
- **Recommended choice:** *(a)* — you have now supplied sudo access; commands are exactly B-1..B-4 in AUDIT_REPORT §2, each individually visible in the transcript before execution.
- **Consequence if wrong:** (c) leaves the goal permanently blocked; (b) works but is slower and adds manual PATH maintenance.
- **Impacts:** compatibility (toolchain versions pinned: Gradle 9.3.1 / AGP 9.1.1 / Kotlin 2.2.10), architecture (none), performance (none), security (neutral — public artifacts from official CDNs; wrapper jar checksum recorded in `PHASE_0_REPORT.md`).

### D2 — Application identity (package + applicationId) — **revised recommendation**
- **Controls:** the app's installed identity: Kotlin `namespace` (currently `com.example`, `app/build.gradle.kts:12`) and `applicationId` (currently `com.aistudio.kaminarinfty.rzxqpt`, `:16`).
- **Why it matters:** `PLAN.md §13.1` (verbatim): *"**App identity:** confirm final package/application ID before signing and distributing. Changing it later creates a separate app install."* The original audit recommendation ("rebrand later") predates the V1 release goal; the correct moment is **now**, before the first-ever install.
- **Choices:** (a) `com.rplab.kaminarinfty` — matches product name "Kaminari Nfty" and your `rplab` infra; (b) `com.rplab.kaminarinity` — PLAN.md's literal suggestion (line 129: `com.rplab.kaminarinity/`, ambiguous fused word); (c) keep the AI Studio ID.
- **Recommended choice:** *(a)* `com.rplab.kaminarinfty`, applied in Phase 0 (right after toolchain, before baseline build). Revision vs. audit: justified by PLAN §13.1 and the release-APK goal.
- **Consequence if wrong:** (c) ships a signed release under an AI-Studio-generated ID (wrong forever after publication); renaming *after* you install = brand-new app, local history gone. Both (a)/(b) are safe — the choice simply becomes permanent.
- **Impacts:** architecture (mechanical 34-file `com.example.*` → `com.rplab.kaminarinfty.*` rename, verified by compile), compatibility (none), security (none), performance (none).

### D3 — Server version probe permission
- **Controls:** how we learn the exact ntfy version/API surface of `nfty.rplab.qzz.io` before enabling version-specific behavior (AGENTS §6; API_CAPABILITY_MATRIX §7).
- **Why it matters:** documented behaviors and gates differ by release (e.g., internal admin API ~v2.36, subscriber rate limiting, same-second dedupe note of v2.31). Guessing risks enabling the wrong behavior against your server.
- **Choices:** (a) owner runs `ntfy version` over SSH (authoritative, least exposure); (b) additionally `curl https://nfty.rplab.qzz.io/v1/health` from this VM (documented open endpoint, no credentials, no write; verify TLS reachability); (c) skip.
- **Recommended choice:** *(a)+(b)* — SSH command executed **by you** (I never need your SSH credential; it is not requested), curl by the toolchain after D1.
- **Consequence if wrong:** with no version check, admin/feature gating is unverified → runtime failures or claims AGENTS §14 forbids.
- **Impacts:** compatibility (feature gating), security (positive: SSH stays with you; nothing stored client-side), architecture (version-gate flags), performance (none).

### D4 — Git repository initialization
- **Controls:** `git init` + baseline commit + `audit-baseline` tag approved by T0.3.
- **Why it matters:** AGENTS §12 change discipline (small phase-scoped commits, revert ability, audit trail) is impossible otherwise; the ZIP stays as a pristine fallback either way.
- **Choices:** yes / no.
- **Recommended choice:** yes.
- **Consequence if wrong:** no rollback, no diff review, no trustworthy phase reports.
- **Impacts:** security (neutral — audit confirmed no secrets in tree; `.env` not committed), architecture (none), compatibility (none).

### D5 — Sample-data demotion
- **Controls:** the audit-critical fix that demo content (8 fake alerts / 5 fake topics, auto-subscribed to your real server) is never a default view (AUDIT_REPORT risk 2; `MainViewModel.kt:81-91,134-148`).
- **Why it matters:** `PLAN.md` line 108 (verbatim): *"Demo data must be opt-in, visibly marked `DEMO`, isolated from production data, and easy to clear. Do not load demo content by default."* — the repo violates this today, and acceptance test L445 ("Fresh install shows no fake messages and no unexpected subscription") fails as shipped.
- **Choices:** yes (Phase 1 lands it) / no.
- **Recommended choice:** yes.
- **Consequence if wrong:** fabricated alerts on your real homelab topics; false sense of function; acceptance gates unreachable.
- **Impacts:** security/privacy (fake topics on real server), architecture (clean demo::production separation), compatibility (none), performance (minor positive: no seeding work).

### D6 — Sequencing and deferrals
- **Controls:** confirmation of the PLAN.md §9 ladder: publish correctness (Phase 2) → delivery modes (Phase 3) → command centre (Phase 4) → E-Ink (Phase 5) → admin/hardening (Phase 6) → RC (Phase 7); E-Ink stays model-agnostic until your Boox arrives; admin adapted only to *verified* server capability.
- **Why it matters:** prevents well-intentioned reordering (e.g., admin UI before persistence exists; Boox-specific APIs on unknown firmware).
- **Choices:** confirm ladder / propose a reorder / reject.
- **Recommended choice:** confirm as-is — the goal's own 9-step order matches this ladder.
- **Consequence if wrong:** any reorder multiplies rework and risks invented capabilities (AGENTS §14 stop conditions).
- **Impacts:** architecture (foundation-first), compatibility (vendor-defer), security (admin bound to verified capability), performance (profiling checkpoints preserved).

### R-1 — Retry/throttle corrections pulled into Phase 1
- **Controls:** timing of three small, well-understood fixes (T1.7 whitelist above) that PLAN.md otherwise schedules inside Phase 2 ("correct event parser, cancellation, reconnect").
- **Why it matters:** Phase 1's acceptance tests are process-death/migration tests; with the current bugs, 401/403 flap the stream state forever, health probes (`readTimeout(0)`, `NiftyStreamClient.kt:39`) can hang indefinitely during test runs, and the Setup field fires a full-12h replay per keystroke — each test runs against deliberate instability.
- **Choices:** approve pull-in (Phase 1) / keep in Phase 2.
- **Recommended choice:** approve pull-in.
- **Consequence if wrong:** kept-in-Phase-2 = flaky, unfair Phase 1 test baseline and possible hanging test runs; pulled-in = +~3 files touched slightly before the "scheduler" says so (only cost).
- **Impacts:** performance (bounded retries/timeouts), security (no endless 401 retry loop), compatibility (health semantics correct), architecture (error taxonomy fixed at the start).

---

## 3. Locked-requirements cross-check (your message vs. audited plan)

| Locked requirement | Where the audited plan honors it | Delta / honesty note |
|---|---|---|
| Delivery: Instant mode | Phase 3 foreground service + persistent stream (`PLAN.md` L167/L499) | none |
| Delivery: Battery Saver | Phase 3 WorkManager `poll=1&since=<lastId>` catch-up (L168) | latency is **minutes**, never framed as instant (L171) |
| User-selectable modes | Settings delivery mode, explained trade-offs (L171) | none |
| Reliable reconnect + catch-up | Phase 2 parser/backoff/dedupe + `since=` cursor; audit risks 5 & 7 fixed | R-1 approved makes reconnect honestly testable |
| Honest delivery status | queued → sent → server-accepted (+ ack only if receiver protocol exists) — `PLAN.md` L247, L276; API_CAPABILITY_MATRIX §6 | local read ≠ server read receipt is preserved end-to-end |
| Complete supported ntfy administration | Phase 6 admin UI per verified capability; undocumented/internal HTTP APIs = version-gated experimental adapter with fallback (AGENTS §6, PLAN L282-298, API matrix §4) | "complete" is bounded by your locked rule *and* AGENTS: undocumented endpoints are never load-bearing and clearly labeled |
| Separate trusted PVE gateway; allowlist; no shell from notifications | `PLAN.md` §6.4 envelope + allowlist + executor-side checks; AGENTS §7; no PVE creds in APK | first commands: `service.status`, `host.health`, `storage.summary`, `backup.latest_status`, `container.status` (L280) |
| Complete Standard profile + complete E-Ink profile | Phases "Standard UI" then 5 (tokens, monochrome, static states, manual refresh); L173-200 | hardware acceptance (L200, L470) splits: software gates = emulator-verifiable now; **Boox hardware gates deferred until the device arrives** (locked decision) |
| Low-RAM compatibility, bounded memory | PLAN §4 rules (L149-163) + audit risk 7-10 fixes; bounded dedupe, paged history, one-line-at-a-time parser | real-device measurements still required before any performance claim (AGENTS §11; metrics §12) |
| Efficient pagination | Room-backed paging/bounded queries (L154), 500-message scroll jank gate (L485) | Paging3 dep is NOT added unless justified (AGENTS §5); bounded `LIMIT/OFFSET` flows first |
| Minimal background activity | stream closed when app not in Instant mode; poll interval only in Battery Saver (L160) | none |

## 4. PLAN.md ↔ repository: conflicts and recommended corrections (nothing changed silently)

| # | Conflict (PLAN.md claim vs. audited repo fact) | Evidence | Recommended correction (needs your approval) |
|---|---|---|---|
| C1 | PLAN.md §3 suggests package root `com.rplab.kaminarinity/` (L129); repo has `namespace com.example` + `applicationId com.aistudio.kaminarinfty.rzxqpt` (`build.gradle.kts:12,16`) | AUDIT risk 9 | Decide via **D2′** above; rename namespace+applicationId in Phase 0, before first install (PLAN §13.1). Edit PLAN.md only if you reject its suggested ID. |
| C2 | PLAN.md §2.1 defines five bottom destinations (Inbox/Topics/Compose/Activity/Settings-Admin; Detail is drill-in, L95); repo ships 7 screens incl. Saved, Search, manual Setup | screen map | No PLAN edit needed — execution maps Search+Saved into the Inbox destination, Detail stays drill-in, Setup becomes the profile editor inside Settings. Recorded in CURRENT_ARCHITECTURE §10. |
| C3 | PLAN.md L234 event list (`open`, `keepalive`, `message`, `poll_request`, unknown) omits the two stream events added in current docs: `message_delete`, `message_clear` (stable server v2.37.2, 2026-11-03) | API matrix §2 | Amend PLAN.md L234 with those two types (one-line edit, approval requested); Phase 2 parser handles both regardless. |
| C4 | PLAN.md L145 forbids shipping *"Firebase AI, camera, location, or unrelated libraries to a notification client"*; repo ships Firebase BOM 34.17, `firebase-ai`, AppCheck ×2, hollow `google-services.json`, AI-Studio leftovers (`metadata.json`, `.env.example`) | AUDIT risk 9 | Phase 1 dependency-prune task (proposed **A-1** amendment to PHASE_0_EXECUTION_PLAN §2: add explicit "remove unused Firebase/Retrofit/Moshi/plugins + AI-Studio artifacts" row). |
| C5 | PLAN.md L169/L499: FCM = optional advanced spike; goal requires no FCM; server needs `firebase-key-file` + custom APK build | API matrix §5 | Keep optional & non-blocking (aligned); decide only in Phase 6 if you want it. No edit. |
| C6 | PLAN.md E-Ink acceptance (L200) and checklist l.470 demand actual-Boox screenshots; the device has not arrived (locked decision) | L502 | Split the gate: software-level E-Ink gates verifiable in Phase 5 without hardware; hardware screenshots/ghosting tests become an owner-run acceptance item on arrival. Minor PLAN.md wording edit optional. |
| C7 | PLAN.md Phase 0 says "Restore Gradle wrapper" (L336); repo has no `gradlew`/`gradle-wrapper.jar` at all, plus missing JDK/SDK/keystore (audit §2) | audit §2 | Already handled: T0.1–T0.4 + D1. Risk of AGP 9.1.1 × Compose BOM 2024.09.00 mismatch resolved at T0.5/T0.6 (single-line BOM bump fallback). |
| C8 | PLAN.md L208 asks health to check *"both HTTP status and `healthy` JSON field"*; repo reads status only (`NiftyStreamClient.kt:66-72`) | audit §4 | Phase 2 corrects the probe; no PLAN change. |
| C9 | Repo UI shows fake telemetry ("Room SQLite" `InboxScreen.kt:146`, "38.4 ms" `:133`, retention "30 Days" `SettingsScreen.kt:311-321`, fake TLS card `:369-377`) vs. PLAN L95 anti-clutter + L445 honesty | audit §4 | Remove/re-wire in T1.6 (already in PLAN digest); consistent, no edit. |
| C10 | Room table naming: PLAN §7 `TopicSubscriptionEntity` (L307) vs execution plan T1.2 coining `TopicEntity` | §126 L34 | Adopt PLAN's verbatim name `TopicSubscriptionEntity` — no extra decision needed (aligning to the spec is correct execution, recorded as a ruling). |

## 5. Decision register — recommended defaults and the exact approval requested

| ID | Decision | Recommended default | Exact approval I need from you |
|---|---|---|---|
| D1 | toolchain install (B-1..B-4) | **Approve** | "Approve D1" |
| D2′ | app identity — **revised** from audit | **`com.rplab.kaminarinfty`, namespace + applicationId, renamed in Phase 0** | "Approve D2 = com.rplab.kaminarinfty" (or name the string you want, e.g. `com.rplab.kaminarinity`) |
| D3 | server probe | **Owner SSH `ntfy version` + VM `curl /v1/health`** | "Approve D3" |
| D4 | git init | **Approve** | "Approve D4" |
| D5 | sample data demotion | **Approve** | "Approve D5" |
| D6 | phase ladder + deferrals | **Approve as planned** | "Approve D6" |
| R-1 | retry fixes in Phase 1 | **Approve pull-in** | "Approve R-1" |
| C3 | PLAN.md L234 + two event types | **Amend** | "Approve C3" |
| C4 | dependency prune (A-1 amendment) | **Approve** | "Approve C4" |

**One-line approval that covers all of the above:** `Approved: D1-D6, R-1, C3, C4, applicationId com.rplab.kaminarinfty — start Phase 0.`
Any single override (e.g. a different app ID, or "no git init") can be given piecewise; anything not overridden takes the recommended default.

**Note on the sudo password** you sent in the previous message: received in chat only — it has **not** been written to any file, note, memory or script, and will not be echoed back. It will be used interactively for D1's `apt install` step after your approval. Recommendation (one line): rotate it after the build environment is set up, since it has now appeared in a chat context.

---

*Per your instruction and AGENTS §2 I now STOP. Nothing has been installed, built, or written beyond this review document; app code remains untouched; Phase 1 has not started.*
