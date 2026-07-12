#!/usr/bin/env bash
#
# provision-server.sh — automatyczny provisioning serwera produkcyjnego Climbing.
#
# Stawia CALA warstwe systemowa na czystym Ubuntu (amd64 lub ARM/aarch64):
#   - Docker CE + compose plugin (oficjalne repo apt, multi-arch)
#   - swap 2 GB (setup-swap.sh)
#   - rclone + cron (do backupu)
#   - skrypt backupu nsp-backup.sh + cron 3:00
#   - firewall origin Cloudflare (cf-origin-firewall.sh + usluga systemd)
#   - cotygodniowe sprzatanie obrazow Docker
#
# NIE dotyka sekretow ani danych — te kroki (recznie, z bezpiecznego zrodla) sa
# wypisane na koncu. Patrz SERVER-SETUP.md po pelny runbook (w tym disaster recovery).
#
# Uzycie (na docelowym serwerze, z katalogu hub/):
#   sudo bash provision-server.sh
#
# Idempotentny — mozna bezpiecznie uruchomic ponownie.

set -euo pipefail

# Katalog skryptu (pliki-siostry: setup-swap.sh, nsp-backup.sh, cf-origin-firewall.*)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Uzytkownik nie-root, ktory ma trafic do grupy docker (SUDO_USER gdy odpalone przez sudo)
TARGET_USER="${SUDO_USER:-ubuntu}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Ten skrypt wymaga root. Uruchom: sudo bash provision-server.sh" >&2
  exit 1
fi

log() { echo -e "\n==> $*"; }

# ---------------------------------------------------------------------------
log "1/7 apt update + pakiety bazowe (rclone, cron, iptables-persistent...)"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
# iptables-persistent pyta interaktywnie o zapis regul — preseed na "nie", bo
# firewall CF i tak reaplikuje sie przez usluge systemd przy kazdym boocie.
echo "iptables-persistent iptables-persistent/autosave_v4 boolean false" | debconf-set-selections
echo "iptables-persistent iptables-persistent/autosave_v6 boolean false" | debconf-set-selections
apt-get install -y -qq ca-certificates curl gnupg rclone cron iptables-persistent

# ---------------------------------------------------------------------------
log "2/7 Docker CE (oficjalne repo apt — multi-arch: $(dpkg --print-architecture))"
if ! command -v docker >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
else
  echo "    Docker juz zainstalowany — pomijam."
fi
docker --version; docker compose version

# ---------------------------------------------------------------------------
log "3/7 uzytkownik ${TARGET_USER} -> grupa docker"
usermod -aG docker "${TARGET_USER}"
echo "    (zadziala po nowym logowaniu SSH)"

# ---------------------------------------------------------------------------
log "4/7 swap 2 GB (setup-swap.sh)"
bash "${SCRIPT_DIR}/setup-swap.sh"

# ---------------------------------------------------------------------------
log "5/7 skrypt backupu + cron 3:00"
install -m 0755 "${SCRIPT_DIR}/nsp-backup.sh" /usr/local/bin/nsp-backup.sh
mkdir -p /backups/db /backups/files
# root cron: backup codziennie 3:00 (idempotentnie)
( crontab -l 2>/dev/null | grep -v "nsp-backup.sh"; echo "0 3 * * * /usr/local/bin/nsp-backup.sh" ) | crontab -
systemctl enable --now cron
echo "    backup: /usr/local/bin/nsp-backup.sh, cron 0 3 * * *"

# ---------------------------------------------------------------------------
log "6/7 firewall origin Cloudflare (skrypt + usluga systemd)"
install -m 0755 "${SCRIPT_DIR}/cf-origin-firewall.sh" /usr/local/sbin/cf-origin-firewall.sh
install -m 0644 "${SCRIPT_DIR}/cf-origin-firewall.service" /etc/systemd/system/cf-origin-firewall.service
systemctl daemon-reload
systemctl enable cf-origin-firewall.service
# start dopiero gdy docker + kontenery stoja (chain DOCKER-USER musi istniec).
# Skrypt sam czeka do 60 s na chain; jesli docker juz dziala, mozna od razu:
if docker info >/dev/null 2>&1; then
  systemctl start cf-origin-firewall.service || echo "    (start odlozony — uruchom po wstaniu stacku: sudo systemctl start cf-origin-firewall)"
fi
echo "    firewall: 443 tylko Cloudflare, 81 zablokowany, 80 otwarty (renewal certow)"

# ---------------------------------------------------------------------------
log "7/7 cotygodniowe sprzatanie obrazow Docker (uzytkownik ${TARGET_USER})"
sudo -u "${TARGET_USER}" bash -c '( crontab -l 2>/dev/null | grep -v "image prune"; echo "30 3 * * 0 docker image prune -a -f --filter \"until=168h\"" ) | crontab -'

# ---------------------------------------------------------------------------
cat <<'EOF'

============================================================================
 WARSTWA SYSTEMOWA GOTOWA. Pozostaly KROKI RECZNE (sekrety + dane — NIE w git):
============================================================================
  1. ~/nsp-app/            -> docker-compose.prod.yml (z repo hub/) + .env (z managera hasel)
  2. ~/nginx-proxy-manager/ -> compose + data/ + letsencrypt/ (ze starego serwera lub re-issue)
  3. rclone config         -> /root/.config/rclone/rclone.conf (token GDrive + hasla crypt)
  4. seed backupow         -> sudo rclone copy gdrive-crypt: /backups   (ZANIM pierwszy sync!)
  5. wstan NPM (pierwszy):   cd ~/nginx-proxy-manager && docker compose up -d
  6. wstan aplikacje:        cd ~/nsp-app && docker compose -f docker-compose.prod.yml up -d
  7. dane:                   pg_dump -> psql (baza) + tar wolumenu uploads_data_prod
  8. firewall (jesli odlozony): sudo systemctl start cf-origin-firewall
  Pelny runbook: SERVER-SETUP.md
============================================================================
EOF
