import hmac
import time
from typing import Any, Dict, Optional
from urllib.parse import urljoin

import httpx


class ProxmoxError(Exception):
    def __init__(self, status_code: int, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.message = message


class ProxmoxClient:
    def __init__(
        self,
        base_url: str,
        api_token: str,
        verify_tls: bool = True,
        timeout_seconds: float = 10.0,
    ):
        self.base_url = base_url.rstrip("/") + "/"
        self.api_token = api_token
        self.verify_tls = verify_tls
        self.timeout_seconds = timeout_seconds

    def _headers(self) -> Dict[str, str]:
        return {"Authorization": f"PVEAPIToken={self.api_token}"}

    async def request(
        self,
        method: str,
        path: str,
        params: Optional[Dict[str, Any]] = None,
    ) -> Any:
        url = urljoin(self.base_url, path.lstrip("/"))
        async with httpx.AsyncClient(
            verify=self.verify_tls,
            timeout=self.timeout_seconds,
        ) as client:
            response = await client.request(
                method,
                url,
                headers=self._headers(),
                params=params,
            )
        if response.status_code >= 400:
            raise ProxmoxError(
                response.status_code,
                f"Proxmox request failed with HTTP {response.status_code}",
            )
        payload = response.json()
        if isinstance(payload, dict) and "data" in payload:
            return payload["data"]
        return payload

    async def get(self, path: str, params: Optional[Dict[str, Any]] = None) -> Any:
        return await self.request("GET", path, params=params)

    async def post(self, path: str, params: Optional[Dict[str, Any]] = None) -> Any:
        return await self.request("POST", path, params=params)

    @staticmethod
    def redact_token(value: str) -> str:
        if not value or len(value) < 8:
            return "***"
        return value[:4] + "..." + value[-4:]
