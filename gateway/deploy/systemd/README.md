# Kaminari Proxmox Gateway — systemd setup notes

The provided unit file is a template:
`deploy/systemd/kaminari-gateway.service`.

## Template assumptions

- The gateway is installed under `/opt/kaminari-gateway`.
- The virtual environment is at `/opt/kaminari-gateway/.venv`.
- Configuration is deployed to `/etc/kaminari-gateway/.env`.
- A dedicated non-root service user and group are created by the owner.

## What the owner must adjust

- `User` and `Group`
- `WorkingDirectory`
- `EnvironmentFile`
- `ExecStart` paths if the installation layout differs
- Logging, log rotation, and log directory permissions if changed
- Resource limits if the environment requires different values

## Install steps (documented, not automated here)

1. Create the service user and group.
2. Deploy the application code to the chosen directory.
3. Create the Python virtual environment and install dependencies.
4. Deploy the configured `.env` file to the intended path with restricted
   permissions.
5. Install the systemd unit file.
6. Reload systemd, enable and start the service.
7. Validate configuration with `deploy/scripts/validate-config.sh`.
8. Check `health-check.sh` from an appropriate internal host.

## Restart and shutdown behavior

The unit is configured to restart on failure with a short backoff and to allow
a brief graceful shutdown window. This is intended to support controlled
upgrades and rollback by restarting with a previous code/config snapshot.

## Do not run as root

The gateway should not run as root. It does not need privileged access to
Proxmox. The Proxmox API token is a normal service-account credential.

## Logs

stdout/stderr are directed to the journal. Review logs with the system journal
or the configured log targets. The gateway logs audit events without secrets
under normal operation.
