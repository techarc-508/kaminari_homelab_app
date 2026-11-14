"""Prove the phone-facing HTTPS endpoint end-to-end.

Runs on CT104. Uses the real public hostname the Android client is configured
with, the OkHttp user-agent the app sends, a temporary restricted topic, and the
same SSE request shape the app uses. The temporary ACL row is removed at the end
and the removal is verified.
"""
import secrets
import sqlite3
import subprocess
import time

BASE = "https://ntfy.rplab.qzz.io"
DB = "/var/lib/docker/volumes/utilities_ntfy_data/_data/auth.db"
UA = "okhttp/4.12.0"

tokens = {}
user = None
for line in open("/opt/kaminari-ntfy/credentials.env"):
    line = line.strip()
    if line.startswith("USER="):
        user = line.split("=", 1)[1]
    elif line.startswith("TOKEN=") and user:
        tokens[user] = line.split("=", 1)[1]
app = tokens["kaminari_app"]


def curl(*args, timeout="15"):
    return subprocess.run(
        ["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", "--max-time", timeout,
         "-A", UA, *args],
        capture_output=True,
        text=True,
    ).stdout.strip()


topic = "kf-e2e-probe-" + secrets.token_hex(4)
db = sqlite3.connect(DB)
uid = db.execute("select id from user where user=?", ("kaminari_app",)).fetchone()[0]
with db:
    db.execute(
        "insert into user_access (user_id, topic, read, write, provisioned)"
        " values (?,?,1,1,0)",
        (uid, topic),
    )
db.close()
print("temp_topic", topic)

print("okhttp_ua_health", curl(BASE + "/v1/health"))
print("anon_publish_temp_denied", curl("-X", "POST", "-d", "x", BASE + "/" + topic))
print(
    "app_publish_temp_https",
    curl("-X", "POST", "-H", "Authorization: Bearer " + app, "-d", "probe https e2e",
         BASE + "/" + topic),
)

stream = subprocess.Popen(
    ["curl", "-sN", "--max-time", "20", "-A", UA,
     "-H", "Authorization: Bearer " + app, BASE + "/" + topic + "/sse?since=all"],
    stdout=subprocess.PIPE,
    text=True,
)
time.sleep(2)
curl("-X", "POST", "-H", "Authorization: Bearer " + app, "-d", "streamed-over-https",
     BASE + "/" + topic)
time.sleep(3)
stream.terminate()
out = stream.stdout.read()
print("sse_lines", len(out.strip().splitlines()))
print("sse_received_message", "streamed-over-https" in out)
print("sse_has_event", '"event"' in out)

db = sqlite3.connect(DB)
with db:
    db.execute("delete from user_access where user_id=? and topic=?", (uid, topic))
db.close()
print("temp_acl_removed", curl("-X", "POST", "-H", "Authorization: Bearer " + app, "-d", "x",
                              BASE + "/" + topic) == "403")
