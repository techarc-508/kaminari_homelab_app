# Release signing identity

The Kaminari Nfty release APK is signed with `upload` in
`~/.kaminari-signing/release.jks`. The keystore, its password file and the PKI
for the private gateway CA all live outside the repository and are never
committed.

| Item | Location | Mode |
|---|---|---|
| Keystore | `~/.kaminari-signing/release.jks` | 600 |
| Store/key password | `~/.kaminari-signing/password.txt` | 600 |
| Directory | `~/.kaminari-signing/` | 700 |
| Private CA (key + cert) | `~/.kaminari-pki/` | 700 |

Certificate: `CN=Kaminari Nfty, OU=RPLab, O=RPLab, C=IN`,
SHA-256 `a8a88887ddb0ad5f0045806860670c6c08481fe8a95ebcdbbaa05846a61910b8`.

## Taking an encrypted backup

```bash
# passphrase in a 600 file, or omit the variable and type it twice
KF_BACKUP_PASSPHRASE_FILE=~/.kaminari-signing/backup-passphrase \
  ops/signing/backup-signing-key.sh /run/media/offline/kaminari-signing-$(date +%F).tar.gz.gpg
```

The script refuses to overwrite an existing archive, encrypts with AES-256
(`gpg --symmetric`, 650k S2K iterations), then decrypts its own output into a
temporary directory, compares the keystore SHA-256 and opens the restored
keystore with `keytool`. A backup is only reported when all three checks pass.
Secrets are never echoed and the temporary directories are shredded.

Keep the passphrase somewhere separate from the archive, and keep at least two
archives in two physical locations. Losing this identity means future builds can
never update an installed release APK.

## Restoring

```bash
mkdir -p ~/.kaminari-signing && chmod 700 ~/.kaminari-signing
gpg --decrypt kaminari-signing-YYYY-MM-DD.tar.gz.gpg | tar -C /tmp -xzf -
mv /tmp/identity/release.jks /tmp/identity/password.txt ~/.kaminari-signing/
chmod 600 ~/.kaminari-signing/release.jks ~/.kaminari-signing/password.txt
keytool -list -keystore ~/.kaminari-signing/release.jks -storepass "$(cat ~/.kaminari-signing/password.txt)"
```

## Building a signed release

```bash
KEYSTORE_PATH="$HOME/.kaminari-signing/release.jks" \
STORE_PASSWORD="$(cat ~/.kaminari-signing/password.txt)" \
KEY_PASSWORD="$(cat ~/.kaminari-signing/password.txt)" \
./gradlew :app:assembleRelease
```

Verify before publishing:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```
