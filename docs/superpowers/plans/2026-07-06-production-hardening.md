# Production Hardening (Set 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the stack safe to expose on a public VPS — nothing reachable from the host except HTTPS on a Caddy reverse proxy; every service, database, and infra component lives on the internal Docker network.

**Architecture:** Invert Docker Compose to secure-by-default — the base `docker-compose.yml` publishes no host ports; an auto-loaded `docker-compose.override.yml` re-publishes debug ports to `127.0.0.1` for local dev; a `docker-compose.caddy.yml` adds Caddy (the only container publishing 80/443) with automatic TLS, same-origin frontend+API (removes CORS in prod), and an actuator block. Infra credentials (Redis, MinIO, Postgres) and CORS origins move to env.

**Tech Stack:** Docker Compose, Caddy 2 (Let's Encrypt), Spring Cloud Gateway (reactive), Spring Boot, Redis, bash.

**Spec:** `docs/superpowers/specs/2026-07-06-production-hardening-design.md`

## Global Constraints

- Run Maven **from the repo root** with `-pl <module>`; never from inside a module dir.
- Commit messages: lowercase conventional commits (`feat(scope): ...`); **no Co-Authored-By / attribution lines.**
- Docker redeploy recipe per service: `mvn clean package -DskipTests -pl <svc>` → `docker compose build <svc>` → `docker rm -f <container(s)>` → `docker compose up -d <svc>`.
- The dev override binds published ports to `127.0.0.1` (not `0.0.0.0`) so debug ports never reach a developer's LAN.
- `auth-service` and `event-service` run `deploy: replicas: 2` and publish **no** host port (a fixed host port can't map to two replicas) — they stay unpublished everywhere; reached locally only through the gateway.
- Redis password uses an empty-safe default: `${REDIS_PASSWORD:-}` in compose command, `${REDIS_PASSWORD:}` in Spring config — empty = no auth (local dev), set = auth (prod). Redis treats an empty `--requirepass ""` as "no password".
- Production deploy command (documented, not run in CI): `docker compose -f docker-compose.yml -f docker-compose.caddy.yml up -d`.
- Local dev is unchanged: `docker compose up` / `./start.sh` auto-load `docker-compose.override.yml`.

---

### Task 1: CORS allowed-origins from env (api-gateway)

**Files:**
- Modify: `api-gateway/src/main/java/org/ngcvfb/apigateway/config/CorsConfig.java`
- Modify: `api-gateway/src/main/resources/application.yml` (add `app.cors.allowed-origins` property)
- Test: `api-gateway/src/test/java/org/ngcvfb/apigateway/config/CorsConfigTest.java`

**Interfaces:**
- Produces: `CorsConfig.buildCorsConfiguration(String originsCsv)` → `org.springframework.web.cors.CorsConfiguration` — a static, unit-testable helper. Empty/blank CSV → a config with an empty allowed-origins list (no cross-origin permitted). The `@Bean` calls it with the injected property.

- [ ] **Step 1: Write the failing test**

```java
package org.ngcvfb.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void parsesCommaSeparatedOrigins() {
        CorsConfiguration cfg = CorsConfig.buildCorsConfiguration(
                "http://localhost:5173,https://eventhub.kz");
        assertThat(cfg.getAllowedOrigins())
                .containsExactly("http://localhost:5173", "https://eventhub.kz");
        assertThat(cfg.getAllowCredentials()).isTrue();
        assertThat(cfg.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");
    }

    @Test
    void trimsWhitespaceAndDropsBlanks() {
        CorsConfiguration cfg = CorsConfig.buildCorsConfiguration(" http://a.com , , http://b.com ");
        assertThat(cfg.getAllowedOrigins()).containsExactly("http://a.com", "http://b.com");
    }

    @Test
    void blankInputYieldsEmptyOrigins() {
        CorsConfiguration cfg = CorsConfig.buildCorsConfiguration("");
        assertThat(cfg.getAllowedOrigins()).isEmpty();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -pl api-gateway -Dtest=CorsConfigTest`
Expected: COMPILATION ERROR (`buildCorsConfiguration` not defined)

- [ ] **Step 3: Implement**

Replace `api-gateway/src/main/java/org/ngcvfb/apigateway/config/CorsConfig.java` with:

```java
package org.ngcvfb.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
public class CorsConfig {

    // Список origin через запятую. В проде фронт и API одного домена (за Caddy),
    // поэтому значение остаётся пустым и cross-origin не разрешается вовсе.
    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    static CorsConfiguration buildCorsConfiguration(String originsCsv) {
        List<String> origins = originsCsv == null ? List.of()
                : Arrays.stream(originsCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins);
        cfg.setMaxAge(3600L);
        cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        cfg.setAllowedHeaders(Collections.singletonList("*"));
        cfg.setAllowCredentials(true);
        return cfg;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", buildCorsConfiguration(allowedOrigins));
        return new CorsWebFilter(source);
    }
}
```

Add to `api-gateway/src/main/resources/application.yml` under the top-level `spring:` sibling area (e.g. after the `spring:` block, at root level) — put it near the existing `security:` block:

```yaml
app:
  cors:
    allowed-origins: ${APP_CORS_ORIGINS:http://localhost:5173,http://localhost:3000}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -pl api-gateway -Dtest=CorsConfigTest`
Expected: `Tests run: 3, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add api-gateway/src
git commit -m "feat(gateway): read CORS allowed-origins from env (empty in prod)"
```

---

### Task 2: Redis password (compose + service configs)

**Files:**
- Modify: `docker-compose.yml` — redis `command`, and add `REDIS_PASSWORD` env to the 5 services that use Redis (api-gateway, auth-service, like-service, event-service, registration-service)
- Modify: `api-gateway/src/main/resources/application.yml`, `auth-service/.../application.yml`, `like-service/.../application.yml`, `event-service/.../application.yml`, `registration-service/.../application.yml` — add `password` under `spring.data.redis`

**Interfaces:**
- Produces: every Redis-using service authenticates when `REDIS_PASSWORD` is set; no-op when empty (local dev). Consumed operationally by the prod deploy.

- [ ] **Step 1: Add password to redis container command.** In `docker-compose.yml` the `redis:` block (around line 532) currently has no `command:`. Add one under `image: redis:6.2`:

```yaml
  redis:
    image: redis:6.2
    container_name: redis
    command: ["redis-server", "--requirepass", "${REDIS_PASSWORD:-}"]
    mem_limit: 256m
```

- [ ] **Step 2: Add `password` to each Redis-using service's config.** In each of the 5 files, find the `spring.data.redis:` block (has `host:` and `port:`) and add a `password:` line:

```yaml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
```

Files and their current redis blocks: `api-gateway/src/main/resources/application.yml:193`, `auth-service/src/main/resources/application.yml:28`, `like-service/src/main/resources/application.yml:23`, `event-service/src/main/resources/application.yml:41`, `registration-service/src/main/resources/application.yml:27`. (Line numbers approximate — match on the `redis:` block with `host:`.)

- [ ] **Step 3: Pass `REDIS_PASSWORD` into each of the 5 service containers.** In `docker-compose.yml`, each of those services already has a `REDIS_HOST=redis` / `REDIS_PORT=6379` pair in its `environment:` list. Add next to them:

```yaml
      - REDIS_PASSWORD=${REDIS_PASSWORD:-}
```

(api-gateway, auth-service, like-service, event-service, registration-service — the same 5.)

- [ ] **Step 4: Verify empty-password path still works (local dev).** Rebuild the 5 jars is NOT needed (only yml/compose changed, and yml is read at runtime from the image? No — application.yml is baked into the jar). The changed `application.yml` files ARE inside each jar, so rebuild is required. Rebuild + redeploy the 5 services per the Global-Constraints recipe, then:

Run:
```bash
docker exec redis redis-cli ping                          # PONG (no auth locally)
curl -sf http://localhost:8180/api/events?page=0\&size=1 >/dev/null && echo "gateway OK"
```
Expected: `PONG` and `gateway OK` (empty `REDIS_PASSWORD` → no auth, app works as before).

- [ ] **Step 5: Verify the password path.** Prove the auth wiring works without disturbing the running stack:

Run:
```bash
docker run --rm -e REDIS_PASSWORD=testpass123 redis:6.2 \
  sh -c 'redis-server --requirepass "$REDIS_PASSWORD" --daemonize yes && sleep 1 && \
         (redis-cli ping; redis-cli -a "$REDIS_PASSWORD" ping)'
```
Expected: first `ping` → `NOAUTH Authentication required.`; second (`-a testpass123`) → `PONG`. Confirms `--requirepass ${REDIS_PASSWORD}` enforces auth when set.

- [ ] **Step 6: Commit**

```bash
git add docker-compose.yml api-gateway/src auth-service/src like-service/src event-service/src registration-service/src
git commit -m "feat(infra): password-protect Redis via REDIS_PASSWORD (empty = dev)"
```

---

### Task 3: Secure-by-default base compose + dev override

**Files:**
- Modify: `docker-compose.yml` — remove every `ports:` block from all services and infra (the base publishes nothing)
- Create: `docker-compose.override.yml` — re-publish debug ports to `127.0.0.1` for local dev
- Modify: `start.sh` — no change needed (compose auto-loads the override); but verify the summary URLs still print

**Interfaces:**
- Produces: `docker compose config` on the base alone shows zero `published:` ports; `docker compose config` with the auto-loaded override shows the debug ports bound to `127.0.0.1`. The prod deploy names only the base (+caddy), so nothing leaks.

- [ ] **Step 1: Remove all `ports:` blocks from `docker-compose.yml`.** Delete the `ports:` key and its list item(s) from every service that has one. Full list (service → the lines to delete): eureka-server (`- "8761:8761"`), api-gateway (`- "8180:8080"`), user-service (`- "8082:8082"`), tag-service (`- "8087:8087"`), search-service (`- "8085:8085"`), like-service (`- "8084:8084"`), registration-service (`- "8089:8089"`), audit-service (`- "8091:8091"`), notification-service (`- "8086:8086"`), file-service (`- "8088:8088"`), minio (`- "9100:9000"`, `- "9101:9001"`), postgres-users (`- "5437:5432"`), postgres-events (`- "5434:5432"`), postgres-likes (`- "5435:5432"`), postgres-registrations (`- "5439:5432"`), postgres-notifications (`- "5436:5432"`), elasticsearch (`- "9200:9200"`), redis (`- "6380:6379"`), zookeeper (`- "2181:2181"`), kafka (`- "9092:9092"`, `- "29092:29092"`), kafka-ui (`- "8090:8080"`), prometheus (`- "9090:9090"`), grafana (`- "3000:3000"`), zipkin (`- "9411:9411"`). Remove the now-empty `ports:` key each time. Leave `expose:`/`EXPOSE` and internal `depends_on` untouched — services still reach each other over `eventhub-network`.

- [ ] **Step 2: Verify the base publishes nothing.**

Run: `docker compose -f docker-compose.yml config | grep -c 'published:'`
Expected: `0`

- [ ] **Step 3: Create `docker-compose.override.yml`** re-publishing the debug ports to loopback:

```yaml
# Local-dev port publishing. Docker Compose auto-loads this file on
# `docker compose up`. Bound to 127.0.0.1 so debug ports never reach the LAN.
# Production does NOT name this file — the base (docker-compose.yml) publishes
# nothing; only Caddy (docker-compose.caddy.yml) exposes 80/443.
services:
  eureka-server:
    ports: ["127.0.0.1:8761:8761"]
  api-gateway:
    ports: ["127.0.0.1:8180:8080"]
  user-service:
    ports: ["127.0.0.1:8082:8082"]
  tag-service:
    ports: ["127.0.0.1:8087:8087"]
  search-service:
    ports: ["127.0.0.1:8085:8085"]
  like-service:
    ports: ["127.0.0.1:8084:8084"]
  registration-service:
    ports: ["127.0.0.1:8089:8089"]
  audit-service:
    ports: ["127.0.0.1:8091:8091"]
  notification-service:
    ports: ["127.0.0.1:8086:8086"]
  file-service:
    ports: ["127.0.0.1:8088:8088"]
  minio:
    ports: ["127.0.0.1:9100:9000", "127.0.0.1:9101:9001"]
  postgres-users:
    ports: ["127.0.0.1:5437:5432"]
  postgres-events:
    ports: ["127.0.0.1:5434:5432"]
  postgres-likes:
    ports: ["127.0.0.1:5435:5432"]
  postgres-registrations:
    ports: ["127.0.0.1:5439:5432"]
  postgres-notifications:
    ports: ["127.0.0.1:5436:5432"]
  elasticsearch:
    ports: ["127.0.0.1:9200:9200"]
  redis:
    ports: ["127.0.0.1:6380:6379"]
  zookeeper:
    ports: ["127.0.0.1:2181:2181"]
  kafka:
    ports: ["127.0.0.1:9092:9092", "127.0.0.1:29092:29092"]
  kafka-ui:
    ports: ["127.0.0.1:8090:8080"]
  prometheus:
    ports: ["127.0.0.1:9090:9090"]
  grafana:
    ports: ["127.0.0.1:3000:3000"]
  zipkin:
    ports: ["127.0.0.1:9411:9411"]
```

- [ ] **Step 4: Verify the override restores dev ports on loopback.**

Run: `docker compose config | grep -A2 '8180'`
Expected: shows `published: "8180"` with `host_ip: 127.0.0.1` (compose auto-merged the override).

- [ ] **Step 5: Verify the running stack still works locally.** The stack is already up; recreate to apply the compose change surgically is heavy — instead confirm the merged config is correct (Step 4) and that an existing published port still answers:

Run: `curl -sf http://localhost:8180/api/events?page=0\&size=1 >/dev/null && echo "app OK"`
Expected: `app OK`. (Full `docker compose up -d` to realize the override is fine but not required for this task's verification — the config merge is the deliverable.)

- [ ] **Step 6: Commit**

```bash
git add docker-compose.yml docker-compose.override.yml
git commit -m "feat(infra): secure-by-default base compose, dev ports via override"
```

---

### Task 4: Caddy production compose file + Caddyfile

**Files:**
- Create: `docker-compose.caddy.yml`
- Create: `Caddyfile`

**Interfaces:**
- Consumes: the secure base (Task 3) — Caddy reaches `api-gateway:8080` over `eventhub-network`.
- Produces: production entrypoint. `docker compose -f docker-compose.yml -f docker-compose.caddy.yml up -d` serves the frontend + proxies `/api`,`/auth` on one HTTPS origin, blocks `/actuator`, adds security headers.

- [ ] **Step 1: Create `Caddyfile`.** Domain and TLS email from env (Caddy expands `{$VAR}`):

```
{$APP_DOMAIN} {
	encode gzip

	# Block the actuator surface at the proxy (fuller reduction is set 2).
	respond /actuator/* 403

	# API + auth → gateway over the internal network.
	handle /api/* {
		reverse_proxy api-gateway:8080
	}
	handle /auth/* {
		reverse_proxy api-gateway:8080
	}

	# Everything else → the built SPA, with client-side routing fallback.
	handle {
		root * /srv
		try_files {path} /index.html
		file_server
	}

	header {
		Strict-Transport-Security "max-age=31536000; includeSubDomains"
		X-Content-Type-Options "nosniff"
		X-Frame-Options "DENY"
		Referrer-Policy "strict-origin-when-cross-origin"
		-Server
	}
}
```

- [ ] **Step 2: Create `docker-compose.caddy.yml`.** Caddy is the only container publishing host ports; it serves the pre-built `frontend/dist` and shares `eventhub-network`:

```yaml
# Production overlay: adds Caddy (TLS reverse proxy + static SPA host).
# Deploy: docker compose -f docker-compose.yml -f docker-compose.caddy.yml up -d
# Requires: frontend built to ./frontend/dist and APP_DOMAIN set in .env.prod.
services:
  caddy:
    image: caddy:2-alpine
    container_name: caddy
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    environment:
      - APP_DOMAIN=${APP_DOMAIN}
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - ./frontend/dist:/srv:ro
      - caddy-data:/data
      - caddy-config:/config
    depends_on:
      - api-gateway
    networks:
      - eventhub-network
    logging: *default-logging

volumes:
  caddy-data:
  caddy-config:
```

Note: `*default-logging` is a YAML anchor defined at the top of `docker-compose.yml`. Anchors do NOT cross files in Docker Compose. So in `docker-compose.caddy.yml`, replace `logging: *default-logging` with the inline equivalent:

```yaml
    logging:
      driver: "json-file"
      options:
        max-size: "50m"
        max-file: "3"
```

- [ ] **Step 3: Local rehearsal with a self-signed cert.** Let's Encrypt can't issue for a fake domain, so rehearse with Caddy's internal CA. Build the frontend and bring up a caddy-only test against the running stack:

```bash
cd frontend && npx vite build && cd ..
# Temporary local Caddyfile using the internal CA on localhost:
printf 'localhost {\n\ttls internal\n\trespond /actuator/* 403\n\thandle /api/* { reverse_proxy localhost:8180 }\n\thandle { root * %s/frontend/dist\n\t\ttry_files {path} /index.html\n\t\tfile_server }\n\theader { X-Frame-Options "DENY" }\n}\n' "$PWD" > /tmp/Caddyfile.local
docker run --rm -d --name caddy-test --network host \
  -v /tmp/Caddyfile.local:/etc/caddy/Caddyfile:ro \
  -v "$PWD/frontend/dist":"$PWD/frontend/dist":ro caddy:2-alpine
sleep 3
```

- [ ] **Step 4: Verify the proxy behavior.**

```bash
curl -k -s -o /dev/null -w "spa: %{http_code}\n" https://localhost/
curl -k -s -o /dev/null -w "actuator: %{http_code}\n" https://localhost/actuator/health
curl -k -s -o /dev/null -w "api: %{http_code}\n" "https://localhost/api/events?page=0&size=1"
curl -k -sI https://localhost/ | grep -i "x-frame-options"
docker rm -f caddy-test
```
Expected: `spa: 200`, `actuator: 403`, `api: 200`, and an `X-Frame-Options: DENY` header line.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.caddy.yml Caddyfile
git commit -m "feat(infra): Caddy prod overlay — TLS, same-origin SPA+API, actuator block"
```

---

### Task 5: Env templates, gitignore, prod deploy docs

**Files:**
- Modify: `.env.example` — add the new keys
- Create: `.env.prod.example`
- Modify: `.gitignore` — ensure `.env.prod` is ignored
- Modify: `README.md` — a "Production deployment" section + secret-rotation checklist

**Interfaces:**
- Produces: documented, reproducible prod configuration. Consumed by the human operator and the smoke test (Task 6).

- [ ] **Step 1: Add keys to `.env.example`.** Append:

```
# Redis auth (leave empty for local dev; set a strong value in prod)
REDIS_PASSWORD=

# Comma-separated CORS origins for local dev (prod uses same-origin, leave unset)
APP_CORS_ORIGINS=http://localhost:5173,http://localhost:3000

# Public domain for the Caddy prod deploy (prod only)
APP_DOMAIN=eventhub.kz
```

- [ ] **Step 2: Create `.env.prod.example`** (placeholders only — never real secrets):

```
# Production secrets. Copy to .env.prod, fill with STRONG generated values,
# chmod 600, and NEVER commit. Deploy with:
#   docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.caddy.yml up -d

# Database
DB_USER=postgres
DB_PASSWORD=CHANGE-ME-strong-random

# JWT — generate: openssl rand -base64 32   (ROTATE: was briefly in git history)
JWT_SECRET=CHANGE-ME-base64-32-bytes

# Redis
REDIS_PASSWORD=CHANGE-ME-strong-random

# MinIO / S3 (compose maps these to MINIO_ROOT_USER/PASSWORD and file-service creds)
AWS_ACCESS_KEY=CHANGE-ME-minio-user
AWS_SECRET_KEY=CHANGE-ME-strong-random
AWS_REGION=eu-north-1
AWS_S3_BUCKET=eventhubkz-files
AWS_S3_ENDPOINT=http://minio:9000
AWS_S3_PUBLIC_URL=https://eventhub.kz/files

# Mail (ROTATE the Gmail app-password: was briefly in git history)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=you@example.com
MAIL_PASSWORD=CHANGE-ME-app-password

# Gemini (ROTATE: key was in the local .env)
GEMINI_API_KEY=CHANGE-ME

# Grafana
GRAFANA_PASSWORD=CHANGE-ME-strong-random

# Public domain (drives Caddy TLS)
APP_DOMAIN=eventhub.kz
# CORS unset in prod (same-origin behind Caddy)
APP_CORS_ORIGINS=
```

- [ ] **Step 3: Ensure `.env.prod` is gitignored.** Check `.gitignore` — it has `.env` and `.env.local`. Add `.env.prod` if not covered:

```
.env.prod
```

Run: `git check-ignore .env.prod`
Expected: prints `.env.prod` (confirms ignored).

- [ ] **Step 4: Add the README "Production deployment" section.** After the existing "Getting started" section, add:

```markdown
## Production deployment

Local dev publishes debug ports to `127.0.0.1` via `docker-compose.override.yml`
(auto-loaded). Production publishes **only** Caddy's 80/443 — every service and
database stays on the internal network.

1. Point your domain's A record at the server; set `APP_DOMAIN` in `.env.prod`.
2. Copy `.env.prod.example` → `.env.prod`, fill STRONG values, `chmod 600 .env.prod`.
   **Rotate** the three secrets that were briefly in git history: `JWT_SECRET`
   (`openssl rand -base64 32`), the Gmail app-password, and the Gemini API key.
3. Build the frontend for same-origin API: `cd frontend && npx vite build` (with
   `VITE_API_URL=/api`).
4. Deploy:
   ```bash
   docker compose --env-file .env.prod \
     -f docker-compose.yml -f docker-compose.caddy.yml up -d --build
   ```
   Caddy obtains a Let's Encrypt certificate automatically on first request.
5. Verify with `scripts/prod-smoke.sh <domain>` (see below).

Set 1 hardens network isolation, TLS, and infra credentials. Elasticsearch
`xpack.security` stays disabled by design — network isolation removes its
exposure; revisit if ES ever leaves the internal network. Rate limiting, inbound
`X-User-*` stripping, refresh-token revocation, and Flyway are tracked as set 2/3.
```

- [ ] **Step 5: Note the VITE_API_URL prod value.** In `frontend/.env` (or documented in the README step above), the prod build needs `VITE_API_URL=/api`. Confirm the frontend reads `import.meta.env.VITE_API_URL` in `frontend/src/api.js` and that a relative `/api` base works (same-origin). If `api.js` hardcodes a host, note it in the report; otherwise no code change — it's a build-time env value already documented in Step 4.

- [ ] **Step 6: Commit**

```bash
git add .env.example .env.prod.example .gitignore README.md
git commit -m "docs: production deploy guide, env templates, secret rotation"
```

---

### Task 6: Production smoke-test script

**Files:**
- Create: `scripts/prod-smoke.sh`

**Interfaces:**
- Consumes: a deployed prod stack (Tasks 3–5).
- Produces: an acceptance script proving isolation + TLS + end-to-end. This is the spec's acceptance test.

- [ ] **Step 1: Write `scripts/prod-smoke.sh`.**

```bash
#!/usr/bin/env bash
# Production hardening acceptance test.
# Usage: scripts/prod-smoke.sh <domain> [--insecure]
#   --insecure  use curl -k (local rehearsal with Caddy 'tls internal')
set -uo pipefail

DOMAIN="${1:?usage: prod-smoke.sh <domain> [--insecure]}"
CURL_TLS=""
[ "${2:-}" = "--insecure" ] && CURL_TLS="-k"

fail=0
ok()   { echo "  ✓ $*"; }
bad()  { echo "  ✗ $*"; fail=1; }

echo "== 1. Isolation: debug ports must NOT answer from the host =="
for port in 8082 8091 8084 8089 8086 8088 8087 5434 9200 8090 3000 6380; do
  if timeout 2 bash -c "</dev/tcp/${DOMAIN}/${port}" 2>/dev/null; then
    bad "port ${port} is reachable (should be closed)"
  else
    ok "port ${port} closed"
  fi
done

echo "== 2. HTTP redirects to HTTPS =="
code=$(curl -s -o /dev/null -w '%{http_code}' "http://${DOMAIN}/" || echo 000)
case "$code" in 301|302|308) ok "HTTP → HTTPS ($code)";; *) bad "expected redirect, got $code";; esac

echo "== 3. HTTPS serves the SPA =="
code=$(curl $CURL_TLS -s -o /dev/null -w '%{http_code}' "https://${DOMAIN}/")
[ "$code" = "200" ] && ok "SPA 200" || bad "SPA got $code"

echo "== 4. Actuator blocked at the proxy =="
code=$(curl $CURL_TLS -s -o /dev/null -w '%{http_code}' "https://${DOMAIN}/actuator/health")
[ "$code" = "403" ] && ok "actuator 403" || bad "actuator got $code (expected 403)"

echo "== 5. End-to-end: public catalog through Caddy =="
code=$(curl $CURL_TLS -s -o /dev/null -w '%{http_code}' "https://${DOMAIN}/api/events?page=0&size=1")
[ "$code" = "200" ] && ok "catalog 200" || bad "catalog got $code"

echo "== 6. Security headers present =="
hdrs=$(curl $CURL_TLS -sI "https://${DOMAIN}/")
echo "$hdrs" | grep -qi "x-frame-options: DENY" && ok "X-Frame-Options" || bad "missing X-Frame-Options"
echo "$hdrs" | grep -qi "x-content-type-options: nosniff" && ok "X-Content-Type-Options" || bad "missing X-Content-Type-Options"

echo
[ "$fail" = 0 ] && echo "ALL CHECKS PASSED" || { echo "SOME CHECKS FAILED"; exit 1; }
```

- [ ] **Step 2: Make it executable and syntax-check.**

Run: `chmod +x scripts/prod-smoke.sh && bash -n scripts/prod-smoke.sh && echo "syntax OK"`
Expected: `syntax OK`

- [ ] **Step 3: Local rehearsal (optional but recommended).** With the caddy-test container from Task 4 Step 3 running against `localhost` with `tls internal`, run a reduced check (checks 3–6 apply; port-isolation check 1 is only meaningful on the real host):

Run: `scripts/prod-smoke.sh localhost --insecure` (expect checks 3,4,5,6 to pass; the port-isolation section will report ports closed since only Caddy is bound — acceptable locally).

- [ ] **Step 4: Commit**

```bash
git add scripts/prod-smoke.sh
git commit -m "test(infra): production hardening acceptance smoke script"
```

---

## Self-Review Notes

- **Spec coverage:** three-file compose model (T3, T4), Caddy+TLS+same-origin+headers+actuator-block (T4), CORS-from-env (T1), Redis password (T2), MinIO/Postgres strong creds (T5 env templates — compose already reads `AWS_ACCESS_KEY/SECRET` and `DB_PASSWORD` from env, so no compose change needed for those two), ES-xpack-deferred (documented in T5 README), secret-rotation checklist (T5), acceptance smoke test (T6). All spec sections map to a task.
- **Config-not-code caveat:** only Task 1 has true unit tests; Tasks 2–6 are infra/config verified by concrete commands (compose-config assertions, redis auth probe, curl through Caddy) — this matches the spec's "acceptance test, not unit tests" stance.
- **Load-bearing ordering:** T3 (strip ports) must land with T4 available or the prod path has no entry — but T3's own deliverable (base publishes nothing, override restores dev) is independently testable via `docker compose config`, so they stay separate tasks.
- **Anchor gotcha:** YAML anchors don't cross compose files — T4 inlines the logging block (called out in the task).
- **Rebuild gotcha:** T2 changes `application.yml` files baked into jars — the 5 services must be rebuilt+redeployed, not just restarted (called out in T2 Step 4).
