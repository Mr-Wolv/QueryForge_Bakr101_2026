#!/bin/sh
set -eu

: "${BACKUP_INTERVAL_SECONDS:=86400}"
: "${BACKUP_RETENTION_DAYS:=14}"
: "${BACKUP_DIRECTORY:=/backups}"

export PGPASSWORD
PGPASSWORD="$(cat /run/secrets/queryforge_app_password)"
mkdir -p "$BACKUP_DIRECTORY"

while :; do
  backup_file="$BACKUP_DIRECTORY/queryforge-$(date -u +%Y%m%dT%H%M%SZ).dump"
  partial_file="$backup_file.partial"
  pg_dump \
    --host=db \
    --username=queryforge_app \
    --dbname=queryforge \
    --format=custom \
    --no-owner \
    --file="$partial_file"
  pg_restore --list "$partial_file" >/dev/null
  mv "$partial_file" "$backup_file"
  find "$BACKUP_DIRECTORY" -type f -name 'queryforge-*.dump' \
    -mtime "+$BACKUP_RETENTION_DAYS" -delete
  find "$BACKUP_DIRECTORY" -type f -name 'queryforge-*.dump.partial' -mtime +1 -delete
  sleep "$BACKUP_INTERVAL_SECONDS"
done
