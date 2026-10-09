#!/bin/bash
#
# nsp-backup.sh — daily backup of the Climbing production stack.
#
#   1. pg_dump of the database   -> /backups/db/<date>.sql.gz
#   2. tar of the uploads volume -> /backups/files/<date>.tar.gz — only when the
#      uploads changed, or the newest archive is FILES_REFRESH_DAYS old
#   3. upload to the encrypted Google Drive remote (gdrive-crypt:)
#   4. prune: 7 days locally, 40 days on the remote — /backups/milestones is exempt,
#      and the newest uploads archive is always kept locally
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
# Dumps taken by hand before a risky operation (a major Postgres upgrade, a data
# migration) live here and NO prune touches them, local or remote. They used to sit
# in db/ next to the daily ones, where `-name '*.sql.gz' -mtime +7` cannot tell a
# one-off safety net from yesterday's routine copy: the pre-upgrade dump of
# 2026-09-05 was one night away from deletion while the rollback volume it backed up
# had already been removed. A directory, not a filename convention — the person
# dumping before a risky operation is exactly the one who will not remember a suffix.
MILESTONE_DIR="/backups/milestones"
DB_BACKUP="${DB_DIR}/${DATE}.sql.gz"
FILES_BACKUP="${FILES_DIR}/${DATE}.tar.gz"
COMPOSE_DIR="/home/ubuntu/nsp-app"
UPLOADS_VOLUME="nsp-app_uploads_data_prod"
LOG="/var/log/nsp-backup.log"
REMOTE="gdrive-crypt:"
LOCAL_RETENTION_DAYS=7
# 40 days, not 90: the Google Drive account (15 GB) is shared with fire-academy and
# anovastudio, and this stack alone sent a ~150 MB uploads archive every night — at
# 90 days the account would have filled up in November 2026 (measured 2026-10-09).
REMOTE_RETENTION_DAYS=40

# The uploads archive is only made when the uploads changed. Every archive is a
# complete copy, so a nightly archive of mostly the same photos spent ~150 MB of
# Drive a day for nothing. Each archive is still FULL, never incremental: a restore
# is the newest dump plus the newest uploads archive dated on or before it — no newer
# archive means precisely that nothing changed in between.
#
# The state file holds a fingerprint of the volume (path, size and mtime of every
# file). Even with no change an archive is made every FILES_REFRESH_DAYS days, which
# must stay below REMOTE_RETENTION_DAYS, or the remote prune would delete the only
# archive there is. No state file (a new server) means an archive straight away.
FILES_REFRESH_DAYS=30
FILES_STATE="/var/lib/nsp-backup/files-state"

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
mkdir -p "$DB_DIR" "$FILES_DIR" "$MILESTONE_DIR" "$(dirname "$FILES_STATE")"

[ "$FILES_REFRESH_DAYS" -lt "$REMOTE_RETENTION_DAYS" ] \
  || fail "FILES_REFRESH_DAYS (${FILES_REFRESH_DAYS}) must be below REMOTE_RETENTION_DAYS (${REMOTE_RETENTION_DAYS}) — Drive would be left without an uploads archive"

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
# The tail goes into a variable before grep looks at it: `grep -q` at the end of a
# pipeline exits on its first match, `tail` still writing then gets SIGPIPE, and under
# `pipefail` the test fails — a good dump reported as truncated.
DUMP_TAIL=$(gunzip -c "${DB_BACKUP}.part" | tail -20)
if ! grep -q 'PostgreSQL database dump complete' <<<"$DUMP_TAIL"; then
  fail "DB dump has no completion marker — truncated. Kept as ${DB_BACKUP}.part for inspection."
fi
mv "${DB_BACKUP}.part" "$DB_BACKUP"
log "DB OK: $(du -sh "$DB_BACKUP" | cut -f1)"

# --------------------------------------------------------------------------
# 2. Uploaded files
# --------------------------------------------------------------------------
# Fingerprint of the volume: path|size|mtime of every file, sorted and hashed. Adding,
# removing or replacing an upload changes it. Taken BEFORE the archive: a file added
# in between lands in the archive and changes tomorrow's fingerprint — at worst one
# archive too many, never one too few.
FILES_FP=$(docker run --rm -v "${UPLOADS_VOLUME}:/data:ro" alpine \
  sh -c 'cd /data && find . -type f -exec stat -c "%n|%s|%Y" {} + | sort' \
  | sha256sum | cut -d' ' -f1)

PREV_FP=""
PREV_AT=0
if [ -r "$FILES_STATE" ]; then
  read -r PREV_FP PREV_AT < "$FILES_STATE" || true
fi
# A damaged state file (a write cut short by a full disk) must not break every
# night's backup: anything that is not a number counts as no state, so an archive is
# made and the write after it repairs the file.
case "$PREV_AT" in
  ''|*[!0-9]*) PREV_FP=""; PREV_AT=0 ;;
esac
FILES_AGE_DAYS=$(( ( $(date +%s) - PREV_AT ) / 86400 ))

if [ "$FILES_FP" != "$PREV_FP" ] || [ "$FILES_AGE_DAYS" -ge "$FILES_REFRESH_DAYS" ]; then
  log "Files archive -> ${FILES_BACKUP}.part"
  docker run --rm -v "${UPLOADS_VOLUME}:/data:ro" -v "${FILES_DIR}:/backup" alpine \
    tar czf "/backup/${DATE}.tar.gz.part" -C /data .

  # Reads the whole archive back through gzip + tar: catches both a corrupt stream
  # and a truncated member table, which is what a disk filling up mid-tar produces.
  if ! tar tzf "${FILES_BACKUP}.part" >/dev/null 2>&1; then
    fail "Files archive is unreadable — kept as ${FILES_BACKUP}.part for inspection."
  fi
  mv "${FILES_BACKUP}.part" "$FILES_BACKUP"
  # Written only once the archive is published: a run that dies leaves the old
  # fingerprint, so the next run makes the archive again.
  printf '%s %s\n' "$FILES_FP" "$(date +%s)" > "$FILES_STATE"
  log "Files OK: $(du -sh "$FILES_BACKUP" | cut -f1)"
else
  log "Files unchanged since $(date -d "@${PREV_AT}" +%F 2>/dev/null || echo "${FILES_AGE_DAYS} days ago") — newest archive still current, not making another"
fi

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
# The exclude is the remote half of the milestone rule above. Without it the off-site
# copy of a pre-upgrade dump expires 40 days after the upgrade — exactly when nobody
# is watching it any more, and with no local copy left to notice it went.
rclone delete "$REMOTE" --min-age "${REMOTE_RETENTION_DAYS}d" \
  --exclude "milestones/**" --log-file="$LOG" --log-level INFO

log "Prune local older than ${LOCAL_RETENTION_DAYS}d"
find "$DB_DIR"    -name '*.sql.gz' -mtime "+${LOCAL_RETENTION_DAYS}" -delete
# The newest uploads archive always stays, even past 7 days: while nothing changes it
# IS the current copy, and a restore from this box alone (no Drive) must still have
# one. File names are dates, so sorting by name is sorting by age.
NEWEST_FILES=$(find "$FILES_DIR" -maxdepth 1 -name '*.tar.gz' | sort | tail -1)
find "$FILES_DIR" -name '*.tar.gz' -mtime "+${LOCAL_RETENTION_DAYS}" ! -path "${NEWEST_FILES:-/none}" -delete
# Leftovers from failed runs: kept for inspection, but not forever.
find "$DB_DIR" "$FILES_DIR" -name '*.part' -mtime "+${LOCAL_RETENTION_DAYS}" -delete

log "=== Backup done ==="
ping_healthcheck
