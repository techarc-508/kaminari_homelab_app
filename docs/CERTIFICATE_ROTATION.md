# Certificate rotation

The app trusts two different things and they rotate differently. Nothing here
involves pinning a short-lived leaf certificate.

## 1. Gateway certificate (private CA, host-scoped)

| Item | Value |
|---|---|
| CA | `Kaminari Gateway Private CA`, key in `~/.kaminari-pki` (mode 700, offline, never in Git) |
| CA validity | to 2029-02-13 |
| Leaf | `gateway-combined.pem` in `/etc/kaminari-gateway-tls/` on the PVE host, validity to 2027-02-13 |
| Hosts covered | `100.82.207.40` (tailnet), LAN address of the gateway route |
| App trust | `res/raw/kf_gateway_ca.pem`, trusted **only** for `100.82.207.40` via `domain-config` |
| Guard test | `NetworkSecurityConfigTest` fails if the anchor moves to `base-config` |

Annual leaf renewal (CA unchanged, no app rebuild needed):

```bash
# on the workstation that holds ~/.kaminari-pki
cd ~/.kaminari-pki
openssl req -new -key gateway.key -out gateway.csr \
  -subj "/CN=kaminari-gateway/O=RPLab"
openssl x509 -req -in gateway.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out gateway.crt -days 365 \
  -extfile <(printf "subjectAltName=IP:100.82.207.40,DNS:kaminari-gateway\n")
cat gateway.crt ca.crt > gateway-chain.pem
```

Install on the PVE host, then restart only the TLS listener:

```bash
install -m 600 gateway-chain.pem /etc/kaminari-gateway-tls/gateway-combined.pem
systemctl restart kf-gateway-tls.service
python3 /tmp/kf_route_matrix.py https://100.82.207.40:8443 /etc/kaminari-gateway-tls/ca.crt
```

CA rotation (every few years, **requires an app rebuild**):

1. Generate the new CA and leaf; keep the old CA file too.
2. Ship **both** anchors: add `res/raw/kf_gateway_ca_next.pem` and a second
   `<certificates>` entry in the gateway `domain-config`, bump `versionCode`,
   rebuild, sign, install.
3. Only after the new app is installed, swap the certificate on the route.
4. Remove the old anchor in the following release.

Certificates must never be rotated in the opposite order: the app would reject
the endpoint and the owner would lose gateway access until a new APK is built.

## 2. ntfy certificate (public, automatic)

`ntfy.rplab.qzz.io` is fronted by Cloudflare; the certificate is a Cloudflare
Universal certificate (`CN=rplab.qzz.io`, SAN `*.rplab.qzz.io`, issuer Google
Trust Services `WE1`), renewed automatically before expiry (current expiry
2026-12-25). Nothing to do at renewal time.

Verify after any Cloudflare/DNS change:

```bash
echo | openssl s_client -connect ntfy.rplab.qzz.io:443 -servername ntfy.rplab.qzz.io \
  2>/dev/null | openssl x509 -noout -subject -dates -ext subjectAltName
curl -s -o /dev/null -w '%{http_code}\n' -A okhttp/4.12.0 https://ntfy.rplab.qzz.io/v1/health
```

The SAN must still cover `ntfy.rplab.qzz.io` (a single label under the wildcard).
If Cloudflare switches to a per-host certificate, verify the SAN again — a
matching common name alone is not sufficient.
