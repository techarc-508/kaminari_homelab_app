#!/usr/bin/env python3
"""Reconcile ntfy's SQLite auth database with the kf- contract.

ntfy 2.28 stores users, tokens and topic permissions in `auth.db` (SQLite), so
the previous YAML generator produced a file the running server never read. This
tool works on the real store:

  * the contract (`contracts/kf_contract.v1.json`) is the only source of truth;
  * identity -> topic permissions are derived deterministically from
    `writers`/`readers`, plus anonymous access only for legacy topics that opt
    in with `"anonymous": true`;
  * command-result topics are never anonymous — no `*` row is generated for
    them, and any stray `*` row is deleted;
  * applying an identical contract is a no-op (idempotent);
  * `--apply` writes a timestamped backup of `auth.db` first.

Usage:
  # what would change (safe, read-only)
  python3 ops/ntfy/generate_auth.py --db /path/to/auth.db

  # apply, then restart ntfy and run ops/verify/ntfy_matrix.py
  python3 ops/ntfy/generate_auth.py --db /path/to/auth.db --apply

The tool never prints or writes tokens. Tokens live in the DB and in the
operator's `/opt/kaminari-ntfy/credentials.env`, never here.
"""

from __future__ import annotations

import argparse
import json
import shutil
import sqlite3
import time
from pathlib import Path

ANONYMOUS_USER = "*"


def load_contract(path: Path) -> dict:
    return json.loads(path.read_text())


def desired_state(contract: dict) -> tuple[dict[str, str], list[tuple[str, str, int, int]]]:
    """Return ({identity: role}, [(identity, topic, read, write)])."""
    roles: dict[str, str] = {}
    for identity in contract["identities"]:
        roles[identity["name"]] = "admin" if identity.get("ntfyAdmin") else "user"
    # Legacy-only publishers (the dashboard) are real ntfy users but deliberately
    # not app identities, so they live outside `identities` and never reach the APK.
    for identity in contract.get("legacyIdentities", []):
        roles[identity["name"]] = "admin" if identity.get("ntfyAdmin") else "user"

    access: list[tuple[str, str, int, int]] = []

    # Anonymous access is opt-in per legacy topic and never applies to kf- topics.
    for topic in contract["legacyTopics"]:
        if topic.get("anonymous"):
            access.append((ANONYMOUS_USER, topic["id"], 1, 1))
        # Explicit legacy grants let a named producer/consumer work while `*`
        # is still in place, so removing `*` later is a measured step.
        for writer in topic.get("writers", []):
            access.append((writer, topic["id"], 0, 1))
        for reader in topic.get("readers", []):
            access.append((reader, topic["id"], 1, 0))

    admins = {name for name, role in roles.items() if role == "admin"}
    for topic in contract["topics"]:
        tid = topic["id"]
        for writer in topic["writers"]:
            if writer not in admins:
                access.append((writer, tid, 0, 1))
        for reader in topic["readers"]:
            if reader not in admins:
                access.append((reader, tid, 1, 0))

    # An identity that both reads and writes the same topic collapses to rw.
    merged: dict[tuple[str, str], list[int]] = {}
    for identity, topic, read, write in access:
        slot = merged.setdefault((identity, topic), [0, 0])
        slot[0] = max(slot[0], read)
        slot[1] = max(slot[1], write)
    return roles, [(i, t, r, w) for (i, t), (r, w) in sorted(merged.items())]


def read_live(db: sqlite3.Connection) -> tuple[dict[str, str], dict[str, str], dict[tuple[str, str], tuple[int, int]]]:
    users = {row[1]: row[0] for row in db.execute("SELECT id, user FROM user")}
    ids = {row[0]: row[1] for row in db.execute("SELECT id, user FROM user")}
    access = {
        (ids[row[0]], row[1]): (int(row[2]), int(row[3]))
        for row in db.execute("SELECT user_id, topic, read, write FROM user_access")
        if row[0] in ids
    }
    return users, ids, access


def diff(
    roles: dict[str, str],
    desired: list[tuple[str, str, int, int]],
    users: dict[str, str],
    access: dict[tuple[str, str], tuple[int, int]],
) -> tuple[list[str], list[tuple[str, str]], list[tuple[str, str, int, int]], list[tuple[str, str]]]:
    missing_users = sorted(name for name in roles if name not in users)
    wanted = {(i, t): (r, w) for i, t, r, w in desired}
    add = [(i, t, r, w) for (i, t), (r, w) in wanted.items() if (i, t) not in access and i in users]
    update = [
        (i, t, r, w)
        for (i, t), (r, w) in wanted.items()
        if (i, t) in access and access[(i, t)] != (r, w) and i in users
    ]
    remove = [(i, t) for (i, t) in access if (i, t) not in wanted]
    return missing_users, sorted(remove), sorted(add), sorted(update)


