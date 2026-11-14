"""Read-only summary of live ntfy state (no tokens, no message bodies)."""
import sqlite3

DB = "/var/lib/docker/volumes/utilities_ntfy_data/_data/auth.db"
CACHE = "/var/lib/docker/volumes/utilities_ntfy_data/_data/cache.db"

db = sqlite3.connect(DB)
names = {row[0]: row[1] for row in db.execute("select id, user from user")}
print("identities:", len(names))
print("roles:", sorted((n, r) for n, r in db.execute("select user, role from user")))

anon = db.execute(
    "select topic, read, write from user_access where user_id="
    "(select id from user where user='*') order by topic"
).fetchall()
print("anonymous '*' grants:", anon)

rows = db.execute(
    "select user_id, topic, read, write from user_access "
    "where topic in ('kf-command-result','rplab-command-results')"
).fetchall()
print("command-result grants:", [(names.get(u, u), t, r, w) for u, t, r, w in rows])
print("total grants:", db.execute("select count(*) from user_access").fetchone()[0])
db.close()

cache = sqlite3.connect(CACHE)
tables = [r[0] for r in cache.execute("select name from sqlite_master where type='table'")]
for table in ("message", "messages", "cache"):
    if table in tables:
        print(f"cached topics ({table}):",
              cache.execute(f"select topic, count(*) from {table} group by topic").fetchall())
        break
else:
    print("cache tables:", tables)
cache.close()
