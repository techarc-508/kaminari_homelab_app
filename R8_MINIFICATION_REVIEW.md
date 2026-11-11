# R8 / ProGuard Minification Review

**Stage:** Phase 8, Stage 5.
**Date:** 2026-11-10.

## Summary

The release build currently does not run R8 or ProGuard because
`isMinifyEnabled = false`. The project has a `proguard-rules.pro` file, but it is a
minimal template and is not sufficient as-is for a minified release build.

## Current state

- `app/build.gradle.kts`:
  - release `isMinifyEnabled = false`
  - release `proguardFiles` references:
    - `proguard-android-optimize.txt`
    - `proguard-rules.pro`
- `app/proguard-rules.pro` is a generic template with commented examples only.

Because minification is disabled, the referenced ProGuard files are inactive. The
release APK is therefore not shrinked, optimized, or obfuscated in this stage.

## Review findings

- A minified release build cannot be validated today because it is disabled.
- If minification is enabled later, substantial keep rules will be needed for:
  - Compose runtime and UI reflection-heavy paths
  - Room entities, DAOs, and KSP-generated code
  - DataStore and preference classes
  - Kotlin metadata and serialization usage if present
  - Any retained theme or resource references required at runtime
- A minified build should be validated with a full release test pass before publication.

## Recommendation

- Do not enable release minification in this stage without a focused validation plan.
- If the owner wants a minified release, enable it on a branch, run a release test pass,
  and confirm no runtime or Compose regressions appear.
- Keep the existing `proguard-rules.pro` as a starting point, but enhance it only after
  enabling minification and observing what is actually stripped.

## Status

- Minification: not enabled.
- R8/ProGuard validation: not performed in this stage.
- Decision: keep current non-minified release config and document the gap.

