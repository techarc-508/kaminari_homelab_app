"""Typed gateway errors.

The app needs stable machine-readable codes, not provider-specific exception
strings. Provider secrets and raw PVE payloads must never be copied into a
client-facing message.
"""
from __future__ import annotations


class GatewayError(Exception):
    def __init__(self, code: str, message: str, status_code: int = 400):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code
