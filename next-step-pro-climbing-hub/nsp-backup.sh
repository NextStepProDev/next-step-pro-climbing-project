#!/bin/bash
#
# nsp-backup.sh — daily backup of the Climbing production stack.
#
#   1. pg_dump of the database   -> /backups/db/<date>.sql.gz
#   2. tar of the uploads volume -> /backups/files/<date>.tar.gz
#   3. upload to the encrypted Google Drive remote (gdrive-crypt:)
#   4. prune: 7 days locally, 90 days on the remote
#
# Every artefact is written as <name>.part, verified, and only then renamed.
# A half-written dump therefore never occupies the name a restore would reach for.
#
# Installed to /usr/local/bin/nsp-backup.sh (provision-server.sh or the deploy
# workflow). Cron: root, 0 3 * * *. Recovery runbook: RESTORE.md.

# -E so the ERR trap also fires inside functions; without it a failure there is silent.
set -Eeuo pipefail

DATE=$(date +%Y-%m-%d)
DB_DIR="/backups/db"
FILES_DIR="/backups/files"
DB_BACKUP="${DB_DIR}/${DATE}.sql.gz"
FILES_BACKUP="${FILES_DIR}/${DATE}.tar.gz"
COMPOSE_DIR="/home/ubuntu/nsp-app"
UPLOADS_VOLUME="nsp-app_uploads_data_prod"
LOG="/var/log/nsp-backup.log"
REMOTE="gdrive-crypt:"
LOCAL_RETENTION_DAYS=7
REMOTE_RETENTION_DAYS=90

# HEALTHCHECK_URL lives here, never in git — the URL is the credential.
# Missing file = pings are skipped and the backup runs exactly as before.
ENV_FILE="/etc/nsp-backup.env"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOG"; }

HEALTHCHECK_URL=""
# shellcheck source=/dev/null
[ -r "$ENV_FILE" ] && . "$ENV_FILE"

# A monitor that cannot be reached must never fail the backup — the backup is the
# point, the ping is only the report about it.
ping_healthcheck() {
  [ -n "${HEALTHCHECK_URL}" ] || return 0
  curl -fsS -m 10 --retry 3 -o /dev/null "${HEALTHCHECK_URL}${1:-}" \
    || log "WARN: healthcheck ping '${1:-<success>}' failed — backup itself unaffected"
}

PINGED_FAIL=0
notify_fail() {
  [ "$PINGED_FAIL" -eq 1 ] && return 0
  PINGED_FAIL=1
  ping_healthcheck "/fail"
}

fail() { log "ERROR: $*"; notify_fail; exit 1; }

trap 'code=$?; log "ERROR: unexpected failure at line ${LINENO} (exit ${code})"; notify_fail' ERR

log "=== Backup start ==="
mkdir -p "$DB_DIR" "$FILES_DIR"

# --------------------------------------------------------------------------
# 1. Database
# --------------------------------------------------------------------------
log "DB dump -> ${DB_BACKUP}.part"
docker compose -f "${COMPOSE_DIR}/docker-compose.prod.yml" exec -T postgres \
  pg_dump -U nextsteppro nextsteppro | gzip > "${DB_BACKUP}.part"

# A dump cut short still gzips cleanly (gzip sees EOF and writes a valid trailer),
# so `gunzip -t` passes it. Only pg_dump's own end-of-file marker proves the dump
# ran to the end. Window is 20 lines, not 5: PostgreSQL 17.6+ appends an
# `\unrestrict <token>` line after the marker, and a future version adding another
# trailing line must not turn a good backup into a nightly false alarm. A truncated
# dump loses the marker entirely, so the wider window costs no detection.
if ! gunzip -c "${DB_BACKUP}.part" | tail -20 | grep -q 'PostgreSQL database dump complete'; then
  fail "DB dump has no completion marker — truncated. Kept as ${DB_BACKUP}.part for inspection."
fi
mv "${DB_BACKUP}.part" "$DB_BACKUP"
log "DB OK: $(du -sh "$DB_BACKUP" | cut -f1)"

# --------------------------------------------------------------------------
# 2. Uploaded files
# --------------------------------------------------------------------------
log "Files archive -> ${FILES_BACKUP}.part"
docker run --rm -v "${UPLOADS_VOLUME}:/data:ro" -v "${FILES_DIR}:/backup" alpine \
  tar czf "/backup/${DATE}.tar.gz.part" -C /data .

# Reads the whole archive back through gzip + tar: catches both a corrupt stream
# and a truncated member table, which is what a disk filling up mid-tar produces.
if ! tar tzf "${FILES_BACKUP}.part" >/dev/null 2>&1; then
  fail "Files archive is unreadable — kept as ${FILES_BACKUP}.part for inspection."
fi
mv "${FILES_BACKUP}.part" "$FILES_BACKUP"
log "Files OK: $(du -sh "$FILES_BACKUP" | cut -f1)"

# --------------------------------------------------------------------------
# 3. Upload — copy, never sync
# --------------------------------------------------------------------------
# `sync` mirrors the local directory, so the local prune below would delete the
# remote copies too and the off-site archive could never outlive local retention.
# `.part` files are excluded: an unverified artefact must not reach the remote.
log "Upload to ${REMOTE} (copy)"
rclone copy /backups "$REMOTE" --exclude "*.part" --log-file="$LOG" --log-level INFO

# --------------------------------------------------------------------------
# 4. Prune — the two archives age independently
# --------------------------------------------------------------------------
log "Prune remote older than ${REMOTE_RETENTION_DAYS}d"
rclone delete "$REMOTE" --min-age "${REMOTE_RETENTION_DAYS}d" --log-file="$LOG" --log-level INFO

log "Prune local older than ${LOCAL_RETENTION_DAYS}d"
find "$DB_DIR"    -name '*.sql.gz' -mtime "+${LOCAL_RETENTION_DAYS}" -delete
find "$FILES_DIR" -name '*.tar.gz' -mtime "+${LOCAL_RETENTION_DAYS}" -delete
# Leftovers from failed runs: kept for inspection, but not forever.
find "$DB_DIR" "$FILES_DIR" -name '*.part' -mtime "+${LOCAL_RETENTION_DAYS}" -delete

log "=== Backup done ==="
ping_healthcheck
