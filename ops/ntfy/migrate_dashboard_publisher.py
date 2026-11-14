#!/usr/bin/env python3
"""Move the legacy dashboard publisher off anonymous `*` access.

The dashboard (`rplab-dashboard`) and its Docker-events watcher
(`kaminari-docker-events`) publish to the legacy topic `KAMINARI-FUJI-Homelab`
without credentials, which is the last reason the topic keeps an anonymous
`read-write` grant. This tool adds optional bearer-token support to those
publishers so the `*` rule can be removed once every producer has migrated.

Behaviour:

  * every edit is idempotent — a second run changes nothing;
  * `NTFY_TOKEN` is read from the environment at runtime, never written here;
  * without `NTFY_TOKEN` the code keeps working exactly as before (anonymous),
    so the migration itself cannot break the dashboard;
  * touched files are backed up next to themselves before being rewritten.

Usage:
  python3 ops/ntfy/migrate_dashboard_publisher.py --root /opt/stack/kaminari-fuji \\
      --events-root /opt/stack/kaminari-events [--apply]

Without `--apply` it only reports what would change.
"""

from __future__ import annotations

import argparse
import shutil
import time
from pathlib import Path

NOTIFY_ANCHOR = "const ENABLED = Boolean(NTFY_URL && NTFY_TOPIC) && process.env.NTFY_PUSH !== '0';"
NOTIFY_AUTH = """

/* Named identity instead of the anonymous `*` grant. Optional: when NTFY_TOKEN
 * is unset the publisher behaves exactly as before, so the `*` rule can be
 * removed without a flag day. */
const NTFY_TOKEN = process.env.NTFY_TOKEN || '';
const AUTH = NTFY_TOKEN ? { Authorization: `Bearer ${NTFY_TOKEN}` } : {};"""

NOTIFY_HEADERS_OLD = """    headers: {
      Title: title,"""
NOTIFY_HEADERS_NEW = """    headers: {
      ...AUTH,
      Title: title,"""

INDEX_OLD = "headers: { Title: 'Kaminari-Fuji test', Tags: 'bell' }"
INDEX_NEW = (
    "headers: { ...(process.env.NTFY_TOKEN "
    "? { Authorization: `Bearer ${process.env.NTFY_TOKEN}` } : {}), "
    "Title: 'Kaminari-Fuji test', Tags: 'bell' }"
)

REQUESTS_ANCHOR = """async function publishTo(url, topic, body, headers) {
  const res = await fetch(`${url}/${topic}`, {
    method: 'POST',
    headers: { ...headers, 'content-type': 'text/plain; charset=utf-8' },"""
REQUESTS_NEW = """/* Same optional identity as notify.js: the request bot publishes and streams
 * as a named user so the legacy `*` grant is no longer load-bearing. */
const NTFY_TOKEN = process.env.NTFY_TOKEN || '';
const NTFY_AUTH = NTFY_TOKEN ? { Authorization: `Bearer ${NTFY_TOKEN}` } : {};

async function publishTo(url, topic, body, headers) {
  const res = await fetch(`${url}/${topic}`, {
    method: 'POST',
    headers: { ...NTFY_AUTH, ...headers, 'content-type': 'text/plain; charset=utf-8' },"""

STREAM_OLD = "          headers: { Accept: 'application/x-ndjson' },"
STREAM_NEW = "          headers: { ...NTFY_AUTH, Accept: 'application/x-ndjson' },"

EVENTS_ANCHOR = 'NTFY_URL="http://192.168.0.104:8201/KAMINARI-FUJI-Homelab"'
EVENTS_NEW = """NTFY_URL="http://192.168.0.104:8201/KAMINARI-FUJI-Homelab"
# Optional named identity (kf_dashboard). Unset = previous anonymous behaviour.
NTFY_TOKEN="${NTFY_TOKEN:-}"

auth_header() {
  if [ -n "$NTFY_TOKEN" ]; then
    printf 'Authorization: Bearer %s' "$NTFY_TOKEN"
  fi
}"""
EVENTS_SEND_OLD = """  wget -q -O /dev/null \\
    --header="Title: $1" \\"""
EVENTS_SEND_NEW = """  wget -q -O /dev/null \\
    --header="$(auth_header)" \\
    --header="Title: $1" \\"""


def patch_file(path: Path, pairs: list[tuple[str, str]], apply: bool) -> bool:
    if not path.exists():
        print(f"missing  {path}")
        return False
    text = path.read_text()
    changed = False
    for old, new in pairs:
        if new in text:
            continue
        count = text.count(old)
        if count != 1:
            print(f"skip     {path.name}: anchor matched {count}x ({old.splitlines()[0][:50]!r})")
            return False
        text = text.replace(old, new)
        changed = True
    if not changed:
        print(f"ok       {path} (already migrated)")
        return True
    if apply:
        backup = path.with_name(f"{path.name}.pre-authz-{time.strftime('%Y%m%d%H%M%S')}")
        shutil.copy2(path, backup)
        path.write_text(text)
        print(f"patched  {path} (backup {backup.name})")
    else:
        print(f"would    patch {path}")
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("/opt/stack/kaminari-fuji"))
    parser.add_argument("--events-root", type=Path, default=Path("/opt/stack/kaminari-events"))
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()

    ok = True
    ok &= patch_file(args.root / "server/notify.js", [(NOTIFY_ANCHOR, NOTIFY_ANCHOR + NOTIFY_AUTH),
                                                      (NOTIFY_HEADERS_OLD, NOTIFY_HEADERS_NEW)], args.apply)
    ok &= patch_file(args.root / "server/index.js", [(INDEX_OLD, INDEX_NEW)], args.apply)
    ok &= patch_file(args.root / "server/requests.js", [(REQUESTS_ANCHOR, REQUESTS_NEW),
                                                        (STREAM_OLD, STREAM_NEW)], args.apply)
    ok &= patch_file(args.events_root / "events.sh", [(EVENTS_ANCHOR, EVENTS_NEW),
                                                      (EVENTS_SEND_OLD, EVENTS_SEND_NEW)], args.apply)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
