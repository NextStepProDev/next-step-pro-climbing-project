#!/bin/bash
set -euo pipefail

DATE=$(date +%Y-%m-%d)
DB_BACKUP="/backups/db/${DATE}.sql.gz"
FILES_BACKUP="/backups/files/${DATE}.tar.gz"
COMPOSE_DIR="/home/ubuntu/nsp-app"
LOG="/var/log/nsp-backup.log"

echo "=== Backup start: $(date) ===" >> "$LOG"

echo "DB backup..." >> "$LOG"
docker compose -f "${COMPOSE_DIR}/docker-compose.prod.yml" exec -T postgres pg_dump -U nextsteppro nextsteppro | gzip > "$DB_BACKUP"
echo "DB OK: $(du -sh "$DB_BACKUP" | cut -f1)" >> "$LOG"

echo "Files backup..." >> "$LOG"
docker run --rm -v nsp-app_uploads_data_prod:/data:ro -v /backups/files:/backup alpine tar czf "/backup/${DATE}.tar.gz" -C /data .
echo "Files OK: $(du -sh "$FILES_BACKUP" | cut -f1)" >> "$LOG"

echo "Sync to Google Drive..." >> "$LOG"
rclone sync /backups gdrive-crypt: --log-file="$LOG" --log-level INFO

find /backups/db -name "*.sql.gz" -mtime +7 -delete
find /backups/files -name "*.tar.gz" -mtime +7 -delete

echo "=== Backup done: $(date) ===" >> "$LOG"
