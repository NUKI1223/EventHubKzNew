# Security Hardening Set 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add gateway rate limiting on `/auth` and AI endpoints, and deterministically strip inbound `X-User-*` headers so no client can spoof identity to a downstream service.

**Architecture:** All gateway-side except one small frontend touch. A `GlobalFilter` at `HIGHEST_PRECEDENCE` strips the five `X-User-*` headers from every inbound request before `AuthenticationFilter` runs. Two `KeyResolver` beans (IP-based for `/auth`, user-based for AI) feed Spring Cloud Gateway's Redis `RequestRateLimiter` on dedicated routes. The frontend surfaces HTTP 429 as a toast.

**Tech Stack:** Spring Cloud Gateway (reactive), `RequestRateLimiter` + Redis token bucket (redis-reactive already present), React + axios + react-i18next, bash.

**Spec:** `docs/superpowers/specs/2026-07-07-security-hardening-set2-design.md`

## Global Constraints

- Run Maven **from the repo root** with `-pl api-gateway`; never from inside a module dir.
- Commit messages: lowercase conventional commits (`feat(scope): ...`); **no Co-Authored-By / attribution lines.**
- Docker redeploy recipe: `mvn clean package -DskipTests -pl api-gateway` → `docker compose build api-gateway` → `docker rm -f api-gateway` → `docker compose up -d api-gateway`. The stack is LIVE; the gateway is reachable at `localhost:8180` (dev override).
- The five headers stripped/managed: `X-User-Id`, `X-User-Name`, `X-Username`, `X-User-Email`, `X-User-Role`.
- Rate limits (replenishRate / burstCapacity / requestedTokens): `/auth/login`,`/auth/signup` = 5 / 10 / 1 (IP); `/auth/resend` = 1 / 3 / 1 (IP); `/api/events/suggest-tags` POST = 1 / 5 / 1 (user); `/api/support/chat` POST = 1 / 5 / 1 (user).
- `X-Forwarded-For` is trusted only because the gateway sits behind Caddy; the IP resolver falls back to the socket remote address when the header is absent (local dev), and to the literal `"unknown"` as a last resort (never returns empty — `deny-empty-key` stays `true`).
- Dedicated `/auth` rate-limited routes MUST keep `RewritePath=/auth/(?<segment>.*), /api/auth/${segment}`, or auth breaks.
- Route match order matters: dedicated routes are listed BEFORE the general routes they carve out of.
- On AI routes filter order is `AuthenticationFilter` before `RequestRateLimiter` so `X-User-Id` is populated for the user key resolver.

---

### Task 1: StripUserHeadersGlobalFilter

**Files:**
- Create: `api-gateway/src/main/java/org/ngcvfb/apigateway/filter/StripUserHeadersGlobalFilter.java`
- Test: `api-gateway/src/test/java/org/ngcvfb/apigateway/filter/StripUserHeadersGlobalFilterTest.java`

**Interfaces:**
- Produces: a `@Component` `GlobalFilter` + `Ordered` (HIGHEST_PRECEDENCE) that removes the five `X-User-*` headers from every inbound request. No later task references it by name (Spring wires it globally); it simply must run before `AuthenticationFilter`.

- [ ] **Step 1: Write the failing test**

```java
package org.ngcvfb.apigateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class StripUserHeadersGlobalFilterTest {

    private final StripUserHeadersGlobalFilter filter = new StripUserHeadersGlobalFilter();

    @Test
    void removesAllSpoofedUserHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/events")
                .header("X-User-Id", "999")
                .header("X-User-Role", "ADMIN")
                .header("X-User-Email", "evil@x.kz")
                .header("X-User-Name", "evil")
                .header("X-Username", "evil")
                .header("X-Trace", "keep-me")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            forwarded.set(ex.getRequest());
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        var headers = forwarded.get().getHeaders();
        assertThat(headers.containsKey("X-User-Id")).isFalse();
        assertThat(headers.containsKey("X-User-Role")).isFalse();
        assertThat(headers.containsKey("X-User-Email")).isFalse();
        assertThat(headers.containsKey("X-User-Name")).isFalse();
        assertThat(headers.containsKey("X-Username")).isFalse();
        assertThat(headers.getFirst("X-Trace")).isEqualTo("keep-me"); // unrelated headers untouched
    }

    @Test
    void runsAtHighestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -pl api-gateway -Dtest=StripUserHeadersGlobalFilterTest`
Expected: COMPILATION ERROR (`StripUserHeadersGlobalFilter` does not exist)

- [ ] **Step 3: Implement**

