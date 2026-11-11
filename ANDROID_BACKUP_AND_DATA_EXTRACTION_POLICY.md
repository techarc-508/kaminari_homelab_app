# Android Backup and Data Extraction Policy Review

**Stage:** Phase 8, Stage 5.
**Date:** 2026-11-10.

## Summary

The manifest sets `android:allowBackup="false"`, which is correct for an app that may
store secrets locally. Two XML resource files exist under
`app/src/main/res/xml/`, but they are currently placeholder/sample files rather than
actively finalized policy files.

## Current state

- `AndroidManifest.xml`:
  - `android:allowBackup="false"`
- `app/src/main/res/xml/backup_rules.xml`: placeholder sample file with only commented-out
  sample include/exclude rules; no active rules are present.
- `app/src/main/res/xml/data_extraction_rules.xml`: placeholder sample file; no active
  rules are present.

No active `<include>` or `<exclude>` rules are present in either file.

## Review findings

- `allowBackup="false"` blocks the default full-backup behavior for this app.
- Placeholder backup/data-extraction XML files are harmless now because there is little
  or no persisted secret data in the current prototype, but they should be finalized
  before production if any secrets are stored or if future storage increases.
- The app stores credentials via `KeystoreSecretStore` at runtime. That storage is
  SharedPreferences-backed ciphertext protected by the Android Keystore.
- The final policy should explicitly decide whether any non-secret data may be restored
  and whether the secret store should be excluded under any allowed backup path.

## Recommended policy stance

- Keep `android:allowBackup="false"` for this app's security posture.
- If any future backup path is allowed, explicitly exclude secret storage and any
  credential-bearing preferences.
- Finalize `backup_rules.xml` and `data_extraction_rules.xml` only when the app's
  persisted data model justifies them, and never leave them as inactive templates in a
  published release without review.

## Status

- Backup disabled via manifest.
- Backup/data-extraction XML files present but placeholder.
- Finalization deferred to owner review before publication.

