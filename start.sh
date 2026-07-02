#!/usr/bin/env bash
# Usage:
#   ./start.sh              — поднять backend (docker) + frontend (vite)
#   ./start.sh --build      — пересобрать jar-ы и docker-образы перед стартом
#   ./start.sh --seed       — прогнать seed только на пустых таблицах (идемпотентно)
#   ./start.sh --seed-force — TRUNCATE + полный reseed (уничтожает данные!)
#   ./start.sh --no-front   — поднять только backend
#   ./start.sh --with-monitoring — добавить prometheus/grafana/zipkin/kafka-ui (ест ~2 ГБ RAM)
#   ./start.sh stop         — остановить всё
#   ./start.sh logs <svc>   — показать логи сервиса (eureka, api-gateway, ...)
#   ./start.sh status       — статус контейнеров
#   docker exec redis redis-cli KEYS '*'
#   docker exec redis redis-cli GET 'events::5'
#   # список индексов (увидишь events + системные)
    #  curl 'http://localhost:9200/_cat/indices?v'
    #
    #  # сколько документов
    #  curl 'http://localhost:9200/events/_count?pretty'
    #
    #  # все документы (как "SELECT *")
    #  curl 'http://localhost:9200/events/_search?pretty&size=50'
    #
    #  # один документ по id
    #  curl 'http://localhost:9200/events/_doc/5?pretty'
    #
    #  # схема (mapping — аналог DDL таблицы)
    #  curl 'http://localhost:9200/events/_mapping?pretty'
    #
    #  # поиск по слову
    #  curl 'http://localhost:9200/events/_search?q=React&pretty'
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR"
FRONTEND_DIR="$SCRIPT_DIR/frontend"
GATEWAY_URL="http://localhost:8180"
FRONTEND_URL="http://localhost:5173"
FRONT_LOG="/tmp/eventhub-frontend.log"
FRONT_PID="/tmp/eventhub-frontend.pid"

C_GREEN="\033[1;32m"
C_BLUE="\033[1;34m"
C_YELLOW="\033[1;33m"
C_RED="\033[1;31m"
C_RESET="\033[0m"

log()  { echo -e "${C_BLUE}▸${C_RESET} $*"; }
ok()   { echo -e "${C_GREEN}✓${C_RESET} $*"; }
warn() { echo -e "${C_YELLOW}⚠${C_RESET} $*"; }
err()  { echo -e "${C_RED}✗${C_RESET} $*" >&2; }

# ── prerequisites ─────────────────────────────────────────────────
check_prereqs() {
  local missing=0
  for cmd in docker mvn node npm curl; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      err "Не найдена утилита: $cmd"
      missing=1
    fi
  done
  if ! docker compose version >/dev/null 2>&1; then
    err "docker compose plugin не установлен"
    missing=1
  fi
  if ! docker info >/dev/null 2>&1; then
    err "Docker daemon не запущен (sudo systemctl start docker)"
    missing=1
  fi
  [ "$missing" = 0 ] || exit 1
}

# ── stop ──────────────────────────────────────────────────────────
stop_all() {
  log "Останавливаю frontend..."
  if [ -f "$FRONT_PID" ]; then
    local pid; pid="$(cat "$FRONT_PID")"
    if kill -0 "$pid" 2>/dev/null; then
      pkill -P "$pid" 2>/dev/null || true
      kill "$pid" 2>/dev/null || true
      ok "frontend (pid $pid) остановлен"
    fi
    rm -f "$FRONT_PID"
  else
    pkill -f "vite" 2>/dev/null && ok "vite процессы убиты" || warn "vite не запущен"
  fi

  log "Останавливаю docker stack..."
  cd "$BACKEND_DIR"
  # --profile observability обязателен: иначе контейнеры мониторинга (grafana/prometheus/
  # zipkin/kafka-ui), поднятые с этим профилем, не попадут в down и удержат сеть
  # eventhub-network ("has active endpoints"), из-за чего down падает с ошибкой.
  docker compose --profile observability down
  ok "Всё остановлено"
}

# ── build ─────────────────────────────────────────────────────────
build_backend() {
  log "Собираю jar-ы (mvn package -DskipTests)..."
  cd "$BACKEND_DIR"
  mvn -q package -DskipTests
  ok "jar-ы собраны"

  log "Пересобираю docker-образы (--no-cache)..."
  docker compose build --no-cache
  ok "образы готовы"
}

# ── start backend ─────────────────────────────────────────────────
start_backend() {
  cd "$BACKEND_DIR"
  if [ "$DO_MONITORING" = 1 ]; then
    log "Поднимаю docker stack (+ мониторинг)..."
    docker compose --profile observability up -d
  else
    log "Поднимаю docker stack (lite — без prometheus/grafana/zipkin/kafka-ui)..."
    docker compose up -d
  fi
  ok "контейнеры стартанули"
}

