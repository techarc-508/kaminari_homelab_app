# `kf-backups` notification contract (Workstream A ↔ B)

Workstream A (OpenCode) owns the backup engine. Workstream B (Freebuff) owns the
ntfy identity model and the Android consumer. This file is the written boundary
between them; neither side may change ntfy configuration for this topic without
the other being told.

## Topic

| Item | Value |
|---|---|
| Topic | `kf-backups` |
| Publisher identity | `kf_monitor` (already granted write, **no other identity may publish**) |
| Readers | `kaminari_app` (the phone), `kfadmin` (admin) |
| Anonymous access | **none** — `*` has no rule for this topic |
| Retention / priority | `normal`, schema `kf.infrastructure.v1` |
| Current state | topic exists, publisher works, **no producer is wired yet** (see `docs/KF_BACKUPS_PUBLISHER_HANDOFF.md` for the install + call recipe) |

Token: `kf_monitor` in `/opt/kaminari-ntfy/credentials.env`. Never in a report,
a commit, a chat message or a backup log.

## Payload (UTF-8 JSON body; keep it under 4 KB)

```json
{
  "schema": "kf.backup.event.v1",
  "event": "backup.completed",
  "generation_id": "2026-11-15T02:00:00Z/ct104-daily",
  "started_at": "2026-11-15T02:00:03Z",
  "finished_at": "2026-11-15T02:14:51Z",
  "target": "ct104",
  "job": "daily-vzdump",
  "outcome": "success",
  "archive": {
    "created": true,
    "bytes": 7340032000,
    "integrity_verified": true,
    "restore_tested": false,
    "offsite_copied": false
  },
  "failure": null,
  "severity": "info"
}
```

Rules:

- `event` ∈ `backup.started` · `backup.completed` · `backup.failed` · `backup.verify` · `backup.restore_drill` · `backup.retention`.
- `outcome` ∈ `success` · `partial` · `failed` · `unknown` — never inferred from a timer.
- The three booleans are **independent** and must reflect real evidence:
  an archive being created is not integrity verification, and neither implies a
  restore test or an off-site copy.
- `failure` carries `code` and a short message. No credentials, no key material,
  no archive paths beyond the logical target name, no full logs.
- `severity` ∈ `info` · `warning` · `critical`; the phone maps `critical` to a
  high-priority notification.
- Every event carries the same `generation_id` for one backup run, so the phone
  can correlate start/finish/verify without guessing.

## Android side responsibilities

- Show `configured / last attempted / last successful / integrity verified /
  restore tested / off-site copy verified` as **separate** states.
- Never render green because a timer elapsed or because `archive.created` is true.
- Distinguish "publish accepted by ntfy" from "received by this device".
- Treat a missing topic or an empty history as "no backup source", not "healthy".

## Coordination protocol

1. Workstream A announces it is ready to publish and which `event` values it
   emits.
2. Workstream B confirms the `kf_monitor` token is in place and that
   `ops/verify/ntfy_matrix.py` still passes (`monitor_pub_backups` = 200,
   anonymous = 403).
3. One end-to-end event is published from A and observed by B on the phone.
4. Only then does the Android backup screen leave its empty state.

Until step 3 happens, the backup screen must say "no source" and the reports must
not describe backups as monitored.
