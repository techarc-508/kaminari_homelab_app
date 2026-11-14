#!/usr/bin/env python3
"""Prove the legacy `*` grant is no longer load-bearing, then put it back.

The drill runs on CT104, against the live ntfy container:

  1. take an `auth.db` backup;
  2. temporarily drop `*` **write** on `KAMINARI-FUJI-Homelab` (read is left in
     place, and the window lasts seconds);
  3. check that anonymous publishing is now refused;
  4. check that the migrated dashboard still publishes (notify.js) and that the
     apps receive it;
  5. check that the migrated Docker-events watcher still publishes;
  6. restore the `*` grant and the original state, in `finally`, even on error.

Exit status is non-zero if any expectation fails or the restore fails.

Usage (on the host that runs the ntfy container):
  sudo python3 ops/verify/legacy_star_removal_drill.py
"""

from __future__ import annotations

import json
import shlex
import sqlite3
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

DB = Path("/var/lib/docker/volumes/utilities_ntfy_data/_data/auth.db")
CREDS = Path("/opt/kaminari-ntfy/credentials.env")
TOPIC = "KAMINARI-FUJI-Homelab"
BASE = "http://127.0.0.1:8201"
EVENTS_BASE = "http://192.168.0.104:8201"
DASHBOARD = "rplab-dashboard"
EVENTS = "kaminari-docker-events"

results: list[tuple[str, bool, str]] = []


def record(name: str, ok: bool, detail: str = "") -> None:
    results.append((name, ok, detail))
    print(f"{'PASS' if ok else 'FAIL'}  {name}{'  ' + detail if detail else ''}")


def sh(cmd: str, timeout: int = 60) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)


def token(user: str) -> str:
    block = []
    for line in CREDS.read_text().splitlines():
        if line.startswith("USER="):
            block = [line]
        elif block:
            block.append(line)
            if line.startswith("TOKEN=") and block[0] == f"USER={user}":
                return line.split("=", 1)[1].strip()
    return ""


def set_star_write(enabled: bool) -> None:
    with sqlite3.connect(DB) as db:
        db.execute(
            "UPDATE user_access SET write=? WHERE topic=? "
            "AND user_id=(SELECT id FROM user WHERE user='*')",
            (1 if enabled else 0, TOPIC),
        )
        row = db.execute(
            "SELECT read, write FROM user_access WHERE topic=? "
            "AND user_id=(SELECT id FROM user WHERE user='*')",
            (TOPIC,),
        ).fetchone()
    print(f"      * grant on {TOPIC} now read={row[0]} write={row[1]}")


def restart_ntfy() -> None:
    sh("docker restart ntfy", timeout=120)
    for _ in range(20):
        time.sleep(1)
        try:
            with urllib.request.urlopen(f"{BASE}/v1/health", timeout=2):
                return
        except Exception:  # noqa: BLE001 - readiness poll
            continue
    raise RuntimeError("ntfy did not become healthy after restart")


def publish_anonymous() -> int:
    data = b"KF-DRILL anonymous publish (should be refused)"
    req = urllib.request.Request(f"{BASE}/{TOPIC}", data=data, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=5) as res:
            return res.status
    except urllib.error.HTTPError as exc:
        return exc.code


def dashboard_publish(message: str) -> None:
    js = (
        "import('/app/server/notify.js').then(m=>{"
        f"m.notify({{msg:{json.dumps(message)}}});"
        "setTimeout(()=>process.exit(0),2500)})"
    )
    out = sh(f"docker exec {DASHBOARD} node -e {json.dumps(js)}", timeout=60)
    if out.returncode != 0:
        print(out.stdout, out.stderr)


def events_publish(message: str) -> int:
    inner = (
        'if [ -n "$NTFY_TOKEN" ]; then wget -q -O /dev/null '
        '--header="Authorization: Bearer $NTFY_TOKEN" '
        f'--header="Title: KF drill" --post-data="{message}" '
        # Same address the watcher itself uses: 127.0.0.1 inside the container
        # is the container's own namespace, not the host.
        f"{EVENTS_BASE}/{TOPIC}; echo $?; else echo 9; fi"
    )
    # shlex.quote keeps `$NTFY_TOKEN` for the container's shell instead of
    # letting the caller's shell expand it to an empty string first.
    out = sh(f"docker exec {EVENTS} sh -c {shlex.quote(inner)}", timeout=60)
    try:
        return int(out.stdout.strip().splitlines()[-1])
    except (ValueError, IndexError):
        return 9


def subscribe(messages: list[str]) -> subprocess.Popen:
    app = token("kaminari_app")
    proc = subprocess.Popen(
        ["curl", "-sN", "-H", f"Authorization: Bearer {app}", f"{BASE}/{TOPIC}/json"],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
    )

    def reader() -> None:
        assert proc.stdout is not None
        for line in proc.stdout:
            try:
                messages.append(json.loads(line).get("message", ""))
            except json.JSONDecodeError:
                continue

    import threading

    threading.Thread(target=reader, daemon=True).start()
    return proc


def wait_for(messages: list[str], needle: str, seconds: float = 6.0) -> bool:
    deadline = time.time() + seconds
    while time.time() < deadline:
        if any(needle in m for m in messages):
            return True
        time.sleep(0.25)
    return False


def main() -> int:
    if not DB.exists():
        print(f"FAIL  auth.db not found at {DB}")
        return 1

    stamp = time.strftime("%Y%m%d%H%M%S")
    sh(f"cp -a {DB} {DB}.drill-{stamp}")

    messages: list[str] = []
    sub = None
    try:
        record("dashboard has a named token", bool(token("kf_dashboard")))

        set_star_write(False)
        restart_ntfy()
        sub = subscribe(messages)
        time.sleep(1)

        anon = publish_anonymous()
        record("anonymous publish is refused while * write is off", anon == 403, f"HTTP {anon}")

        dashboard_publish("KF-DRILL dashboard publish without * write")
        record(
            "dashboard notify.js still publishes",
            wait_for(messages, "KF-DRILL dashboard publish"),
        )

        code = events_publish("KF-DRILL events publish without * write")
        record("docker-events watcher still publishes", code == 0, f"wget exit {code}")
        record(
            "docker-events message is received",
            wait_for(messages, "KF-DRILL events publish"),
        )
    finally:
        if sub is not None:
            sub.terminate()
        set_star_write(True)
        restart_ntfy()

    restored = publish_anonymous()
    record("anonymous publish restored", restored in (200, 202), f"HTTP {restored}")

    failed = [name for name, ok, _ in results if not ok]
    print()
    print(f"{len(results) - len(failed)}/{len(results)} checks passed")
    if failed:
        print("failed: " + ", ".join(failed))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
