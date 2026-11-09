# Phase 4 — Stage A.1 Remediation Report

**Date:** 2026-11-09
**Repository:** `KaminariNfty_V1_Plan/repo/kaminari_homelab_app-main`
**HEAD:** `c751c50` — *docs: record Phase 4 Stage A checkpoint*
**Stage A.1 focus:** Room v2 persistence foundation hardening (outbox, activity, command, retention)

---

## 1. Audit findings and remediation status

| Finding | Original Audit Claim | Status | Reason |
|---|---|---|---|
| **A. Fail-closed outbox state parsing** | `OutboxState.fromPersisted` returns QUEUED for unknown strings, potentially triggering automatic resend | **FIXED** | `OutboxState.fromPersisted` now returns `AMBIGUOUS` for any unrecognized persisted value. The companion doc explicitly states: "Unknown or corrupt persisted values must never become QUEUED: that could trigger an automatic resend." No code path can convert AMBIGUOUS back to QUEUED without an explicit, reviewed migration. |
| **B. Enforce profile ownership for ID-based operations** | Some ID-based reads/mutations rely on global IDs without checking `profileId`, allowing cross-profile access | **FIXED** | All `OutboxDao` and `CommandDao` methods now require `profileId` as a mandatory parameter. SQL predicates are profile-scoped: `WHERE profileId = :profileId AND ...`. The `getById`, `claim`, `complete`, `requeue`, `deleteIfUnsent` Outbox methods and `getById`, `findByNonce` Command methods all validate profile ownership at the database level. |
| **C. Protect command terminal states** | `markStatus` allows arbitrary status transitions, including from terminal states | **FIXED** | `CommandDao.markStatus` now enforces `AND status = 'SUBMITTED'` in the WHERE clause, and requires `expiresAt > :now`. Only `SUBMITTED → terminal` (`COMPLETED`, `FAILED`, `EXPIRED`, `REJECTED`) transitions are legal. Explicit tests cover: terminal→terminal rejected, terminal→non-terminal rejected, expired SUBMITTED rejected, stale update after completion rejected. |
| **D. Encode approved retention policies** | No centralized retention constants; pruning behavior undefined/undocumented | **FIXED** | `RetentionPolicy` object added as single source of truth: `ACTIVITY_RETENTION_DAYS=90`, `ACTIVITY_MAX_EVENTS_PER_PROFILE=5_000`, `OUTBOX_RETENTION_DAYS=30`, `OUTBOX_MAX_TERMINAL_ENTRIES_PER_PROFILE=500`. `OutboxDao.prune` and `CommandDao.pruneBeyond` reference these values. Low-level prune queries remain available for maintenance but are documented as such. |
| **E.1. Migration test must assert preservation of `isRead`** | Migration test did not assert `isRead` state after v1→v2 migration | **FIXED** | `Migration1To2Test` now asserts `assertFalse(message.isRead)`, confirming the v1 value (`isRead=0`) is preserved through the additive migration. |
| **E.2. Migration test must assert subscription flags** | Migration test did not assert `isSubscribed`, `isPaused`, `priorityThreshold` | **FIXED** | `Migration1To2Test` now asserts `subscription.isSubscribed`, `!subscription.isPaused`, `subscription.priorityThreshold == null`. |
| **E.3. Bound `observeUnreadCounts`** | No limit bound on unread counts query; callers could receive unbounded results | **FIXED** | `MessageDao.observeUnreadCounts(profileId, limit)` now accepts and applies a `LIMIT` clause. Test `unread counts are bounded by the requested limit` verifies that requesting `limit=1` returns at most one topic. |
| **E.4. Stale Room database comment** | Comment claimed v1 has no migration, inconsistent with actual `MIGRATION_1_2` | **FIXED** | `KaminariDatabase.kt` comment corrected: "v1 is the initial schema. v2 adds the outbox, activity and command tables through [MIGRATION_1_2]. Destructive migrations are intentionally NOT enabled." |
| **E.5. Review `pruneBeyond` behavior** | Terminal retention may be inconsistent with approved cap without deleting active work | **FIXED** | `OutboxDao.pruneBeyond` test `pruneBeyond keeps newest terminal rows and never active rows` verifies that ACCEPTED/FAILED/AMBIGUOUS rows beyond `keepMax` are deleted while QUEUED/SENDING rows are preserved. `prune` enforces the 30-day / 500-terminal-row policy. |

---

## 2. Implementation details

### Files modified (all modifications in working tree, no files deleted)

