# Kaminari Proxmox Gateway — Proxmox ACL Setup Guide

This guide explains how to create a least-privilege Proxmox service account for
the gateway.

**This is owner-executed administrative documentation.** The commands below
modify Proxmox access control. They are shown as examples with placeholders and
must be executed by the owner against the real Proxmox cluster.

Do not use the Proxmox root account as the gateway identity.
Do not grant broad permissions by default.
Do not enable mutations unless there is a documented operational need.

## 1. Create a dedicated service user

Create a user dedicated to the gateway, for example:

pveum useradd kaminari-gateway@pve --password REALLY_STRONG_PASSWORD

If your environment uses a different realm, adjust `kaminari-gateway@pve`
accordingly. Keep the realm consistent with the rest of your Proxmox identity
setup.

Record the password securely. It is only used to create API tokens, not for
interactive login.

## 2. Create a scoped API token

Create an API token for the gateway:

pveum api-token create kaminari-gateway@pve --purpose kaminari-gateway \
  --token-id kaminari-gateway-token

The output includes `username` and `token`. The full API token string has the
form:

USERNAME@REALM!TOKEN_ID=TOKEN secret

Store that string securely. It is the value of `GATEWAY_PROXMOX_API_TOKEN` in
the deployed gateway configuration.

This token is the credentials the gateway uses to talk to Proxmox. It is not a
gateway auth token. The Android client authenticates to the gateway with
`GATEWAY_GATEWAY_TOKEN`.

## 3. Assign read-only ACL permissions

The gateway currently implements read-only Proxmox operations. Grant only the
permissions needed for those operations.

Example read-only ACL for nodes, VMs, containers, storage, and tasks:

pveum aclmod / -group kaminari-gateway-readonly -role PVEAuditor

or, if you prefer a user-level ACL with a more constrained custom role:

pveum aclmod / -user kaminari-gateway@pve -role kaminari-gateway-ro

If you create a custom role, include only the following kinds of read access:

- Node listing
- VM listing on a node
- Container listing on a node
- VM status on a node/VM
- Container status on a node/VM
- Storage listing on a node
- Task history on a node

Do not grant write, stop, start, or migration permissions in the read-only role.

## 4. Scope permissions to the intended resources

If the gateway should only access specific nodes, scope the ACL to those nodes
instead of the root `/`. For example:

pveum aclmod /nodes/NODE1 -user kaminari-gateway@pve -role kaminari-gateway-ro
pveum aclmod /nodes/NODE2 -user kaminari-gateway@pve -role kaminari-gateway-ro

If the gateway does not need access to every node, do not grant it cluster-wide
access.

## 5. Separate mutation permissions

Mutations are disabled in the gateway by default. If a future phase needs them,
create a separate mutation role and assign it only where explicitly required.

Example mutation privilege set for VM and container lifecycle operations:

- PVEVMAdmin-style lifecycle actions on specific VMs/containers, scoped tightly
- No shell access
- No storage management unless explicitly required
- No cluster configuration changes
- No user/role administration permissions for the gateway identity

Grant mutation permissions only to the extent required and only after the
gateway mutation path has been reviewed for the intended use case.

## 6. Do not assign broad roles

Avoid roles like Administrator for the gateway identity. Use the narrowest role
that covers the implemented read-only operations.

If you are unsure which Proxmox permissions map to the implemented endpoints,
start with a conservative role and expand only after testing each endpoint
against the real Proxmox API.

## 7. Token expiration and rotation

API tokens can be rotated without changing the gateway user account. If a token
is compromised or needs rotation:

1. Create a new token for the same user.
2. Update `GATEWAY_PROXMOX_API_TOKEN` in the deployed .env.
3. Restart the gateway.
4. Remove or disable the old token after confirmation.

Do not embed the token in the Android app or commit it to Git.

## 8. Revocation procedure

If the gateway must be decommissioned or its access revoked:

1. Remove or disable the API token.
2. Remove the user or ACL entries as appropriate.
3. Verify Proxmox logs/audit to confirm the gateway stopped making requests.

## 9. Post-setup verification

After setup, verify with the gateway test suite and the real Proxmox API before
enabling any mutation path:

- Node list works
- VM list works for the intended node
- Container list works for the intended node
- VM status works for an intended VMID
- Container status works for an intended VMID
- Storage summary works for the intended node
- Task history works for the intended node

Do not enable mutations until the read-only path is confirmed and the mutation
use case is documented.
