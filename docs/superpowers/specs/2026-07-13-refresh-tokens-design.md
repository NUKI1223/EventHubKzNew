# SET 3b — Refresh tokens (short access token + revocable refresh in Redis, httpOnly cookie)

Date: 2026-07-13. Status: draft for approval.

## Goal

Today auth issues a single 1-hour JWT access token with no way to revoke it. Add a
refresh-token flow so access tokens can be **short-lived** (15 min) and sessions are
**revocable** (logout / server-side): a leaked access token dies in ≤15 min, and a refresh
token can be killed instantly.

## Decisions (made with the user)

- **Refresh store: Redis with TTL.** auth-service already has `spring-boot-starter-data-redis`.
  Volatility accepted — a Redis flush just forces re-login.
- **Delivery: httpOnly cookie.** The refresh token is never readable by JS (XSS-safe). Prod is
  same-origin behind Caddy, so the cookie flows on same-origin API calls.

## Token model

- **Access token**: unchanged JWT, but TTL shortened to **15 min** (`JWT_EXPIRATION` default
  `900000`). Still stateless; the gateway validates it exactly as now.
- **Refresh token**: an opaque 256-bit random string (high-entropy, like a session id).
  Stored in Redis as `refresh:<token> → <userId>` with TTL = refresh lifetime
  (`REFRESH_EXPIRATION` default `604800000` = 7 days). Delivered as an httpOnly cookie.

## Cookie

`Set-Cookie: refresh_token=<token>; HttpOnly; SameSite=Lax; Path=/auth; Max-Age=<ttlSec>`
plus `Secure` when `COOKIE_SECURE=true` (prod; omitted in dev over http, otherwise the browser
drops it). `Path=/auth` scopes the cookie to auth endpoints only (not sent on every API call).

## Flow

- **POST /auth/login** (existing): on success, additionally mint a refresh token, store it in
  Redis, and `Set-Cookie`. Response body still returns the access token (+ user) as today.
- **POST /auth/refresh** (new): read `refresh_token` cookie → look up in Redis. If valid,
  **rotate**: delete the old key, mint a new refresh token (new Redis key + new cookie), and
  return a fresh access token in the body. If missing/unknown/expired → 401 (a rotated-then-
  reused old token is already gone from Redis → 401, which is basic theft detection).
- **POST /auth/logout** (new): read cookie → delete the Redis key → clear the cookie
  (`Max-Age=0`). Idempotent.

The gateway already routes `/auth/**` publicly and forwards cookies; `/auth/refresh` and
`/auth/logout` need no `AuthenticationFilter` (they authenticate via the cookie, not the
access token). They are NOT rate-limited (only login/signup/resend are).

## Components / files

**auth-service:**
- `config`: `JWT_EXPIRATION` default → `900000`; add `refresh.expiration` + `cookie.secure`,
  `cookie.same-site` properties.
- `RefreshTokenService` (new): `String issue(Long userId)`, `Long validate(String token)`,
  `String rotate(String oldToken)` (validate+delete+issue), `void revoke(String token)` —
  backed by `StringRedisTemplate`, keys `refresh:<token>`, TTL from config.
- `CookieFactory` (new/helper): build the `ResponseCookie` for set + clear.
- `AuthController`: `/login` sets the cookie; add `/refresh` and `/logout`.

**frontend:**
- `api.js`: `withCredentials: true`; a response interceptor — on 401, call `POST /auth/refresh`
  once, swap in the new access token, retry the original request; if refresh 401s, clear session
  and redirect to login.
- Login stores the access token as today (cookie is set automatically). Logout calls
  `POST /auth/logout` before clearing local state.

**gateway:** no code change (verify `/auth/refresh` + `/auth/logout` reachable and cookies pass).

## Testing

- **auth-service (unit)**: `RefreshTokenService` issue→validate→rotate (old token invalid after
  rotate)→revoke. Mock/embedded Redis or a `StringRedisTemplate` test double.
- **Live smoke**: login returns access + sets refresh cookie; `/auth/refresh` with the cookie
  returns a new access token + new cookie and the old refresh token no longer works; `/auth/logout`
  kills it; an expired access token gets transparently refreshed by the frontend interceptor.

## Out of scope

- Full refresh-token *family* revocation / reuse-detection cascades (MVP does simple rotation).
- Remember-me / device management UI.

## Ships as

Branch `feat/refresh-tokens` off master. No schema change (Redis-backed), so no Flyway migration
needed. Git commits are the user's alone — no attribution lines.