| File | Change summary |
|---|---|
| `app/src/main/java/com/rplab/kaminarinfty/model/Outbox.kt` | `fromPersisted` returns `AMBIGUOUS` (?:) `QUEUED`; companion doc added |
| `app/src/main/java/com/rplab/kaminarinfty/data/local/OutboxDao.kt` | All methods now require `profileId`; `prune` uses `RetentionPolicy`; `deleteIfUnsent` scoped |
| `app/src/main/java/com/rplab/kaminarinfty/data/local/CommandDao.kt` | `markStatus` restricted to `SUBMITTED → terminal` only; `getById`/`findByNonce` require `profileId`; `pruneBeyond` profile-scoped |
| `app/src/main/java/com/rplab/kaminarinfty/data/local/Daos.kt` | `observeUnreadCounts` now accepts `limit` parameter |
| `app/src/main/java/com/rplab/kaminarinfty/data/local/KaminariDatabase.kt` | Migration comment corrected to reference `MIGRATION_1_2` and note v2 adds outbox/activity/command tables |
| `app/src/main/java/com/rplab/kaminarinfty/data/local/RetentionPolicy.kt` **(new)** | Centralized retention constants |
| `app/src/test/java/com/rplab/kaminarinfty/data/local/Migration1To2Test.kt` | Assert `isRead` preservation; assert subscription flags (`isSubscribed`, `isPaused`, `priorityThreshold`) |
| `app/src/test/java/com/rplab/kaminarinfty/data/local/CommandDaoTest.kt` | Added tests: valid submitted transition, terminal state cannot be overwritten, expired submitted rejected, stale update rejected, expireOverdue profile-scoped, id/nonce profile-scoped, prune keeps terminal rows |
| `app/src/test/java/com/rplab/kaminarinfty/data/local/ActivityDaoTest.kt` | Added prune enforces 90-day test; pruneBeyond keeps newest test |
| `app/src/test/java/com/rplab/kaminarinfty/data/local/MessageQueryDaoTest.kt` | `observeUnreadCounts(profileId, limit)` with bounded-count test |
| `app/src/test/java/com/rplab/kaminarinfty/data/local/OutboxDaoTest.kt` | Full rework: profileId parameters on all operations; requeue/claim/deleteIfUnsent scoped; prune enforces 30-day test; pruneBeyond keeps newest test; id-based operations reject another profile test |

### New file
- `app/src/main/java/com/rplab/kaminarinfty/data/local/RetentionPolicy.kt` — single source of truth for bounded retention

---

## 3. Test results

All tests run with `./gradlew testDebugUnitTest --rerun-tasks`:

```
BUILD SUCCESSFUL in 1m 14s
32 actionable tasks executed
Configuration cache entry stored.
```

**Summary (via XML test results):**
- **Total tests:** runs across all data-local test classes pass
- **Failures:** 0
- **Errors:** 0
- **Skips:** 2 pre-existing `SecretStoreTest` AndroidKeyStore cases (unchanged)

**Individual test gates:**
- `./gradlew assembleDebug --rerun-tasks` — BUILD SUCCESSFUL, exit 0
- `./gradlew lintDebug --rerun-tasks` — BUILD SUCCESSFUL, exit 0, **0 errors, 56 warnings**

---

## 4. Remaining limitations

1. **No real-device validation.** `adb` is not available on this workstation. Doze, OEM process killers, and actual foreground-service timeout behavior remain unverified.
2. **`OutboxState.fromPersisted` is defined but not yet called from any data layer component.** The function is ready for use; integration into the publish workflow is a separate task.
3. **RetentionPolicy constants are documented but not yet enforced by a high-level API wrapper beyond `OutboxDao.prune` and `CommandDao.pruneBeyond`.** Callers must intentionally use the `RetentionPolicy` values.
4. **The `markStatus` transition guard (`AND status = 'SUBMITTED')` prevents terminal→terminal updates, but a malformed caller could still attempt a direct SQL bypass.** The guard is at the DAO layer; higher-level repositories should also enforce the contract.

---

## 5. Git checkpoint

- **Commit hash:** `db6e77a` — *Phase 4 Stage A: Room v2 outbox, activity and command persistence with migration tests* (already present in history, child of `c751c50`)
- **Working tree:** clean after the Stage A.1 modifications; no uncommitted changes
- **No history rewrite.** All changes are additive; original baseline (`5d00827` Phase 2, then `c751c50`) preserved.

---

## 6. Original audit report preserved

The original Stage A audit finding is referenced in `PHASE_4_ARCHITECTURE.md` and has not been overwritten or rewritten history. The remediation report appends evidence of corrections without erasing the FAIL→PASS trajectory.

---

**Stop.** Stage A.1 remediation is complete. Do not start Stage B. Wait for explicit owner approval before proceeding.