# ── wait for gateway ──────────────────────────────────────────────
wait_for_gateway() {
  log "Жду готовности api-gateway на $GATEWAY_URL ..."
  local tries=0 max=60
  while [ "$tries" -lt "$max" ]; do
    if curl -sf -o /dev/null "$GATEWAY_URL/actuator/health" 2>/dev/null \
       || curl -sf -o /dev/null "$GATEWAY_URL" 2>/dev/null; then
      ok "api-gateway отвечает"
      return 0
    fi
    sleep 2
    tries=$((tries + 1))
    [ $((tries % 5)) -eq 0 ] && echo "  ... ещё жду (${tries}/${max})"
  done
  warn "api-gateway не ответил за $((max*2))s — сиды и фронт всё равно запущу, но проверь логи"
}

# ── seed ──────────────────────────────────────────────────────────
seed_data() {
  local force="${1:-0}"
  if [ "$force" = 1 ]; then
    log "Прогоняю seed --force (TRUNCATE + перезалив)..."
  else
    log "Прогоняю seed (идемпотентно — пропустит непустые таблицы)..."
  fi
  cd "$BACKEND_DIR"
  if [ ! -x seed/seed.sh ]; then
    chmod +x seed/seed.sh
  fi
  if [ "$force" = 1 ]; then
    bash seed/seed.sh --force
  else
    bash seed/seed.sh
  fi
  ok "seed применён"
}

# ── frontend ──────────────────────────────────────────────────────
start_frontend() {
  log "Поднимаю Vite dev server..."
  cd "$FRONTEND_DIR"
  if [ ! -f .env ]; then
    log ".env не найден — создаю с дефолтом VITE_API_URL=$GATEWAY_URL"
    echo "VITE_API_URL=$GATEWAY_URL" > .env
  fi
  if [ ! -d node_modules ]; then
    log "node_modules не найден — npm install..."
    npm install
  fi
  nohup npm run dev > "$FRONT_LOG" 2>&1 &
  echo $! > "$FRONT_PID"
  sleep 2
  if kill -0 "$(cat "$FRONT_PID")" 2>/dev/null; then
    ok "frontend стартанул (pid $(cat "$FRONT_PID"), лог: $FRONT_LOG)"
  else
    err "frontend упал, см. $FRONT_LOG"
    return 1
  fi
}

# ── status ────────────────────────────────────────────────────────
status() {
  cd "$BACKEND_DIR"
  docker compose ps
  echo
  if [ -f "$FRONT_PID" ] && kill -0 "$(cat "$FRONT_PID")" 2>/dev/null; then
    ok "frontend: pid $(cat "$FRONT_PID"), лог $FRONT_LOG"
  else
    warn "frontend: не запущен"
  fi
}

# ── summary ───────────────────────────────────────────────────────
print_summary() {
  echo
  echo "══════════════════════════════════════════════════════"
  ok "EventHub.kz поднят"
  echo
  echo "  Frontend:   $FRONTEND_URL"
  echo "  API:        $GATEWAY_URL"
  echo "  Eureka:     http://localhost:8761"
  echo "  MinIO UI:   http://localhost:9101  (minioadmin / minioadmin)"
  if [ "$DO_MONITORING" = 1 ]; then
    echo "  Grafana:    http://localhost:3000  (admin / admin)"
    echo "  Zipkin:     http://localhost:9411"
    echo "  Kafka UI:   http://localhost:8090"
  fi
  echo
  echo "  Тестовые учётки (пароль 'password123'):"
  echo "    aidar.kasenov@example.kz       — обычный"
  echo "    dinara.zhumabaeva@example.kz   — ADMIN"
  echo
  echo "  Остановить:  $0 stop"
  echo "  Лог фронта:  tail -f $FRONT_LOG"
  echo "  Лог бэка:    $0 logs <service>   (например: $0 logs notification-service)"
  echo "══════════════════════════════════════════════════════"
}

# ── main ──────────────────────────────────────────────────────────
case "${1:-}" in
  stop)
    stop_all
    exit 0
    ;;
  status)
    status
    exit 0
    ;;
  logs)
    shift
    cd "$BACKEND_DIR"
    docker compose logs -f --tail=200 "${@:-}"
    exit 0
    ;;
esac

DO_BUILD=0
DO_SEED=0
DO_SEED_FORCE=0
DO_FRONT=1
DO_MONITORING=0

for arg in "$@"; do
  case "$arg" in
    --build)            DO_BUILD=1 ;;
    --seed)             DO_SEED=1 ;;
    --seed-force)       DO_SEED=1; DO_SEED_FORCE=1 ;;
    --no-front)         DO_FRONT=0 ;;
    --with-monitoring)  DO_MONITORING=1 ;;
    -h|--help)
      sed -n '2,18p' "$0"; exit 0 ;;
    *) err "Неизвестный флаг: $arg"; exit 1 ;;
  esac
done

check_prereqs

[ "$DO_BUILD" = 1 ] && build_backend
start_backend
wait_for_gateway

[ "$DO_SEED" = 1 ] && seed_data "$DO_SEED_FORCE"
[ "$DO_FRONT" = 1 ] && start_frontend

print_summary
