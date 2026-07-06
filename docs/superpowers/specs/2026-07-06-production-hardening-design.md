# Production hardening (set 1) — design

Date: 2026-07-06. Status: approved by user.

## Goal

Make the EventHub.kz stack safe to expose on a public VPS. After this work, the
only thing reachable from the public internet is HTTPS on a reverse proxy;
every application service, database, and infrastructure component lives on the
internal Docker network and cannot be reached directly.

This is **set 1 of a security roadmap**. It closes the critical (🔴) pre-prod
findings from the 2026-07-06 security audit: gateway-bypass via published ports,
infrastructure exposed with default credentials, no TLS, and secret rotation.

## Background: why this is critical

Every application service publishes its port to the host (`user-service:8082`,
`like-service:8084`, `registration-service:8089`, `audit-service:8091`,
`notification-service:8086`, `file-service:8088`, `tag-service:8087`), and every
downstream service **trusts the `X-User-Id` / `X-User-Role` headers without
validating a JWT** — that validation only happens at the gateway. On a public
host, an attacker sends `curl -H "X-User-Role: ADMIN" -H "X-User-Id: 1"
http://server:8082/api/users/admin/list` and gets every user's email, or
`DELETE`s any account — with no token. Docker publishes ports straight into
iptables, bypassing a host `ufw`, so a firewall rule is not a sufficient fix.
Infrastructure (Postgres `postgres123`, Redis with no password, Elasticsearch
`xpack.security.enabled=false`, MinIO `minioadmin`, Kafka UI with no auth,
Grafana `admin/admin`) is likewise published. There is no TLS anywhere.

## Approach: secure-by-default compose, three files

Docker Compose merges `ports:` lists additively — an override cannot *remove* a
published port. So instead of a prod override that unpublishes, we invert the
default: the base file publishes nothing, and local dev opts back in.

- **`docker-compose.yml` (secure base):** no `ports:` on any application service
  or infrastructure component. Services communicate only over the existing
  `eventhub-network`. This is what runs in production.
- **`docker-compose.override.yml` (local dev, auto-loaded):** re-publishes the
  debug ports to `127.0.0.1` — gateway 8180, the five Postgres instances,
  Elasticsearch 9200, Redis 6380, Kafka 9092/29092, Kafka UI 8090, MinIO
  9100/9101, Eureka 8761, Prometheus 9090, Grafana 3000, Zipkin 9411, and the
  single-instance service ports (user 8082, tag 8087, search 8085, like 8084,
  registration 8089, audit 8091, notification 8086, file 8088). Docker Compose
  auto-applies this file on `docker compose up`, so `start.sh` and the current
  local workflow are unchanged.
  Note: `auth-service` and `event-service` run with `deploy: replicas: 2` and
  already publish **no** host port (a fixed host port can't map to two
  replicas) — they stay unpublished in the override too, reached locally only
  through the gateway, exactly as today.
- **`docker-compose.caddy.yml` (production):** adds the Caddy reverse proxy
  (the only container publishing host ports: 80 and 443). Production deploy is
  `docker compose -f docker-compose.yml -f docker-compose.caddy.yml up -d` — the
  dev override is not named, so nothing else is published.

Binding host publishes to `127.0.0.1` in the dev override (not `0.0.0.0`) is an
extra safety net so even a developer's own machine doesn't expose debug ports to
its LAN.

## Caddy, TLS, and same-origin (removes CORS in prod)

`Caddyfile` (~15 lines), domain from the `APP_DOMAIN` env var:

- Automatic Let's Encrypt certificate; HTTP→HTTPS redirect is automatic.
- Serves the built frontend (`frontend/dist`) as static files.
- Reverse-proxies `/api/*` and `/auth/*` to `api-gateway:8080` over the internal
  network — **on the same domain as the frontend.**
- `header` directive adds HSTS (`Strict-Transport-Security`),
  `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Referrer-Policy: strict-origin-when-cross-origin`.
- `respond /actuator/* 403` — blocks the actuator surface at the proxy (trivial
  at this layer; the fuller actuator-reduction is set 2).

