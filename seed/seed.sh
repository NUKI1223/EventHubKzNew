#!/usr/bin/env bash
set -euo pipefail

# Seed test users / events / likes into the running Docker stack.
# Assumes docker compose is up. All passwords are "password123".
#
# По умолчанию seed идемпотентен: если в users/events/likes уже есть строки,
# соответствующий шаг пропускается — реальные пользовательские данные не трогаются.
# Флаг --force возвращает старое поведение (TRUNCATE + полный reseed).

FORCE=0
for arg in "$@"; do
  case "$arg" in
    --force|-f) FORCE=1 ;;
    *) echo "Неизвестный флаг: $arg" >&2; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

if [ -f .env ]; then
  set -a; . ./.env; set +a
fi

: "${DB_USER:?DB_USER not set in .env}"
: "${DB_PASSWORD:?DB_PASSWORD not set in .env}"

GATEWAY="${GATEWAY_URL:-http://localhost:8180}"

# row_count <container> <db> <table>  → число строк, или 0 если таблицы ещё нет.
row_count() {
  local container="$1" db="$2" table="$3"
  local out
  out="$(docker exec -i -e PGPASSWORD="$DB_PASSWORD" "$container" \
    psql -U "$DB_USER" -d "$db" -tAc \
    "SELECT COUNT(*) FROM ${table}" 2>/dev/null | tr -d '[:space:]')" || out=""
  echo "${out:-0}"
}

run_sql() {
  local container="$1" db="$2" file="$3"
  echo "→ ${container} (${db}) ← ${file}"
  docker exec -i -e PGPASSWORD="$DB_PASSWORD" "$container" \
    psql -U "$DB_USER" -d "$db" -v ON_ERROR_STOP=1 < "$file"
}

# step <container> <db> <probe-table> <sql-file> <label>
step() {
  local container="$1" db="$2" probe="$3" file="$4" label="$5"
  local count
  count="$(row_count "$container" "$db" "$probe")"
  if [ "$FORCE" = 1 ]; then
    echo "== ${label} (force, было ${count} строк) =="
    run_sql "$container" "$db" "$file"
  elif [ "$count" = "0" ]; then
    echo "== ${label} (пусто, заливаю) =="
    run_sql "$container" "$db" "$file"
  else
    echo "== ${label} — в ${probe} уже ${count} строк, пропускаю (--force чтобы перезалить) =="
  fi
}

step postgres-users  users_db  users          seed/users.sql    "users"
step postgres-events events_db events         seed/events.sql   "events + tags"
step postgres-events events_db event_requests seed/requests.sql "event-requests + support"
step postgres-likes  likes_db  likes          seed/likes.sql    "likes"

# Денормализованный like_count: имеет смысл только когда реально стоят
# наши тестовые события (id 1..15). Запускаем при --force ИЛИ когда в users
# присутствует фирменный seed-email (значит таблицы только что залиты нами).
SEEDED_PRESENT="$(docker exec -i -e PGPASSWORD="$DB_PASSWORD" postgres-users \
  psql -U "$DB_USER" -d users_db -tAc \
  "SELECT COUNT(*) FROM users WHERE email = 'aidar.kasenov@example.kz'" 2>/dev/null \
  | tr -d '[:space:]')" || SEEDED_PRESENT=0
SEEDED_PRESENT="${SEEDED_PRESENT:-0}"

if [ "$FORCE" = 1 ] || [ "$SEEDED_PRESENT" != "0" ]; then
  echo "== sync events.like_count =="
  docker exec -i -e PGPASSWORD="$DB_PASSWORD" postgres-events \
    psql -U "$DB_USER" -d events_db -v ON_ERROR_STOP=1 <<'SQL'
-- like_count is denormalized in events_db.events; sync it from likes_db via a hardcoded map.
UPDATE events SET like_count = c FROM (VALUES
  (1,6),(2,5),(3,5),(4,4),(5,3),(6,4),(7,3),(8,3),(9,4),(10,9),(11,3),(12,3),(13,4),(14,4),(15,5),
  (16,2),(17,5),(18,3),(19,4),(20,6),(21,1),(22,7),(23,2),(24,3),(25,8),(26,4),(27,5),(28,6),(29,3),(30,5)
) AS v(id, c) WHERE events.id = v.id;
SQL
else
  echo "== sync like_count пропущен (тестовых seed-юзеров нет) =="
fi

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
