#!/usr/bin/env bash
# Offline encrypted backup of the Kaminari Nfty release signing identity.
#
# The archive contains the keystore and its password file, so it is only ever
# written encrypted, and the passphrase is never taken from the command line
# (which would land in shell history and process listings).
#
#   KF_BACKUP_PASSPHRASE_FILE=~/.kaminari-signing/backup-passphrase \
#     ops/signing/backup-signing-key.sh /run/media/offline/kaminari-signing-2026-11-14.tar.gz.gpg
#
# Without KF_BACKUP_PASSPHRASE_FILE the script prompts on the terminal twice.
# After writing, the archive is decrypted into a temporary directory, the
# keystore digest is compared and `keytool -list` must succeed; only then is the
# backup reported as usable.
set -euo pipefail

SIGNING_DIR="${KF_SIGNING_DIR:-$HOME/.kaminari-signing}"
KEYSTORE="${KF_KEYSTORE_PATH:-$SIGNING_DIR/release.jks}"
PASSWORD_FILE="${KF_SIGNING_PASSWORD_FILE:-$SIGNING_DIR/password.txt}"
OUT="${1:-}"

die() { printf 'error: %s\n' "$*" >&2; exit 1; }

[ -n "$OUT" ] || die "usage: $0 <output.tar.gz.gpg>"
[ -f "$KEYSTORE" ] || die "keystore not found: $KEYSTORE"
[ -f "$PASSWORD_FILE" ] || die "password file not found: $PASSWORD_FILE"
case "$OUT" in
  *.gpg) ;;
  *) die "output must end in .gpg so it is obvious the file is encrypted" ;;
esac
[ -e "$OUT" ] && die "refusing to overwrite existing backup: $OUT"
command -v gpg >/dev/null || die "gpg is required"

WORK="$(mktemp -d)"; trap 'shred -u "$WORK"/* 2>/dev/null || true; rm -rf "$WORK"' EXIT
mkdir -p "$WORK/identity"
cp "$KEYSTORE" "$WORK/identity/release.jks"
cp "$PASSWORD_FILE" "$WORK/identity/password.txt"
chmod 600 "$WORK/identity/"*

KEYSTORE_SHA="$(sha256sum "$KEYSTORE" | awk '{print $1}')"
CERT_SHA="$(keytool -list -v -keystore "$KEYSTORE" -storepass "$(cat "$PASSWORD_FILE")" 2>/dev/null \
  | awk '/SHA256:/{print $2; exit}')"
cat > "$WORK/identity/BACKUP_MANIFEST.txt" <<EOF
Kaminari Nfty release signing identity
created_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
keystore_sha256=$KEYSTORE_SHA
certificate_sha256=$CERT_SHA
key_alias=upload
restore=see ops/signing/README.md
EOF

tar -C "$WORK" -czf - identity | gpg --batch --yes --symmetric \
  --cipher-algo AES256 --s2k-mode 3 --s2k-count 65011712 \
  $( [ -n "${KF_BACKUP_PASSPHRASE_FILE:-}" ] && printf '%s' "--passphrase-file ${KF_BACKUP_PASSPHRASE_FILE}" ) \
  --output "$OUT"

# --- verification: decrypt, compare, and prove the keystore opens -------------
VERIFY="$(mktemp -d)"; trap 'shred -u "$VERIFY"/* 2>/dev/null || true; rm -rf "$VERIFY" "$WORK"' EXIT
gpg --batch --yes --decrypt \
  $( [ -n "${KF_BACKUP_PASSPHRASE_FILE:-}" ] && printf '%s' "--passphrase-file ${KF_BACKUP_PASSPHRASE_FILE}" ) \
  --output "$VERIFY/backup.tar.gz" "$OUT" 2>/dev/null
tar -C "$VERIFY" -xzf "$VERIFY/backup.tar.gz"
RESTORED_SHA="$(sha256sum "$VERIFY/identity/release.jks" | awk '{print $1}')"
[ "$RESTORED_SHA" = "$KEYSTORE_SHA" ] || die "restored keystore digest mismatch"
keytool -list -keystore "$VERIFY/identity/release.jks" \
  -storepass "$(cat "$VERIFY/identity/password.txt")" >/dev/null 2>&1 \
  || die "restored keystore does not open with the backed-up password"

printf 'backup written: %s\n' "$OUT"
printf 'archive sha256: %s\n' "$(sha256sum "$OUT" | awk '{print $1}')"
printf 'keystore sha256: %s\n' "$KEYSTORE_SHA"
printf 'certificate sha256: %s\n' "$CERT_SHA"
printf 'verified: decrypt + digest match + keystore opens\n'
printf 'next: store this file offline, in a second location from the keystore itself.\n'