```java
package org.ngcvfb.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Удаляет клиентские X-User-* из КАЖДОГО входящего запроса до любого роут-фильтра.
 * AuthenticationFilter затем ставит подлинные заголовки на авторизованных роутах;
 * публичные роуты доходят до downstream без этих заголовков — подделать личность нельзя.
 */
@Component
public class StripUserHeadersGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> USER_HEADERS = List.of(
            "X-User-Id", "X-User-Name", "X-Username", "X-User-Email", "X-User-Role");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> USER_HEADERS.forEach(h::remove))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -pl api-gateway -Dtest=StripUserHeadersGlobalFilterTest`
Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add api-gateway/src
git commit -m "feat(gateway): strip inbound X-User-* headers at highest precedence"
```

---

### Task 2: Rate-limit key resolvers

**Files:**
- Create: `api-gateway/src/main/java/org/ngcvfb/apigateway/config/RateLimitConfig.java`
- Test: `api-gateway/src/test/java/org/ngcvfb/apigateway/config/RateLimitConfigTest.java`

**Interfaces:**
- Produces: two `@Bean KeyResolver` — `ipKeyResolver` (name `ipKeyResolver`) and `userKeyResolver` (name `userKeyResolver`). Task 3's route config references them by SpEL bean name (`#{@ipKeyResolver}`, `#{@userKeyResolver}`), so the bean method names are load-bearing and must match exactly.

- [ ] **Step 1: Write the failing test**

```java
package org.ngcvfb.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigTest {

    private final RateLimitConfig config = new RateLimitConfig();
    private final KeyResolver ip = config.ipKeyResolver();
    private final KeyResolver user = config.userKeyResolver();

    @Test
    void ipResolverPrefersFirstXForwardedForEntry() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"));
        assertThat(ip.resolve(exchange).block()).isEqualTo("203.0.113.7");
    }

    @Test
    void ipResolverFallsBackToRemoteAddressWhenNoHeader() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login").remoteAddress(
                        new java.net.InetSocketAddress("192.168.1.50", 12345)));
        assertThat(ip.resolve(exchange).block()).isEqualTo("192.168.1.50");
    }

    @Test
    void ipResolverNeverReturnsEmpty() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/auth/login"));
        String key = ip.resolve(exchange).block();
        assertThat(key).isNotBlank();
    }

    @Test
    void userResolverUsesUserIdHeader() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/support/chat").header("X-User-Id", "42"));
        assertThat(user.resolve(exchange).block()).isEqualTo("42");
    }

    @Test
    void userResolverFallsBackToIpWhenNoUserId() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/support/chat")
                        .header("X-Forwarded-For", "203.0.113.9"));
        assertThat(user.resolve(exchange).block()).isEqualTo("203.0.113.9");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -pl api-gateway -Dtest=RateLimitConfigTest`
Expected: COMPILATION ERROR (`RateLimitConfig` does not exist)

- [ ] **Step 3: Implement**

```java
package org.ngcvfb.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    // За Caddy реальный IP клиента — в X-Forwarded-For (первый элемент), а не в сокете.
    // В dev без Caddy заголовка нет — падаем на remoteAddress, в крайнем случае "unknown".
    static String clientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        var addr = exchange.getRequest().getRemoteAddress();
        if (addr != null && addr.getAddress() != null) {
            return addr.getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(clientIp(exchange));
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            return Mono.just(userId != null && !userId.isBlank() ? userId : clientIp(exchange));
        };
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -pl api-gateway -Dtest=RateLimitConfigTest`
Expected: `Tests run: 5, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add api-gateway/src
git commit -m "feat(gateway): IP and user key resolvers for rate limiting"
```

---

### Task 3: Rate-limited gateway routes

**Files:**
- Modify: `api-gateway/src/main/resources/application.yml` (add 4 dedicated routes before the routes they carve out of)

**Interfaces:**
- Consumes: `ipKeyResolver`, `userKeyResolver` beans (Task 2); `AuthenticationFilter` (existing); `StripUserHeadersGlobalFilter` (Task 1, global — no route reference needed).
- Produces: HTTP 429 on limit breach for the four endpoint groups.

- [ ] **Step 1: Add the two `/auth` rate-limited routes BEFORE the existing `auth-service` route.** In `api-gateway/src/main/resources/application.yml`, the `auth-service` route (`Path=/auth/**`, with `RewritePath`) is the first route under `routes:`. Insert these two routes immediately before it (they must precede it so the specific paths match first), keeping the `RewritePath` filter on each:

