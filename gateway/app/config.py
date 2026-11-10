from pydantic import HttpUrl, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="GATEWAY_", case_sensitive=False)

    gateway_token: str
    proxmox_base_url: HttpUrl
    proxmox_api_token: str
    proxmox_verify_tls: bool = True
    proxmox_timeout_seconds: float = 10.0
    mutations_enabled: bool = False
    rate_limit_per_minute: int = 60
    max_request_bytes: int = 65536

    @field_validator("proxmox_base_url")
    @classmethod
    def require_https(cls, value: HttpUrl) -> HttpUrl:
        if value.scheme != "https":
            raise ValueError("Proxmox base URL must use https://")
        return value
