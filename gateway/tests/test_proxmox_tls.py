"""TLS verification for the Proxmox API connection.

The gateway used to disable verification (`GATEWAY_PROXMOX_VERIFY_TLS=false`)
because the PVE node presents a cluster-signed certificate. These tests pin the
replacement behaviour: the cluster CA path is the only trust anchor, and a bad
or missing CA is a configuration error rather than a silent downgrade.
"""
from __future__ import annotations

from pathlib import Path

import pytest
from pydantic import ValidationError
from app.config import Settings
from app.proxmox import ProxmoxClient

CA_PEM = """-----BEGIN CERTIFICATE-----
MIIBszCCAVmgAwIBAgIUdGVzdC1jYQ==
-----END CERTIFICATE-----
"""


def make_settings(ca_cert: str | None, verify_tls: bool = True) -> Settings:
    return Settings(
        gateway_token="test-gateway-token",
        proxmox_base_url="https://192.168.0.113:8006/api2/json",
        proxmox_api_token="kaminari-read@pve!gateway=secret",
        proxmox_verify_tls=verify_tls,
        proxmox_ca_cert=ca_cert,
    )


def test_pinned_ca_path_is_the_trust_anchor(tmp_path: Path) -> None:
    ca = tmp_path / "pve-root-ca.pem"
    ca.write_text(CA_PEM)
    settings = make_settings(str(ca))
    assert settings.proxmox_tls_verify() == str(ca)


def test_missing_ca_file_is_rejected(tmp_path: Path) -> None:
    with pytest.raises(ValidationError):
        make_settings(str(tmp_path / "absent.pem"))


def test_non_pem_ca_file_is_rejected(tmp_path: Path) -> None:
    ca = tmp_path / "not-a-cert.pem"
    ca.write_text("this is not a certificate")
    with pytest.raises(ValidationError):
        make_settings(str(ca))


def test_client_without_ca_keeps_the_verification_flag() -> None:
    assert ProxmoxClient("https://pve:8006", "token", verify_tls=True).tls_verify is True
    assert ProxmoxClient("https://pve:8006", "token", verify_tls=False).tls_verify is False


def test_client_prefers_ca_over_flag(tmp_path: Path) -> None:
    ca = tmp_path / "pve-root-ca.pem"
    ca.write_text(CA_PEM)
    client = ProxmoxClient("https://pve:8006", "token", verify_tls=False, ca_cert=str(ca))
    assert client.tls_verify == str(ca)


def test_verification_is_on_by_default() -> None:
    settings = Settings(
        gateway_token="test-gateway-token",
        proxmox_base_url="https://192.168.0.113:8006/api2/json",
        proxmox_api_token="kaminari-read@pve!gateway=secret",
    )
    assert settings.proxmox_verify_tls is True
    assert settings.proxmox_tls_verify() is True
