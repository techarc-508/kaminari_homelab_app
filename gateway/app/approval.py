"""Approval / step-up interface.

The pre-Phase-2 nonce-echo mechanism has been removed: it was possession of the
bearer token, not a second factor. Every mutation now goes through a
`StepUpVerifier`; `TotpStepUpService` is the only production implementation.
"""
from __future__ import annotations

from typing import Any, Mapping, Optional, Protocol

from .errors import GatewayError


class StepUpVerifier(Protocol):
    is_step_up: bool

    def available(self) -> bool: ...

    def verify(
        self, approval: Optional[Mapping[str, Any]], envelope: Mapping[str, Any]
    ) -> None: ...


class UnavailableStepUpVerifier:
    """Truthful default: no verifier, so every mutation is refused."""

    is_step_up = False

    def available(self) -> bool:
        return False

    def verify(
        self, approval: Optional[Mapping[str, Any]], envelope: Mapping[str, Any]
    ) -> None:
        raise GatewayError(
            "step_up_unavailable",
            "Step-up authentication is not available on this gateway",
            403,
        )
