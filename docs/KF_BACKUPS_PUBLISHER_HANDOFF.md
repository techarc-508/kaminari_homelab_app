# `kf-backups` publisher handoff (Workstream B → Workstream A)

Companion to `docs/KF_BACKUPS_NOTIFICATION_CONTRACT.md`. The ntfy/Android side of
the boundary is done and verified; this is what remains on the backup-engine side.

## State of the boundary (verified 2026-11-15)

| Item | State |
|---|---|
| Topic `kf-backups` | exists, `auth-default-access: deny-all` |
| `kf_monitor` write | granted (publish → 200) |
| `kaminari_app` read | granted (read → 200) |
| anonymous read/write | 403 both |
| `ops/verify/ntfy_matrix.py` | 41/41 PASS, includes `monitor_pub_backups` |
| Real producer | **absent** — no notification code exists in `/opt/kf-backup/bin` |

Nothing else may publish to `kf-backups`; ask before changing the topic's ACLs.

## Install

```bash
install -m 0750 ops/backups/kf_backups_notify.py /opt/kf-backup/bin/kf-backups-notify.py
install -m 0600 /dev/null /etc/kf-backup/config/kf-notify.env
# paste, inside that file:  NTFY_TOKEN=<kf_monitor token from /opt/kaminari-ntfy/credentials.env>
```

The token is `kf_monitor`'s, not the gateway's and not `kaminari_app`'s.

## Wire it into `backup-daily.sh`

```bash
NOTIFY=/opt/kf-backup/bin/kf-backups-notify.py
NOTIFY_ARGS="--env-file /etc/kf-backup/config/kf-notify.env --target ct104 --job daily-vzdump"

"$NOTIFY" $NOTIFY_ARGS --event backup.started --generation-id "$GEN" --outcome unknown || true

# … real work …

"$NOTIFY" $NOTIFY_ARGS --event backup.completed --generation-id "$GEN" \
    --outcome success --archive-created --integrity-verified --archive-bytes "$BYTES" || true

# on failure path
"$NOTIFY" $NOTIFY_ARGS --event backup.failed --generation-id "$GEN" \
    --outcome failed --severity critical \
    --failure-code "$CODE" --failure-message "$SHORT_TEXT" || true
```

Rules the tool enforces (so a bug fails loudly instead of lying):

- `--archive-created` only when an archive exists; `--integrity-verified` only when
  verification actually ran — never implied by creation;
- `--restore-tested` and `--offsite-copied` only with real evidence;
- `--outcome` must be stated; `backup.completed --outcome success` without an
  archive is rejected;
- `--target` is a logical name (`ct104`, `pve`, `apps`), never a path;
- the payload is capped at 4 KB and credential-shaped keys are rejected.

`|| true` keeps notification failure from failing the backup itself. The tool
exits non-zero (3) on transport failure, which is worth logging.

## Verification the engine side must show

```bash
# 1. the payload the engine would send
/opt/kf-backup/bin/kf-backups-notify.py --dry-run … 

# 2. one real publish, then read it back as the phone identity
curl -s -H "Authorization: Bearer $KFAPP_TOKEN" \
  'http://192.168.0.104:8201/kf-backups/json?poll=1&since=10m'
```

Expect one JSON body with `schema: kf.backup.event.v1` and the same
`generation_id` on start and finish. Report the first real `generation_id` back so
the Android side can retire its "no source" state.

## Known blocker on the engine side

`kf-backup.service` failed on 2026-11-14 (`tee: /dev/stderr: No such device or
address` after the `make-room` prune) and `kf-backup.timer` is disabled. A producer
wired into a failing job would only publish `backup.failed`, which is honest but
not useful. Fix the engine first, then wire the notifier.
