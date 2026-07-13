#!/usr/bin/env bash
#
# provision-server.sh — automated provisioning of the Climbing production server.
#
# Sets up the WHOLE system layer on a clean Ubuntu (amd64 or ARM/aarch64):
#   - Docker CE + compose plugin (official apt repo, multi-arch)
#   - 2 GB swap (setup-swap.sh)
#   - rclone + cron (for backups)
#   - backup script nsp-backup.sh + 3:00 AM cron
#   - Cloudflare origin firewall (cf-origin-firewall.sh + systemd service)
#   - weekly Docker image cleanup
#
# Does NOT touch secrets or data — those steps (manual, from a safe source) are
# printed at the end. See SERVER-SETUP.md for the full runbook (incl. disaster recovery).
#
# Usage (on the target server, from the hub/ directory):
#   sudo bash provision-server.sh
#
# Idempotent — safe to run again.

set -euo pipefail

# Script directory (sibling files: setup-swap.sh, nsp-backup.sh, cf-origin-firewall.*)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Non-root user to be added to the docker group (SUDO_USER when run via sudo)
TARGET_USER="${SUDO_USER:-ubuntu}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "This script requires root. Run: sudo bash provision-server.sh" >&2
  exit 1
fi

log() { echo -e "\n==> $*"; }

# ---------------------------------------------------------------------------
log "1/7 apt update + base packages (rclone, cron, iptables-persistent...)"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
# iptables-persistent asks interactively about saving rules — preseed "no", because
# the CF firewall is reapplied via the systemd service on every boot anyway.
echo "iptables-persistent iptables-persistent/autosave_v4 boolean false" | debconf-set-selections
echo "iptables-persistent iptables-persistent/autosave_v6 boolean false" | debconf-set-selections
apt-get install -y -qq ca-certificates curl gnupg rclone cron iptables-persistent

# ---------------------------------------------------------------------------
log "2/7 Docker CE (official apt repo — multi-arch: $(dpkg --print-architecture))"
if ! command -v docker >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
else
  echo "    Docker already installed — skipping."
fi
docker --version; docker compose version

# ---------------------------------------------------------------------------
log "3/7 user ${TARGET_USER} -> docker group"
usermod -aG docker "${TARGET_USER}"
echo "    (takes effect after a new SSH login)"

# ---------------------------------------------------------------------------
log "4/7 swap 2 GB (setup-swap.sh)"
bash "${SCRIPT_DIR}/setup-swap.sh"

# ---------------------------------------------------------------------------
log "5/7 backup script + 3:00 AM cron"
install -m 0755 "${SCRIPT_DIR}/nsp-backup.sh" /usr/local/bin/nsp-backup.sh
mkdir -p /backups/db /backups/files
# root cron: daily backup at 3:00 AM (idempotent)
( crontab -l 2>/dev/null | grep -v "nsp-backup.sh"; echo "0 3 * * * /usr/local/bin/nsp-backup.sh" ) | crontab -
systemctl enable --now cron
echo "    backup: /usr/local/bin/nsp-backup.sh, cron 0 3 * * *"

# ---------------------------------------------------------------------------
log "6/7 Cloudflare origin firewall (script + systemd service)"
install -m 0755 "${SCRIPT_DIR}/cf-origin-firewall.sh" /usr/local/sbin/cf-origin-firewall.sh
install -m 0644 "${SCRIPT_DIR}/cf-origin-firewall.service" /etc/systemd/system/cf-origin-firewall.service
systemctl daemon-reload
systemctl enable cf-origin-firewall.service
# start only once docker + containers are up (the DOCKER-USER chain must exist).
# The script itself waits up to 60 s for the chain; if docker is already running, start now:
if docker info >/dev/null 2>&1; then
  systemctl start cf-origin-firewall.service || echo "    (start deferred — run once the stack is up: sudo systemctl start cf-origin-firewall)"
fi
echo "    firewall: 443 Cloudflare-only, 81 blocked, 80 open (cert renewal)"

# ---------------------------------------------------------------------------
log "7/7 weekly Docker image cleanup (user ${TARGET_USER})"
sudo -u "${TARGET_USER}" bash -c '( crontab -l 2>/dev/null | grep -v "image prune"; echo "30 3 * * 0 docker image prune -a -f --filter \"until=168h\"" ) | crontab -'

# ---------------------------------------------------------------------------
cat <<'EOF'

============================================================================
 SYSTEM LAYER READY. Remaining MANUAL STEPS (secrets + data — NOT in git):
============================================================================
  1. ~/nsp-app/            -> docker-compose.prod.yml (from the hub/ repo) + .env (from the password manager)
  2. ~/nginx-proxy-manager/ -> compose + data/ + letsencrypt/ (from the old server or re-issue)
  3. rclone config         -> /root/.config/rclone/rclone.conf (GDrive token + crypt passwords)
  4. backup seed           -> sudo rclone copy gdrive-crypt: /backups   (BEFORE the first sync!)
  5. bring up NPM (first):   cd ~/nginx-proxy-manager && docker compose up -d
  6. bring up the app:       cd ~/nsp-app && docker compose -f docker-compose.prod.yml up -d
  7. data:                   pg_dump -> psql (database) + tar of the uploads_data_prod volume
  8. firewall (if deferred): sudo systemctl start cf-origin-firewall
  Full runbook: SERVER-SETUP.md
============================================================================
EOF
