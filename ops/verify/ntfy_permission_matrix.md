# ntfy permission matrix

Before: no auth-file, `enable_login: false`, anonymous publish accepted on any topic.

After: `auth-file` + `auth-default-access: deny-all`.

| Identity | Access |
|---|---|
| `kfadmin` | admin (all topics, server-side only) |
| `kaminari_app` | write: `kf-command-request`; read: `kf-admin-audit`, `kf-backups`, `kf-command-result`, `kf-diary`, `kf-digest`, `kf-iot`, `kf-music-pipeline`, `kf-music-reports`, `kf-network`, `kf-news`, `kf-news-reports`, `kf-ntfy`, `kf-pve-ct`, `kf-pve-host`, `kf-pve-vm`, `kf-security`, `kf-services`, `kf-stickers-pipeline`, `kf-stickers-reports`, `kf-storage`, `kf-web` |
| `kf_gateway` | write: `kf-admin-audit`, `kf-command-result`; read: `kf-command-request` |
| `kf_monitor` | write: `kf-backups`, `kf-digest`, `kf-network`, `kf-ntfy`, `kf-pve-ct`, `kf-pve-host`, `kf-pve-vm`, `kf-security`, `kf-services`, `kf-storage`, `kf-web` |
| `kf_music` | write: `kf-music-pipeline`, `kf-music-reports` |
| `kf_stickers` | write: `kf-stickers-pipeline`, `kf-stickers-reports` |
| `kf_news` | write: `kf-news`, `kf-news-reports` |
| anonymous `*` | rw: `KAMINARI-FUJI-Homelab`, `kaminari-diary`, `kaminari-onboarding-test`, `kf-command-result`, `rplab-command-results` |