```yaml
        # Auth - login/signup: rate limit per client IP (brute-force guard)
        - id: auth-login-signup-rl
          uri: lb://AUTH-SERVICE
          predicates:
            - Path=/auth/login,/auth/signup
          filters:
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@ipKeyResolver}"
                redis-rate-limiter.replenishRate: 5
                redis-rate-limiter.burstCapacity: 10
                redis-rate-limiter.requestedTokens: 1
            - RewritePath=/auth/(?<segment>.*), /api/auth/${segment}

        # Auth - resend: strict rate limit per IP (email-bomb guard)
        - id: auth-resend-rl
          uri: lb://AUTH-SERVICE
          predicates:
            - Path=/auth/resend
          filters:
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@ipKeyResolver}"
                redis-rate-limiter.replenishRate: 1
                redis-rate-limiter.burstCapacity: 3
                redis-rate-limiter.requestedTokens: 1
            - RewritePath=/auth/(?<segment>.*), /api/auth/${segment}
```

The existing `auth-service` route (`Path=/auth/**`) stays where it is, now catching only the remaining `/auth/*` traffic (e.g. `/auth/verify`).

- [ ] **Step 2: Add the `suggest-tags` AI route BEFORE `event-service-write`.** The `event-service-write` route matches `Path=/api/events/**` + `Method=POST,PUT,DELETE`. Insert this dedicated route immediately before it:

```yaml
        # AI - tag suggestion: authenticated, rate limit per user (Gemini quota guard)
        - id: ai-suggest-tags-rl
          uri: lb://EVENT-SERVICE
          predicates:
            - Path=/api/events/suggest-tags
            - Method=POST
          filters:
            - name: AuthenticationFilter
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@userKeyResolver}"
                redis-rate-limiter.replenishRate: 1
                redis-rate-limiter.burstCapacity: 5
                redis-rate-limiter.requestedTokens: 1
            - name: CircuitBreaker
              args:
                name: eventServiceCircuitBreaker
                fallbackUri: forward:/fallback/events
```

