#!/usr/bin/env bash
set -euo pipefail

# Seed test users / events / likes into the running Docker stack.
# Assumes docker compose is up. All passwords are "password123".

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

if [ -f .env ]; then
  set -a; . ./.env; set +a
fi

: "${DB_USER:?DB_USER not set in .env}"
: "${DB_PASSWORD:?DB_PASSWORD not set in .env}"

GATEWAY="${GATEWAY_URL:-http://localhost:8180}"

run_sql() {
  local container="$1" db="$2" file="$3"
  echo "→ ${container} (${db}) ← ${file}"
  docker exec -i -e PGPASSWORD="$DB_PASSWORD" "$container" \
    psql -U "$DB_USER" -d "$db" -v ON_ERROR_STOP=1 < "$file"
}

echo "== users =="
run_sql postgres-users users_db   seed/users.sql

echo "== events + tags =="
run_sql postgres-events events_db seed/events.sql

echo "== likes =="
run_sql postgres-likes  likes_db   seed/likes.sql

echo "== updating events.like_count from likes =="
docker exec -i -e PGPASSWORD="$DB_PASSWORD" postgres-events \
  psql -U "$DB_USER" -d events_db -v ON_ERROR_STOP=1 <<'SQL'
-- like_count is denormalized in events_db.events; sync it from likes_db via a hardcoded map.
UPDATE events SET like_count = c FROM (VALUES
  (1,6),(2,5),(3,5),(4,4),(5,3),(6,4),(7,3),(8,3),(9,4),(10,9),(11,3),(12,3),(13,4),(14,4),(15,5)
) AS v(id, c) WHERE events.id = v.id;
SQL

echo "== reindex Elasticsearch =="
if curl -sf -X POST "${GATEWAY}/api/events/reindex" >/dev/null; then
  echo "✓ reindex triggered"
else
  echo "⚠ reindex call failed — search may show stale data until services are ready"
fi

cat <<EOF

Готово.

Логин: любой email вида <name>@example.kz
Пароль: password123

Учётки:
  aidar.kasenov@example.kz     — обычный
  dinara.zhumabaeva@example.kz — ADMIN
  alisher.zhakupov@example.kz
  aigerim.satpaeva@example.kz
  timur.akhmetov@example.kz
  anastasia.sokolova@example.kz
  bauyrzhan.mukashev@example.kz
  kamila.nurlanova@example.kz
  yerzhan.serikbaev@example.kz
  alina.romanova@example.kz
EOF
