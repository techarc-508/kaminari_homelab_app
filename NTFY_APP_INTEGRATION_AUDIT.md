# Kaminari Nfty — App Integration and Security Audit

**Stage:** Phase 8, Stage 4C.
**Start commit:** `381235c`
**Date:** 2026-11-10

## 1. Scope

This audit reviews the Android app and gateway integration behavior against the
discovered ntfy environment and the existing implementation. It is a code/config
integration audit. Live server verification was limited because the public ntfy
URL returned 502 from the audit host during this stage.

## 2. Server URL configuration

### 2.1 Current default
- Default `ServerConfig.serverUrl` is
  `https://nfty.rplab.qzz.io`.
- Default auth method is `GUEST`.
- `useHttpsOnly` defaults to `true`.

### 2.2 Observations
- The default URL matches the repository’s existing referenced production URL.
- The URL was not reachable from the Proxmox host at audit time
  (HTTP 502 from Cloudflare).
- The app allows per-profile server URL configuration, so the default is not
  hardcoded in a way that prevents using a different reachable endpoint.

### 2.3 Assessment
- Configuration model is acceptable.
- The unreachable URL is an infrastructure verification issue, not an app
  defect by itself.
- Owner must confirm the correct reachable production URL before full integration
  can be validated.

## 3. Authentication handling

### 3.1 Supported methods
- Guest/anonymous
- Username and password
- Access token

### 3.2 State model
- The app has explicit auth states including
  `GUEST`, `REACHABLE — AUTH UNVERIFIED`, `AUTHENTICATED`,
  `AUTHENTICATION_FAILED`, `PERMISSION_DENIED`.
- This is important because reachability alone does not prove authentication.

### 3.3 Assessment
- Auth model is appropriate for a multi-profile ntfy client.
- The current default guest path is reasonable if guest access is enabled on the
  existing server.
- If the existing server disables guest access or restricts guest topics, the
  default profile may need reconfiguration.

## 4. Guest versus superuser permissions

Not confirmed live:
- Whether guest access works on the current server.
- Whether the intended topics allow guest publish/subscribe.
- Whether superuser/admin topics are separate.

Assessment:
- The app supports both guest and authenticated use.
- The audit cannot assert the server-side permission reality at this time.
- This is a required live verification item, not an app defect.

## 5. Topic subscription and filtering

### 5.1 Implementation
- The app supports subscribing to topic lists per profile.
- Subscription handling includes stream parsing and topic-level update handling.
- Stream parsing code bounds a single line to prevent hostile/broken server
  input from exhausting memory.

### 5.2 Assessment
- Subscription and filtering design is reasonable.
- Stream bounds and structured error handling are good integration safeguards.
- Final subscription correctness depends on the live server and topic ACLs.

## 6. Instant SSE delivery

### 6.1 Implementation
- The app uses SSE/streaming for live delivery.
- It has instant delivery path plus a Battery Saver WorkManager catch-up path
  for constraints/wake windows.

### 6.2 Assessment
- Delivery architecture is appropriate.
- The catch-up path is a good fallback for Doze/Battery Saver conditions.
- Live delivery success depends on network reachability and server availability.

## 7. Outbox and publish result handling

### 7.1 Implementation
- Publishing goes through an outbox-style flow with publish result handling.
- Validation exists before publish.
- Recovery/stuck-row handling exists for rows left SENDING by a killed process.

### 7.3 Assessment
- Outbox behavior is appropriate for unreliable networks.
- Note: `ACCEPTED` by the server does not prove recipient delivery. This is
  already understood and should remain explicit in the app/docs.

## 8. NtfyAdminClient capabilities

### 8.1 Implementation
- Thin client for documented ntfy admin endpoints.
- Requires an admin-capable credential.
- Never stores/logs credentials per its documented intent.
- Admin operations include user listing, user creation, deletion, and access
  grants.

### 8.2 Assessment
- Admin client is appropriately limited and does not pretend to be a full admin
  console.
- Admin operations are sensitive and must be protected by appropriate server-side
  admin credentials and ACLs.
- Admin client success also depends on the server supporting the documented
  endpoints and the credential having the right privileges.

## 9. Command Centre request/response topic flow

### 9.1 Implementation
- Command flow is gated behind the command Centre feature.
- Mock executor is the default in `AppContainer`.
- Real gateway executor exists but is separate from the ntfy publish/subscribe
  core.

### 9.2 Assessment
- Default mock boundary is safe.
- Command request/response flow is a separate concern from general notifications.
- Command topics should not be merged into general inbox semantics.

## 10. Gateway authentication separation

### 10.1 Implementation
- Gateway uses its own gateway token, separate from ntfy auth.
- Proxmox API token stays server-side in the gateway.
- Mutations are disabled by default.
- Approval-token binding exists for mutations.

### 10.2 Assessment
- Good separation between ntfy client auth and Proxmox gateway auth.
- This matters because reusing the ntfy server does not mean reusing the same
  credentials for Proxmox gateway access.

## 11. Token storage and redaction

### 11.1 Implementation
- Sensitive values are stored via the existing secret store abstractions.
- Redaction/sanitization exists for logs and output where secrets may appear.

### 11.2 Assessment
- Token handling is consistent with earlier hardening.
- Admin client results and gateway audit paths should continue to avoid printing
  raw secrets.

## 12. Error handling

### 12.1 Covered categories
- DNS resolution failure
- Timeout
- HTTP error from server
- Stream drops
- Offline behavior
- Auth failure and permission denied distinctions

### 12.2 Assessment
- Error handling is reasonably complete for the client side.
- Some failures can only be fully validated against a live server.

## 13. Security conclusions

### 13.1 No app-side regressions found in this audit
- Server URL is configurable and not forced to a hardcoded live endpoint at
  runtime for all profiles.
- Auth model supports least-privilege use.
- Command execution remains gated and separate.
- Admin client is intentionally limited.
- Secrets handling and redaction are in place.

### 13.2 Open integration questions
- Is `https://nfty.rplab.qzz.io` reachable and correct right now?
- Does the existing server allow the app’s default guest profile?
- Do the intended topics exist with appropriate ACLs?
- Does the existing server expose the admin endpoints the admin client expects,
  and with what privilege?

### 13.3 Recommended before production integration
- Confirm a reachable production URL.
- Verify guest and authenticated access against live topics.
- Verify admin endpoint behavior before relying on admin features.
- Verify command topic separation.
- Confirm no credential leakage in logs for real traffic.

## 14. Final verdict

The app integration design is sound and reusable with the existing ntfy
infrastructure, subject to live confirmation of:
- server reachability,
- auth/ACL reality,
- topic existence,
- and admin endpoint support.

The current integration blocker is infrastructure verification, not app
architecture.
