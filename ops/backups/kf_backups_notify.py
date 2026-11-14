#!/usr/bin/env python3
"""Publish one `kf-backups` event, exactly as `docs/KF_BACKUPS_NOTIFICATION_CONTRACT.md` defines it.

Workstream A (the backup engine) owns the evidence; this tool only carries it.
It refuses to invent anything:

  * the three archive booleans are independent flags, never derived from each other;
  * `outcome` must be stated explicitly — it is never inferred from a timer;
  * unknown `event`/`outcome`/`severity` values are rejected before a byte is sent;
  * the payload is capped at 4 KB and scanned for credential-shaped keys.

Install (Workstream A side):

  install -m 0750 ops/backups/kf_backups_notify.py /opt/kf-backup/bin/kf-backups-notify.py
  install -m 0600 /dev/null /etc/kf-backup/config/kf-notify.env    # then paste NTFY_TOKEN
  chmod 0600 /etc/kf-backup/config/kf-notify.env

Call it from `backup-daily.sh` (start and finish) with the evidence you actually have:

  kf-backups-notify.py --event backup.started   --generation-id "$GEN" --target ct104 \\
      --job daily-vzdump --outcome unknown
  kf-backups-notify.py --event backup.completed --generation-id "$GEN" --target ct104 \\
      --job daily-vzdump --outcome success --archive-created --integrity-verified

Exit codes: 0 published, 1 invalid arguments/payload, 3 transport failure.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

DEFAULT_URL = "https://ntfy.rplab.qzz.io"
TOPIC = "kf-backups"
SCHEMA = "kf.backup.event.v1"
EVENTS = {
    "backup.started",
    "backup.completed",
    "backup.failed",
    "backup.verify",
    "backup.restore_drill",
    "backup.retention",
}
OUTCOMES = {"success", "partial", "failed", "unknown"}
SEVERITIES = {"info", "warning", "critical"}
MAX_BYTES = 4096
# A payload that trips these is a bug in the caller, not a notification.
FORBIDDEN_KEY_HINTS = ("token", "password", "secret", "key", "credential", "authorization")


def now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Publish one kf-backups event to ntfy.")
    p.add_argument("--event", required=True, choices=sorted(EVENTS))
    p.add_argument("--generation-id", required=True, help="stable id shared by every event of one run")
    p.add_argument("--target", required=True, help="logical target name (ct104, pve, apps) — never a path")
    p.add_argument("--job", required=True, help="job name, e.g. daily-vzdump")
    p.add_argument("--outcome", required=True, choices=sorted(OUTCOMES))
    p.add_argument("--severity", default="info", choices=sorted(SEVERITIES))
    p.add_argument("--started-at", default=None)
    p.add_argument("--finished-at", default=None)
    p.add_argument("--archive-created", action="store_true", help="an archive was produced")
    p.add_argument("--integrity-verified", action="store_true", help="the archive was verified (not implied by creation)")
    p.add_argument("--restore-tested", action="store_true", help="a restore drill actually ran")
    p.add_argument("--offsite-copied", action="store_true", help="a copy reached the off-site/secondary target and was checked")
    p.add_argument("--archive-bytes", type=int, default=None)
    p.add_argument("--failure-code", default=None)
    p.add_argument("--failure-message", default=None)
    p.add_argument("--url", default=os.environ.get("NTFY_URL", DEFAULT_URL))
    p.add_argument("--env-file", type=Path, default=None, help="file with NTFY_TOKEN=... (mode 600)")
    p.add_argument("--dry-run", action="store_true", help="validate and print the payload, publish nothing")
    p.add_argument("--topic", default=TOPIC, help=argparse.SUPPRESS)  # transport self-test only
    return p.parse_args(argv)


def read_token(args: argparse.Namespace) -> str:
    token = os.environ.get("NTFY_TOKEN", "").strip()
    if not token and args.env_file:
        if not args.env_file.exists():
            raise SystemExit(f"env file not found: {args.env_file}")
        if args.env_file.stat().st_mode & 0o077:
            raise SystemExit(f"refusing: {args.env_file} is readable by group/other (chmod 600)")
        for line in args.env_file.read_text().splitlines():
            if line.startswith("NTFY_TOKEN="):
                token = line.split("=", 1)[1].strip()
    if not token:
        raise SystemExit("no NTFY_TOKEN (set the env var or pass --env-file)")
    return token


def build_payload(args: argparse.Namespace) -> dict:
    archive = {
        "created": bool(args.archive_created),
        "bytes": args.archive_bytes,
        "integrity_verified": bool(args.integrity_verified),
        "restore_tested": bool(args.restore_tested),
        "offsite_copied": bool(args.offsite_copied),
    }
    failure = None
    if args.failure_code or args.failure_message:
        if not args.failure_code:
            raise SystemExit("--failure-message requires --failure-code")
        failure = {"code": args.failure_code, "message": (args.failure_message or "")[:200]}

    if args.event == "backup.completed" and args.outcome == "success" and not archive["created"]:
        raise SystemExit("backup.completed with outcome=success but no archive was created — "
                         "this is exactly the inference the contract forbids")
    if failure and args.outcome == "success":
        raise SystemExit("outcome=success cannot carry a failure object")

    return {
        "schema": SCHEMA,
        "event": args.event,
        "generation_id": args.generation_id,
        "started_at": args.started_at,
        "finished_at": args.finished_at or (now() if args.event != "backup.started" else None),
        "target": args.target,
        "job": args.job,
        "outcome": args.outcome,
        "archive": archive,
        "failure": failure,
        "severity": args.severity,
    }


def validate(payload: dict) -> bytes:
    body = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode()
    if len(body) > MAX_BYTES:
        raise SystemExit(f"payload is {len(body)} bytes; the contract caps it at {MAX_BYTES}")
    lowered = json.dumps(payload).lower()
    for hint in FORBIDDEN_KEY_HINTS:
        if f'"{hint}"' in lowered:
            raise SystemExit(f"payload contains a credential-shaped key ({hint}); refusing to publish")
    if payload["target"].startswith("/") or "/" in payload["target"]:
        raise SystemExit("target must be a logical name, not a filesystem path")
    return body


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    payload = build_payload(args)
    body = validate(payload)

    if args.dry_run:
        print(json.dumps(payload, indent=2, sort_keys=True))
        print(f"\ndry run: {len(body)} bytes, nothing published", file=sys.stderr)
        return 0

    token = read_token(args)
    req = urllib.request.Request(
        f"{args.url.rstrip('/')}/{args.topic}",
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "Title": f"Backup {payload['event'].split('.', 1)[1]}: {payload['target']}",
            "Priority": "5" if payload["severity"] == "critical" else ("4" if payload["severity"] == "warning" else "3"),
            "Tags": "floppy_disk",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            print(f"published {payload['event']} for {payload['target']} (HTTP {res.status})")
            return 0
    except urllib.error.HTTPError as exc:
        print(f"publish failed: HTTP {exc.code} {exc.read()[:200]!r}", file=sys.stderr)
        return 3
    except urllib.error.URLError as exc:
        print(f"publish failed: {exc.reason}", file=sys.stderr)
        return 3


if __name__ == "__main__":
    raise SystemExit(main())
