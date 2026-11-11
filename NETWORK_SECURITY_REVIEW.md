# Network Security Review

**Stage:** Phase 8, Stage 5.
**Date:** 2026-11-10.

## Summary

The app uses HTTPS in its network clients, but the manifest does not currently declare
a `networkSecurityConfig`. This stage reviews the current posture and records the gap
without weakening security to make a build pass.

## Current state

- HTTP clients in the app use HTTPS URLs.
- No `networkSecurityConfig` is declared in `AndroidManifest.xml`.
- The app uses the platform TLS trust chain.

## Review findings

- HTTPS usage helps, but without an explicit network security config, the app's cleartext
  policy is governed by the platform defaults for the target SDK and any future
  configuration changes.
- For a production release that talks to a specific ntfy server, a network security
  config can strengthen the posture and make HTTPS requirements explicit.
- The app does not ship a custom TrustManager or HostnameVerifier, which is good.

## Recommendation

- Decide whether to declare a `networkSecurityConfig` in the manifest for production.
- If declared, use it to enforce HTTPS where appropriate and to document expected
  trust behavior.
- Do not introduce permissive configuration just to make a build or test pass.

## Status

- App-side HTTPS usage: present.
- Manifest network security config: not declared.
- Review outcome: gap documented; finalization pending owner decision.

