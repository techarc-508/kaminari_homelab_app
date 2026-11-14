"""RFC 6238 test vectors (SHA-1, 8 digits) and secret handling."""

import base64

from app import totp

# ASCII secret "12345678901234567890" from RFC 6238 Appendix B.
RFC_SECRET = base64.b32encode(b"12345678901234567890").decode("ascii")
VECTORS = [
    (59, "94287082"),
    (1111111109, "07081804"),
    (1111111111, "14050471"),
    (1234567890, "89005924"),
    (2000000000, "69279037"),
    (20000000000, "65353130"),
]


def test_rfc6238_vectors():
    for timestamp, expected in VECTORS:
        step = totp.step_for(timestamp)
        assert totp.code_for_step(RFC_SECRET, step, digits=8) == expected


def test_verify_accepts_the_current_step():
    now = 1_800_000_000.0
    code = totp.code_at(RFC_SECRET, now)
    assert totp.verify(RFC_SECRET, code, now) == totp.step_for(now)


def test_verify_accepts_one_step_of_drift_only():
    now = 1_800_000_000.0
    step = totp.step_for(now)
    previous = totp.code_for_step(RFC_SECRET, step - 1)
    assert totp.verify(RFC_SECRET, previous, now, window_steps=1) == step - 1
    stale = totp.code_for_step(RFC_SECRET, step - 5)
    assert totp.verify(RFC_SECRET, stale, now, window_steps=1) is None


def test_verify_rejects_malformed_codes():
    now = 1_800_000_000.0
    assert totp.verify(RFC_SECRET, "abcdef", now) is None
    assert totp.verify(RFC_SECRET, "", now) is None
    assert totp.verify(RFC_SECRET, "12345", now) is None
    assert totp.verify(RFC_SECRET, "1234567", now) is None


def test_generated_secrets_are_unique_and_valid():
    secrets = {totp.generate_secret() for _ in range(20)}
    assert len(secrets) == 20
    now = 1_800_000_000.0
    for secret in secrets:
        assert totp.verify(secret, totp.code_at(secret, now), now) is not None


def test_provisioning_uri_is_standard():
    secret = "JBSWY3DPEHPK3PXP"
    uri = totp.provisioning_uri(secret, account="kfadmin", issuer="Kaminari")
    assert uri.startswith("otpauth://totp/")
    assert f"secret={secret}" in uri
    assert "issuer=Kaminari" in uri
    assert "digits=6" in uri
    assert "period=30" in uri
