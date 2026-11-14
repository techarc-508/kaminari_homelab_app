# ntfy permission matrix (generated from contracts/kf_contract.v1.json)

Apply with `ops/ntfy/generate_auth.py --apply`; verify with `ops/verify/ntfy_matrix.py`.

| Identity | Role | Topic permissions |
|---|---|---|
| `kaminari_app` | user | `KAMINARI-FUJI-Homelab` ro, `kaminari-diary` ro, `kf-admin-audit` ro, `kf-backups` ro, `kf-command-request` wo, `kf-command-result` ro, `kf-diary` ro, `kf-digest` ro, `kf-iot` ro, `kf-music-pipeline` ro, `kf-music-reports` ro, `kf-network` ro, `kf-news-reports` ro, `kf-news` ro, `kf-ntfy` ro, `kf-pve-ct` ro, `kf-pve-host` ro, `kf-pve-vm` ro, `kf-security` ro, `kf-services` ro, `kf-stickers-pipeline` ro, `kf-stickers-reports` ro, `kf-storage` ro, `kf-web` ro |
| `kf_dashboard` | user | `KAMINARI-FUJI-Homelab` rw |
| `kf_gateway` | user | `kf-admin-audit` wo, `kf-command-request` ro, `kf-command-result` wo |
| `kf_monitor` | user | `kf-backups` wo, `kf-digest` wo, `kf-network` wo, `kf-ntfy` wo, `kf-pve-ct` wo, `kf-pve-host` wo, `kf-pve-vm` wo, `kf-security` wo, `kf-services` wo, `kf-storage` wo, `kf-web` wo |
| `kf_music` | user | `kf-music-pipeline` wo, `kf-music-reports` wo |
| `kf_news` | user | `kf-news-reports` wo, `kf-news` wo |
| `kf_stickers` | user | `kf-stickers-pipeline` wo, `kf-stickers-reports` wo |
| `kfadmin` | admin | all topics (server-side only, never shipped in the app) |
| anonymous `*` | anonymous | `KAMINARI-FUJI-Homelab` rw, `kaminari-diary` rw, `kaminari-onboarding-test` rw |

Command-result topics (`kf-command-result`, rplab-command-results) have no anonymous row and no `*` rule.
