#!/usr/bin/env bash
# Back up every EventHub Postgres database (gzipped pg_dump), with rotation.
# Uses `docker exec` so it works in dev AND prod (no published DB ports needed).
# Usage: scripts/backup-db.sh          (BACKUP_DIR, BACKUP_KEEP, PG_USER overridable)
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-$(cd "$(dirname "$0")/.." && pwd)/backups}"
KEEP="${BACKUP_KEEP:-14}"
PG_USER="${PG_USER:-postgres}"
INSTANCES="${PG_INSTANCES:-postgres-users postgres-events postgres-likes postgres-registrations postgres-notifications}"

ts="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"
echo "[backup] $ts → $BACKUP_DIR (keep last $KEEP per db)"

for pg in $INSTANCES; do
  if ! docker inspect -f '{{.State.Running}}' "$pg" >/dev/null 2>&1; then
    echo "[backup] WARN: $pg not running, skipping"; continue
  fi
  dbs="$(docker exec "$pg" psql -U "$PG_USER" -tAc \
      "SELECT datname FROM pg_database WHERE datistemplate=false AND datname<>'postgres'")"
  for db in $dbs; do
    out="$BACKUP_DIR/${db}-${ts}.sql.gz"
    if docker exec "$pg" pg_dump -U "$PG_USER" -d "$db" --no-owner --no-privileges | gzip > "$out"; then
      echo "[backup] $pg/$db → $(basename "$out") ($(du -h "$out" | cut -f1))"
    else
      echo "[backup] ERROR dumping $pg/$db"; rm -f "$out"; exit 1
    fi
  done
done

# Rotation: keep the newest $KEEP dumps per database.
for db in $(ls "$BACKUP_DIR" 2>/dev/null | sed -E 's/-[0-9]{8}-[0-9]{6}\.sql\.gz$//' | sort -u); do
  ls -1t "$BACKUP_DIR/${db}-"*.sql.gz 2>/dev/null | tail -n +"$((KEEP+1))" | while read -r old; do
    echo "[backup] rotate: rm $(basename "$old")"; rm -f "$old"
  done
done
echo "[backup] done."
