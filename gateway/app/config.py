from typing import Optional

from pydantic import HttpUrl, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="GATEWAY_", case_sensitive=False)

    # Least-privilege gateway credential (identity: `kf_gateway`).
    gateway_token: str
    previous_gateway_token: Optional[str] = None
    token_expires_at: Optional[str] = None

    # Separate owner credential for TOTP enrollment and audit reads.
    # Must never be the same value as the gateway token.
    owner_token: Optional[str] = None
    previous_owner_token: Optional[str] = None
    owner_token_expires_at: Optional[str] = None
    owner_identity: str = "kfadmin"

    gateway_identity: str = "kf_gateway"

    proxmox_base_url: HttpUrl
    proxmox_api_token: str
    proxmox_verify_tls: bool = True
    proxmox_timeout_seconds: float = 10.0

    # Mutations stay disabled unless explicitly enabled *and* step-up is enrolled.
    mutations_enabled: bool = False

    rate_limit_per_minute: int = 60
    max_request_bytes: int = 65536

    # Durable state. Must be a persistent path the container mounts a volume on.
    db_path: str = "kaminari-gateway.db"
    step_up_challenge_ttl_seconds: int = 60
    step_up_window_steps: int = 1
    step_up_max_attempts: int = 5

    @field_validator("proxmox_base_url")
    @classmethod
    def require_https(cls, value: HttpUrl) -> HttpUrl:
        if value.scheme != "https":
            raise ValueError("Proxmox base URL must use https://")
        return value

    @field_validator("gateway_token")
    @classmethod
    def reject_placeholder_token(cls, value: str) -> str:
        if len(value.strip()) < 16 or value.startswith("REPLACE_WITH"):
            raise ValueError("gateway token must be a real random value of at least 16 characters")
        return value

    @field_validator("owner_token")
    @classmethod
    def reject_placeholder_owner_token(cls, value: Optional[str]) -> Optional[str]:
        if value is not None and (len(value.strip()) < 16 or value.startswith("REPLACE_WITH")):
            raise ValueError("owner token must be a real random value of at least 16 characters")
        return value

    def check_token_separation(self) -> None:
        """The owner credential must be distinct from the gateway credential."""
        if not self.owner_token:
            return
        if self.owner_token == self.gateway_token:
            raise ValueError("owner token must differ from the gateway token")
