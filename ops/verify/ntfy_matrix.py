#!/usr/bin/env python3
"""Pre/post authorization matrix for the kf- ntfy model. Runs on CT104."""
import json
import re
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8201"
CRED = "/opt/kaminari-ntfy/credentials.env"

tokens = {}
with open(CRED) as handle:
    user = None
    for line in handle:
        line = line.strip()
        if line.startswith("USER="):
            user = line.split("=", 1)[1]
        elif line.startswith("TOKEN=") and user:
            tokens[user] = line.split("=", 1)[1]

RESULTS = []


def req(method, path, token=None, data=None, headers=None):
    body = data.encode() if isinstance(data, str) else data
    r = urllib.request.Request(BASE + path, data=body, method=method)
    if token:
        r.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        r.add_header(k, v)
    try:
        with urllib.request.urlopen(r, timeout=10) as resp:
            return resp.status, resp.read().decode(errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(errors="replace")
    except Exception as e:  # noqa: BLE001
        return 0, f"transport: {type(e).__name__}"


def pub(topic, token=None):
    return req("POST", "/" + topic, token=token, data=f"probe {topic}")


def read(topic, token=None):
    return req("GET", f"/{topic}/json?poll=1&since=all", token=token)


def check(name, expected, actual):
    ok = actual == expected
    RESULTS.append((name, ok))
    print(f"{name}: expected={expected} actual={actual} {'PASS' if ok else 'FAIL'}")


# anonymous
check("anon_pub_legacy_homelab", 200, pub("KAMINARI-FUJI-Homelab")[0])
check("anon_pub_legacy_diary", 200, pub("kaminari-diary")[0])
check("anon_pub_legacy_onboarding", 200, pub("kaminari-onboarding-test")[0])
check("anon_pub_kf_monitor_topic", 403, pub("kf-pve-host")[0])
check("anon_read_kf_topic", 403, read("kf-pve-host")[0])
check("anon_pub_kf_music", 403, pub("kf-music-pipeline")[0])
# command-result topics must not be anonymous in either direction (correction C)
check("anon_pub_kf_command_result", 403, pub("kf-command-result")[0])
check("anon_read_kf_command_result", 403, read("kf-command-result")[0])
check("anon_pub_legacy_command_results", 403, pub("rplab-command-results")[0])
check("anon_read_legacy_command_results", 403, read("rplab-command-results")[0])

# kf_monitor
m = tokens["kf_monitor"]
check("monitor_pub_infra", 200, pub("kf-pve-host", m)[0])
check("monitor_pub_backups", 200, pub("kf-backups", m)[0])
check("monitor_pub_music_denied", 403, pub("kf-music-pipeline", m)[0])
check("monitor_pub_legacy_denied_by_default", 403, pub("random-topic", m)[0])

# kf_music
mu = tokens["kf_music"]
check("music_pub_pipeline", 200, pub("kf-music-pipeline", mu)[0])
check("music_pub_reports", 200, pub("kf-music-reports", mu)[0])
check("music_pub_infra_denied", 403, pub("kf-pve-host", mu)[0])
check("music_read_infra_denied", 403, read("kf-pve-host", mu)[0])

# kf_stickers (reserved but scoped)
st = tokens["kf_stickers"]
check("stickers_pub_reserved_topic", 200, pub("kf-stickers-pipeline", st)[0])
check("stickers_pub_news_denied", 403, pub("kf-news", st)[0])

# kaminari_app
app = tokens["kaminari_app"]
check("app_read_infra", 200, read("kf-pve-host", app)[0])
check("app_read_music", 200, read("kf-music-pipeline", app)[0])
check("app_pub_command_request", 200, pub("kf-command-request", app)[0])
check("app_pub_infra_denied", 403, pub("kf-pve-host", app)[0])
# Since the dashboard migration the app has an explicit read-only legacy grant, which
# overrides the anonymous `*` row for this identity (ntfy prefers the specific row).
check("app_read_legacy_homelab", 200, read("KAMINARI-FUJI-Homelab", app)[0])
check("app_pub_legacy_denied_by_specific_grant", 403, pub("kaminari-diary", app)[0])
check("app_read_command_result", 200, read("kf-command-result", app)[0])
check("app_pub_command_result_denied", 403, pub("kf-command-result", app)[0])

# kf_gateway
gw = tokens["kf_gateway"]
check("gateway_read_commands", 200, read("kf-command-request", gw)[0])
check("gateway_pub_result", 200, pub("kf-command-result", gw)[0])
check("gateway_pub_audit", 200, pub("kf-admin-audit", gw)[0])
check("gateway_pub_infra_denied", 403, pub("kf-pve-host", gw)[0])

# kf_dashboard (legacy publisher; the identity that replaces anonymous `*`)
db = tokens["kf_dashboard"]
check("dashboard_pub_legacy_homelab", 200, pub("KAMINARI-FUJI-Homelab", db)[0])
check("dashboard_read_legacy_homelab", 200, read("KAMINARI-FUJI-Homelab", db)[0])
check("dashboard_pub_infra_denied", 403, pub("kf-pve-host", db)[0])
check("dashboard_read_music_denied", 403, read("kf-music-pipeline", db)[0])

# kfadmin
ad = tokens["kfadmin"]
check("admin_pub_any", 200, pub("kf-pve-host", ad)[0])
check("admin_read_any", 200, read("kf-music-reports", ad)[0])

# invalid token
check("invalid_token_pub", 401, pub("kf-pve-host", "tk_not_a_real_token")[0])
check("invalid_token_read", 401, read("kf-pve-host", "tk_not_a_real_token")[0])

# token cannot be used as admin API
check("app_token_admin_api_denied", 401, req("GET", "/v1/users", token=app)[0])  # admin API requires basic auth

failed = [n for n, ok in RESULTS if not ok]
print(f"SUMMARY checks={len(RESULTS)} failed={len(failed)}")
for n in failed:
    print("FAILED:", n)
raise SystemExit(1 if failed else 0)
