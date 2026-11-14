"""RFC 6238 TOTP (SHA-1, 6 digits, 30-second period).

Implemented with the standard library only so the gateway keeps a small
dependency surface and the algorithm is testable against the RFC 4226/6238
test vectors. Secrets are 160-bit base32 values, compatible with standard
authenticator applications.
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import secrets
import struct
import time
from datetime import datetime, timezone
from typing import Optional
from urllib.parse import quote, urlencode

PERIOD_SECONDS = 30
DIGITS = 6
SECRET_BYTES = 20


def generate_secret() -> str:
    """Return a new base32 TOTP secret (no padding)."""
    return base64.b32encode(secrets.token_bytes(SECRET_BYTES)).decode("ascii").rstrip("=")


def _decode_secret(secret_b32: str) -> bytes:
    normalized = secret_b32.strip().replace(" ", "").upper()
    padding = "=" * (-len(normalized) % 8)
    try:
        return base64.b32decode(normalized + padding, casefold=True)
    except Exception as exc:  # noqa: BLE001 - surfaced as a typed error
        raise ValueError("Invalid TOTP secret") from exc


def _hotp(key: bytes, counter: int, digits: int = DIGITS) -> str:
    message = struct.pack(">Q", counter)
    digest = hmac.new(key, message, hashlib.sha1).digest()
    offset = digest[-1] & 0x0F
    binary = struct.unpack(">I", digest[offset : offset + 4])[0] & 0x7FFFFFFF
    return str(binary % (10**digits)).zfill(digits)


def step_for(now: float, period: int = PERIOD_SECONDS) -> int:
    return int(now // period)


def code_for_step(secret_b32: str, step: int, digits: int = DIGITS) -> str:
    return _hotp(_decode_secret(secret_b32), step, digits)


def code_at(secret_b32: str, now: Optional[float] = None, digits: int = DIGITS) -> str:
    return code_for_step(secret_b32, step_for(now if now is not None else time.time()), digits)


def verify(
    secret_b32: str,
    code: str,
    now: Optional[float] = None,
    *,
    window_steps: int = 1,
    digits: int = DIGITS,
) -> Optional[int]:
    """Return the matched step for a valid code, otherwise None.

    A small symmetric window absorbs clock drift. The caller is responsible for
    refusing a step it has already accepted (replay protection).
    """
    candidate = (code or "").strip()
    if not candidate.isdigit() or len(candidate) != digits:
        return None
    current = step_for(now if now is not None else time.time())
    for delta in range(-window_steps, window_steps + 1):
        step = current + delta
        if step < 0:
            continue
        if hmac.compare_digest(code_for_step(secret_b32, step, digits), candidate):
            return step
    return None


def provisioning_uri(
    secret_b32: str,
    *,
    account: str,
    issuer: str = "Kaminari",
) -> str:
    label = quote(f"{issuer}:{account}", safe="")
    query = urlencode(
        {
            "secret": secret_b32,
            "issuer": issuer,
            "algorithm": "SHA1",
            "digits": DIGITS,
            "period": PERIOD_SECONDS,
        }
    )
    return f"otpauth://totp/{label}?{query}"


def now_iso(now: Optional[float] = None) -> str:
    stamp = now if now is not None else time.time()
    return datetime.fromtimestamp(stamp, tz=timezone.utc).isoformat().replace("+00:00", "Z")
