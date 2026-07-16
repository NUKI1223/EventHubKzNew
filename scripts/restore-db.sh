#!/usr/bin/env bash
# Restore ONE database from a gzipped pg_dump. Auto-finds the instance hosting <db>.
# Usage: scripts/restore-db.sh <db_name> <path/to/dump.sql.gz>
set -euo pipefail
DB="${1:?usage: restore-db.sh <db_name> <dump.sql.gz>}"
DUMP="${2:?usage: restore-db.sh <db_name> <dump.sql.gz>}"
PG_USER="${PG_USER:-postgres}"
INSTANCES="${PG_INSTANCES:-postgres-users postgres-events postgres-likes postgres-registrations postgres-notifications}"
[ -f "$DUMP" ] || { echo "no such dump: $DUMP"; exit 1; }

PG=""
for pg in $INSTANCES; do
  if docker exec "$pg" psql -U "$PG_USER" -tAc \
      "SELECT 1 FROM pg_database WHERE datname='$DB'" 2>/dev/null | grep -q 1; then PG="$pg"; break; fi
done
[ -n "$PG" ] || { echo "database '$DB' not found on any instance"; exit 1; }

echo "Restoring $DB on $PG from $DUMP"
read -rp "This OVERWRITES data in $DB. Type 'restore' to continue: " ok
[ "$ok" = "restore" ] || { echo "aborted"; exit 1; }
gunzip -c "$DUMP" | docker exec -i "$PG" psql -U "$PG_USER" -d "$DB" -v ON_ERROR_STOP=1 -q
echo "restore complete."