(The `CircuitBreaker` is kept so this route retains the same Gemini-call protection the general `event-service-write` route provided before the carve-out. `AuthenticationFilter` is first so `X-User-Id` is populated before the rate limiter's user key resolver runs.)

- [ ] **Step 3: Add the `support/chat` AI route BEFORE `support-service`.** The `support-service` route matches `Path=/api/support/**` with `AuthenticationFilter`. Insert immediately before it:

```yaml
        # AI - support chat: authenticated, rate limit per user (Gemini quota guard)
        - id: ai-support-chat-rl
          uri: lb://EVENT-SERVICE
          predicates:
            - Path=/api/support/chat
            - Method=POST
          filters:
            - name: AuthenticationFilter
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@userKeyResolver}"
                redis-rate-limiter.replenishRate: 1
                redis-rate-limiter.burstCapacity: 5
                redis-rate-limiter.requestedTokens: 1
```

- [ ] **Step 4: Rebuild + redeploy the gateway** (yaml is baked into the jar):

```bash
mvn clean package -DskipTests -pl api-gateway
docker compose build api-gateway
docker rm -f api-gateway
docker compose up -d api-gateway
sleep 25
curl -sf http://localhost:8180/actuator/health >/dev/null && echo "gateway up"
```
Expected: `gateway up`.

- [ ] **Step 5: Verify rate limiting is active (live integration).** Hammer login past the burst and observe 429s:

```bash
echo "login burst (expect a run of non-429 then 429s):"
for i in $(seq 1 20); do
  curl -s -o /dev/null -w '%{http_code} ' -X POST http://localhost:8180/auth/login \
    -H 'Content-Type: application/json' -d '{"email":"x@x.kz","password":"x"}'
done; echo
```
Expected: the first several return 401/400 (auth rejects bad creds), then `429`s appear once the burst (10) is exhausted. Also confirm a normal single request still works:
```bash
curl -s -o /dev/null -w 'catalog: %{http_code}\n' "http://localhost:8180/api/events?page=0&size=1"
```
Expected: `catalog: 200` (unrelated route, not rate limited).

- [ ] **Step 6: Verify auth still works end-to-end (RewritePath preserved).**

```bash
curl -s -X POST http://localhost:8180/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"dinara.zhumabaeva@example.kz","password":"password123"}' \
  -o /dev/null -w 'real login: %{http_code}\n'
```
Expected: `real login: 200` (proves the dedicated route's `RewritePath` still routes to `/api/auth/login`).

- [ ] **Step 7: Commit**

```bash
git add api-gateway/src/main/resources/application.yml
git commit -m "feat(gateway): rate-limit auth and AI endpoints (IP / per-user keys)"
```

---

### Task 4: Frontend 429 toast

**Files:**
- Modify: `frontend/src/api.js` (response interceptor: 429 → toast)
- Modify: `frontend/src/i18n/locales/ru.json`, `frontend/src/i18n/locales/kk.json` (add `common.rateLimited`)

**Interfaces:**
- Consumes: the gateway's 429 responses (Task 3). Uses the existing `react-hot-toast` singleton and the `i18n` instance (both usable outside React components).

- [ ] **Step 1: Add the i18n key.** Append inside the existing `"common"` object of `frontend/src/i18n/locales/ru.json` (text edit — do NOT round-trip through a JSON serializer, it reformats the file):

```json
    "rateLimited": "Слишком много запросов, попробуйте позже"
```

And in `kk.json`'s `"common"` object:

```json
    "rateLimited": "Сұраныстар тым көп, сәл кейінірек көріңіз"
```

(If `common` already ends with another key, add a comma to that line first so the JSON stays valid. Validate: `node -e "JSON.parse(require('fs').readFileSync('frontend/src/i18n/locales/ru.json'))"` and the same for kk.json.)

- [ ] **Step 2: Read `frontend/src/api.js`** to find the existing axios response interceptor (there is auth-expiry handling around the request/response). Add a 429 branch to the response interceptor's error handler. If a response interceptor exists, extend its rejected-handler; if only a request interceptor exists, add a response interceptor. The added logic:

```javascript
import toast from 'react-hot-toast';
import i18n from './i18n';

// inside the response interceptor's error handler, before the existing return/reject:
if (error.response && error.response.status === 429) {
  toast.error(i18n.t('common.rateLimited'));
}
```

Keep all existing interceptor behavior (auth-expiry logout, etc.) intact — only add the 429 branch. Confirm the import path for the i18n instance matches how the app initializes it (`frontend/src/i18n/index.js` exports the configured instance; import it as `i18n` — check the actual default export and adjust the path/name if it differs).

- [ ] **Step 3: Build to verify.**

Run: `cd frontend && npx vite build`
Expected: `✓ built` with no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api.js frontend/src/i18n/locales/ru.json frontend/src/i18n/locales/kk.json
git commit -m "feat(frontend): show a toast when the gateway returns 429"
```

---

### Task 5: Acceptance check in prod-smoke.sh

**Files:**
- Modify: `scripts/prod-smoke.sh` (add a rate-limit burst check)

**Interfaces:**
- Consumes: a deployed stack with rate limiting (Task 3).
- Produces: a smoke check asserting the limiter fires in prod.

- [ ] **Step 1: Add a rate-limit check** to `scripts/prod-smoke.sh`, after the existing check 6 (security headers) and before the final pass/fail gate. It bursts the login endpoint and asserts at least one 429 appears:

```bash
echo "== 7. Rate limiting active (login burst yields 429) =="
saw429=0
for i in $(seq 1 25); do
  code=$(curl $CURL_TLS --connect-timeout 5 --max-time 10 -s -o /dev/null -w '%{http_code}' \
    -X POST "https://${DOMAIN}/auth/login" -H 'Content-Type: application/json' -d '{}')
  [ "$code" = "429" ] && { saw429=1; break; }
done
[ "$saw429" = 1 ] && ok "rate limiter returned 429 under burst" || bad "no 429 seen in 25 login attempts"
```

(It reuses the script's existing `$CURL_TLS`, `ok`, `bad`, and `$DOMAIN` from the earlier checks.)

- [ ] **Step 2: Syntax-check.**

Run: `bash -n scripts/prod-smoke.sh && echo "syntax OK"`
Expected: `syntax OK`

- [ ] **Step 3: Optional local rehearsal.** If the dev gateway is up on `localhost:8180`, the same burst logic works without TLS — confirm the limiter fires:

```bash
for i in $(seq 1 25); do
  curl -s -o /dev/null -w '%{http_code} ' -X POST http://localhost:8180/auth/login \
    -H 'Content-Type: application/json' -d '{}'
done; echo
```
Expected: at least one `429` in the output.

- [ ] **Step 4: Commit**

```bash
git add scripts/prod-smoke.sh
git commit -m "test(infra): smoke check that rate limiting returns 429"
```

---

## Self-Review Notes

- **Spec coverage:** strip filter (T1), key resolvers with XFF/fallback (T2), the four rate-limited routes with correct keys/limits/order + RewritePath preservation + AI filter order (T3), frontend 429 toast RU/KK (T4), acceptance check (T5). Actuator item correctly absent (dropped in spec). All spec sections map to a task.
- **Testable-vs-config split:** T1 and T2 have real unit tests (MockServerWebExchange). T3 is config verified by live integration (429 under burst) since the Redis limiter can't be unit-tested. T4 is build-verified. T5 is a bash check.
- **Load-bearing details called out:** bean names `ipKeyResolver`/`userKeyResolver` must match the SpEL refs in T3; `RewritePath` preserved on the dedicated `/auth` routes; dedicated routes listed before the general ones; `AuthenticationFilter` before `RequestRateLimiter` on AI routes; the IP resolver never returns empty (so `deny-empty-key` never 403s).
- **Rebuild gotcha:** T3 changes `application.yml` baked into the gateway jar — rebuild+redeploy, not restart (called out in T3 Step 4).
