import hmac
import time
from collections import defaultdict, deque
from datetime import datetime, timezone
from typing import Optional

from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

security = HTTPBearer(auto_error=True)


def verify_gateway_token(provided: str, *expected: Optional[str]) -> bool:
    """Constant-time comparison against one or more accepted tokens.

    More than one value allows a rotation window: the new token and the previous
    token are both accepted until the old one is removed from configuration.
    """
    if not provided:
        return False
    candidate = provided.encode("utf-8")
    matched = False
    for value in expected:
        if not value:
            continue
        if hmac.compare_digest(candidate, value.encode("utf-8")):
            matched = True
    return matched


def token_expired(expires_at: Optional[str], now: Optional[float] = None) -> bool:
    """Fail closed: an unparsable expiry is treated as expired."""
    if not expires_at:
        return False
    try:
        parsed = datetime.fromisoformat(expires_at.replace("Z", "+00:00"))
    except ValueError:
        return True
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return (now if now is not None else time.time()) >= parsed.timestamp()


class RateLimiter:
    def __init__(self, per_minute: int):
        self.per_minute = max(1, per_minute)
        self._events: dict[str, deque[float]] = defaultdict(deque)

    def check(self, key: str) -> bool:
        now = time.monotonic()
        window = self._events[key]
        while window and now - window[0] > 60.0:
            window.popleft()
        if len(window) >= self.per_minute:
            return False
        window.append(now)
        return True