def render_matrix(contract: dict, roles: dict[str, str], desired: list[tuple[str, str, int, int]]) -> str:
    by_identity: dict[str, list[str]] = {}
    for identity, topic, read, write in desired:
        if identity == ANONYMOUS_USER:
            continue
        label = "rw" if read and write else ("ro" if read else "wo")
        by_identity.setdefault(identity, []).append(f"`{topic}` {label}")
    lines = [
        "# ntfy permission matrix (generated from contracts/kf_contract.v1.json)",
        "",
        "Apply with `ops/ntfy/generate_auth.py --apply`; verify with `ops/verify/ntfy_matrix.py`.",
        "",
        "| Identity | Role | Topic permissions |",
        "|---|---|---|",
    ]
    for name in sorted(roles):
        if roles[name] == "admin":
            lines.append(f"| `{name}` | admin | all topics (server-side only, never shipped in the app) |")
            continue
        topics = sorted(by_identity.get(name, []))
        lines.append(f"| `{name}` | user | {', '.join(topics) if topics else 'none'} |")
    legacy_anon = [t["id"] for t in contract["legacyTopics"] if t.get("anonymous")]
    lines.append(
        "| anonymous `*` | anonymous | "
        + (", ".join(f"`{t}` rw" for t in legacy_anon) if legacy_anon else "none")
        + " |"
    )
    lines.append("")
    lines.append("Command-result topics (`{}`, {}) have no anonymous row and no `*` rule.".format(
        contract["commandReplyTopic"], ", ".join(contract["legacyCommandReplyTopics"])
    ))
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", type=Path, default=Path("contracts/kf_contract.v1.json"))
    parser.add_argument("--db", type=Path, required=True, help="path to ntfy auth.db")
    parser.add_argument("--apply", action="store_true", help="write the diff (backs up auth.db first)")
    parser.add_argument("--matrix", type=Path, help="optional path to write the permission matrix")
    args = parser.parse_args()

    contract = load_contract(args.contract)
    roles, desired = desired_state(contract)

    db = sqlite3.connect(args.db)
    try:
        users, _ids, access = read_live(db)
        missing_users, remove, add, update = diff(roles, desired, users, access)

        print(f"identities in contract : {len(roles)}")
        print(f"live identities        : {len([u for u in users if u != ANONYMOUS_USER])}")
        print(f"desired grants         : {len(desired)}")
        print(f"missing identities     : {missing_users or 'none'}")
        print(f"grants to add          : {len(add)}")
        print(f"grants to update       : {len(update)}")
        print(f"grants to remove       : {len(remove)}")
        for identity, topic in remove:
            print(f"  remove {identity}:{topic}")
        for identity, topic, read, write in add:
            print(f"  add    {identity}:{topic}:{'rw' if read and write else ('ro' if read else 'wo')}")

        matrix = render_matrix(contract, roles, desired)
        if args.matrix:
            args.matrix.write_text(matrix)
            print(f"matrix written         : {args.matrix}")

        if not args.apply:
            print("dry run — pass --apply to write")
            return 0
        if missing_users:
            print("refusing to apply: create the missing identities with `ntfy user add` first")
            return 2

        backup = args.db.with_suffix(f".db.bak-{time.strftime('%Y%m%d%H%M%S')}")
        shutil.copy2(args.db, backup)
        print(f"backup                 : {backup}")

        with db:
            for identity, topic in remove:
                db.execute("DELETE FROM user_access WHERE user_id=? AND topic=?", (users[identity], topic))
            for identity, topic, read, write in add:
                db.execute(
                    "INSERT INTO user_access (user_id, topic, read, write, provisioned) VALUES (?,?,?,?,0)",
                    (users[identity], topic, read, write),
                )
            for identity, topic, read, write in update:
                db.execute(
                    "UPDATE user_access SET read=?, write=? WHERE user_id=? AND topic=?",
                    (read, write, users[identity], topic),
                )
        print("applied                : restart the ntfy container so it reloads auth.db")
        return 0
    finally:
        db.close()


if __name__ == "__main__":
    raise SystemExit(main())