Because the frontend and API are served from one origin in production, **CORS is
not needed in prod at all** — cross-origin only exists locally (vite `:5173` →
gateway `:8180`).

The frontend is built (`frontend/dist`) and served by Caddy; it calls the API at
the relative path `/api` (same origin). `VITE_API_URL` defaults to the relative
`/api` for prod builds; local dev keeps pointing at `http://localhost:8180`.

## Secrets and infrastructure credentials

- **CORS from env:** `api-gateway` `CorsConfig` reads allowed origins from a
  property `app.cors.allowed-origins` (comma-separated; default
  `http://localhost:5173,http://localhost:3000` for dev). In prod it is left
  unset/empty — no cross-origin, no CORS filter needed.
- **Redis:** `command: redis-server --requirepass ${REDIS_PASSWORD}`; every
  service that uses Redis reads `spring.data.redis.password=${REDIS_PASSWORD}`.
- **MinIO:** `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` from env replace
  `minioadmin`; `file-service` S3 credentials come from the same env values.
- **Postgres:** strong `DB_PASSWORD` from `.env.prod` replaces `postgres123`.
- **Elasticsearch xpack.security — deliberately deferred** (documented residual
  risk): enabling full ES security (TLS certs, bootstrap passwords, keystore) is
  disproportionate for this milestone. Network isolation (ES is no longer
  published) removes the exposure that matters. Recorded as an accepted residual
  risk in this spec and the roadmap, revisit if ES ever needs to leave the
  network.
- **`.env.example`:** add `REDIS_PASSWORD`, `MINIO_ROOT_PASSWORD`,
  `APP_CORS_ORIGINS`, `APP_DOMAIN` with placeholder values.
- **`.env.prod.example`:** new template committed to git (placeholders only)
  documenting the full prod secret set.

## Operational checklist (in spec, executed by the human at deploy time)

1. Rotate the three secrets that were briefly in the public git history: JWT
   secret (`openssl rand -base64 32`), Gmail app-password, Gemini API key.
2. Generate strong values for `DB_PASSWORD`, `REDIS_PASSWORD`,
   `MINIO_ROOT_PASSWORD` into `.env.prod`.
3. `chmod 600 .env.prod`; never commit it (already covered by `.gitignore`
   `.env` rule — confirm `.env.prod` is ignored too).

## Acceptance test (config work — verified by a smoke script, not unit tests)

New script `scripts/prod-smoke.sh` (or a documented sequence). After bringing up
the production set on a test host:

1. **Isolation:** no host port answers except 80/443. Probe a sample of the
   previously-published ports (8082, 8091, 5434, 9200, 8090) from the host and
   assert connection refused / no response.
2. **TLS + redirect:** `http://<domain>` returns a 301/308 to `https://`;
   `https://<domain>` serves the frontend with a valid certificate.
3. **Direct-hit refused:** a direct request to a downstream service port from the
   host is refused (proves the gateway-bypass hole is closed).
4. **End-to-end through Caddy:** login (`/auth/login`), fetch the event catalog
   (`/api/events`), and one authenticated call all succeed over HTTPS.
5. **Actuator blocked:** `https://<domain>/actuator/health` returns 403.

Because a laptop can't get a real Let's Encrypt cert for a fake domain, the
local rehearsal of this test uses Caddy's `tls internal` (self-signed) mode with
a `curl -k`; the real cert path is exercised on the actual VPS.

## Out of scope (later sets)

- **Set 2:** rate limiting on `/auth/*` and AI endpoints (Spring Cloud Gateway
  `RequestRateLimiter`, Redis already present); strip inbound `X-User-*` at the
  gateway (`default-filters: RemoveRequestHeader`); reduce actuator exposure to
  `health` on all services.
- **Set 3:** refresh tokens with revocation + password-change session
  invalidation; Flyway migrations replacing `ddl-auto: update`.
- **Medium:** file-upload magic-byte validation + per-user quota; organizer
  email opt-in; PII retention policy for audit snapshots; dependency scanning in
  CI (`npm audit` / OWASP dependency-check / Dependabot).
