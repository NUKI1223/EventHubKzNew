# Security hardening set 2 — design

Date: 2026-07-07. Status: approved by user.

## Goal

Close two remaining pre-prod gaps from the 2026-07-06 security audit at the API
gateway: (1) no rate limiting on authentication and AI endpoints, and (2) inbound
`X-User-*` headers passing through untouched on public routes (a latent
privilege-spoofing landmine). Both are gateway-only changes plus a small
frontend touch.

This is **set 2 of the security roadmap** (set 1 = network isolation/TLS/secrets,
shipped). The roadmap's original third item — reducing actuator exposure — was
**dropped after review**: set 1 already made actuator internal-only (ports
stripped, Caddy 403s the gateway's), the include list already excludes the
dangerous endpoints (no `env`/`heapdump`/`shutdown`/`threaddump`), and
`prometheus`/`metrics` are required by the monitoring stack. Reducing further
would add zero security and break Grafana. YAGNI.

## Decisions made with the user

- Rate-limit keying: `/auth/*` keyed by client IP (unauthenticated → IP is all we
  have; brute-force / email-bomb surface); AI endpoints keyed by userId (fair
  per-user quota, NAT-safe).
- Default limits accepted (table below).
- Actuator item dropped from scope.

## 1. Strip inbound `X-User-*` at the gateway

**Problem:** the gateway injects `X-User-Id/-Name/-Email/-Role` (and `X-Username`)
on authenticated routes via `AuthenticationFilter`, and downstream services trust
those headers. On authenticated routes `.header(name, value)` *replaces* any
client-supplied value, so spoofing through the gateway is already blocked there.
But **public routes** (no `AuthenticationFilter`) pass inbound `X-User-*` through
untouched. No public controller reads a role from these headers today, so there
is no live exploit — but it is a latent landmine: one future public endpoint that
trusts `X-User-Role` becomes an instant privilege escalation.

**Solution:** a new `StripUserHeadersGlobalFilter` in `api-gateway`, implementing
`GlobalFilter` + `Ordered` with `Ordered.HIGHEST_PRECEDENCE`. It removes all five
headers (`X-User-Id`, `X-User-Name`, `X-Username`, `X-User-Email`, `X-User-Role`)
from every inbound request before any route filter runs. `AuthenticationFilter`
(later in the chain) then adds the authentic values on authenticated routes;
public routes reach downstream with the headers absent.

**Why a global filter, not `default-filters: RemoveRequestHeader`:** a global
filter with `HIGHEST_PRECEDENCE` runs deterministically before `AuthenticationFilter`,
independent of the subtle ordering rules that govern default-filters vs custom
gateway filters. Single, testable, order-guaranteed.

**Unit-testable:** the filter takes an exchange with spoofed `X-User-*` headers
and asserts the mutated request forwarded to the chain has none of them.

## 2. Rate limiting (Spring Cloud Gateway `RequestRateLimiter` + Redis)

`api-gateway` already depends on `spring-boot-starter-data-redis-reactive`, which
backs Spring Cloud Gateway's Redis token-bucket `RequestRateLimiter`. Redis is
already password-wired (set 1 Task 2).

### Key resolvers (two `KeyResolver` beans)

- **`ipKeyResolver`** — returns the client IP. Reads the **first** entry of
  `X-Forwarded-For` (behind Caddy the real client IP is there; the socket address
  is Caddy's). Falls back to `exchange.getRequest().getRemoteAddress()` when the
  header is absent (local dev without Caddy). Never returns empty (falls back to
  the literal `"unknown"` as a last resort so `deny-empty-key` never 403s a
  legitimate request). Used by `/auth` routes.
- **`userKeyResolver`** — returns `X-User-Id` (populated by `AuthenticationFilter`,
  which runs before `RequestRateLimiter` on the AI routes), falling back to the
  same IP logic when absent. Used by AI routes.

### Routes and limits

New/dedicated routes, each placed **before** the more general route that would
otherwise match, with `RequestRateLimiter` configured:

| Route (predicate) | Key | replenishRate | burstCapacity | requestedTokens |
|---|---|---|---|---|
| `Path=/auth/login,/auth/signup` | IP | 5 | 10 | 1 |
| `Path=/auth/resend` | IP | 1 | 3 | 1 |
| `Path=/api/events/suggest-tags` + `Method=POST` | user | 1 | 5 | 1 |
| `Path=/api/support/chat` + `Method=POST` | user | 1 | 5 | 1 |

- `/auth/login`,`/auth/signup`: 5 req/s sustained, burst 10, per IP — stops
  credential brute-force without hurting real logins.
- `/auth/resend`: 1 req/s, burst 3, per IP — the verification-email endpoint is
  the email-bomb vector, kept strict.
- AI (`suggest-tags`, `support/chat`): 1 req/s, burst 5, per user — nobody
  legitimately calls these several times a second, and it throttles abuse of the
  shared Gemini free quota (1000/day). Both are already `POST`-only and
  authenticated, so `AuthenticationFilter` runs first and `X-User-Id` is present
  for `userKeyResolver`.

The four rate-limiter parameter sets live in `application.yml` (env-overridable),
so thresholds can be tuned without code changes. On limit breach Spring Cloud
Gateway returns **HTTP 429** automatically. `deny-empty-key` stays at its default
(`true`); the resolvers guarantee a non-empty key so no legitimate request is
denied with 403.

### Route ordering / filter ordering

- The dedicated `suggest-tags` and `support/chat` routes must be listed **before**
  the general `event-service-write` (`/api/events/**` POST) and `support-service`
  (`/api/support/**`) routes — Spring Cloud Gateway matches routes in declared
  order.
- On the AI routes the filter list is `[AuthenticationFilter, RequestRateLimiter]`
  so `X-User-Id` is populated before `userKeyResolver` reads it. (The
  `StripUserHeadersGlobalFilter` from section 1 runs first globally, so any
  client-supplied `X-User-Id` is already gone before `AuthenticationFilter`
  injects the authentic one.)

## 3. Frontend: 429 handling

Small addition to `frontend/src/api.js`'s existing axios response interceptor:
catch HTTP 429 and show a toast — «Слишком много запросов, попробуйте позже»
(localized RU + KK via the existing i18n `common` namespace). Without it, hitting
the login or AI limit surfaces as a confusing generic error. This is the only
change outside the gateway.

## 4. Testing and acceptance

- **Unit (gateway):** `StripUserHeadersGlobalFilter` removes all five headers from
  a spoofed request; `ipKeyResolver` parses the first `X-Forwarded-For` entry,
  falls back to remote address, and never returns empty; `userKeyResolver`
  returns `X-User-Id` and falls back to IP. These are the genuinely
  unit-testable units.
- **Integration (manual / documented):** rate-limit enforcement needs a live
  Redis + gateway. Verify by hammering `/auth/login` past the burst and observing
  429s (`for i in $(seq 1 20); do curl -s -o /dev/null -w '%{http_code} '
  http://localhost:8180/auth/login -X POST -H 'Content-Type: application/json'
  -d '{}'; done` → a run of 200/400s then 429s). Documented in the task; not a CI
  gate.
- **Acceptance:** one added check in `scripts/prod-smoke.sh` — a burst of requests
  to a rate-limited path returns at least one 429 (proves limiting is active in
  prod). Best-effort/optional so a cold cache doesn't flake CI.

## 5. Gotchas (carried into the plan)

- **`X-Forwarded-For` trust:** honored only because the gateway sits behind Caddy
  (set 1), which sets it. In local dev without Caddy the header is absent →
  `ipKeyResolver` falls back to the socket remote address. Do not parse
  `X-Forwarded-For` as trusted in an internet-facing deployment that is *not*
  behind the trusted proxy.
- **Redis auth:** the rate limiter uses the same Redis the gateway already
  connects to; the `REDIS_PASSWORD` wiring from set 1 covers it — no new config.
- **Filter order on AI routes:** `AuthenticationFilter` before `RequestRateLimiter`
  (section 2).
- **`/auth` RewritePath must be preserved:** the existing `auth-service` route
  carries `RewritePath=/auth/(?<segment>.*), /api/auth/${segment}` (auth-service
  serves `/api/auth/*`). The new rate-limited `/auth/login,/auth/signup` and
  `/auth/resend` routes must keep that same `RewritePath` filter alongside
  `RequestRateLimiter`, or auth breaks. Order within the route:
  `[RequestRateLimiter, RewritePath]` (limit before rewrite is fine — the limiter
  keys on the pre-rewrite path predicate). The general `auth-service` route stays
  for the remaining `/auth/**` (verify) traffic, listed after the two dedicated
  ones.
- **`deny-empty-key`:** left at default `true`; safe because resolvers never
  return empty.

## Out of scope (later)

- **Set 3:** refresh tokens with revocation + password-change session
  invalidation; Flyway migrations replacing `ddl-auto: update`.
- **Medium:** file-upload magic-byte validation + per-user quota; organizer email
  opt-in; dependency scanning in CI; Elasticsearch `xpack.security` (accepted
  residual risk, network-isolated).
