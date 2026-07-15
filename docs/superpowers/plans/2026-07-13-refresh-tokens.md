# Refresh Tokens Implementation Plan

> Execute inline (auth is login-critical — verify each task live). Steps use `- [ ]`.

**Goal:** Short-lived (15 min) access JWT + revocable refresh token in Redis, delivered as an httpOnly cookie; frontend transparently refreshes on 401.

**Architecture:** auth-service issues an opaque refresh token stored in Redis (`refresh:<token>→userId`, 7-day TTL) and set as an httpOnly cookie on login. `POST /auth/refresh` rotates it and returns a new access token; `POST /auth/logout` revokes it. Frontend axios adds `withCredentials` + a 401→refresh→retry interceptor.

## Global Constraints
- auth-service already has `spring-boot-starter-data-redis`; use `StringRedisTemplate`.
- Gateway routes `/auth/**` → auth-service `/api/auth/**` (public, cookies forwarded) — no gateway change.
- Cookie: `HttpOnly; SameSite=Lax; Path=/auth`, `Secure` only when `COOKIE_SECURE=true`.
- No `Co-Authored-By` in commits.
- Rebuild recipe: `mvn -q clean package -pl auth-service -DskipTests` → `docker compose build auth-service` → `docker compose up -d --force-recreate --no-deps auth-service` → wait ~45s for Eureka.

---

### Task 1: RefreshTokenService (Redis) + test
**Files:** Create `auth-service/.../service/RefreshTokenService.java`, `auth-service/.../service/RefreshTokenServiceTest.java`

- [ ] Write test: `issue` returns a token; `validate` returns the userId; after `revoke`, `validate` returns null. Use a mocked `StringRedisTemplate` + `ValueOperations`.
- [ ] Implement `RefreshTokenService`: `String issue(Long userId)` (32-byte SecureRandom base64url, `set refresh:<t>=userId EX ttl`), `Long validate(String)`, `void revoke(String)`. `@Value("${security.refresh.expiration:604800000}")`.
- [ ] `mvn -q test -pl auth-service -Dtest=RefreshTokenServiceTest` → green.
- [ ] Commit `feat(auth): refresh token store (Redis)`.

### Task 2: Config — shorten access token, add refresh + cookie settings
**Files:** Modify `auth-service/src/main/resources/application.yml`
- [ ] `JWT_EXPIRATION` default `3600000` → `900000` (15 min). Add under `security:` — `refresh.expiration: ${REFRESH_EXPIRATION:604800000}`, `cookie.secure: ${COOKIE_SECURE:false}`.
- [ ] Add `COOKIE_SECURE` (=true) to `.env.prod`; `.env.example` documents it.
- [ ] Commit `feat(auth): 15-min access token + refresh/cookie config`.

### Task 3: AuthController — set cookie on login, add /refresh + /logout
**Files:** Modify `AuthController.java`; add `getById` + reuse response-building in `AuthenticationService.java`; helper for the cookie.
- [ ] `AuthenticationService`: extract `AuthResponse buildResponse(AuthUser user)` (the existing builder) and add `AuthUser getById(Long id)`.
- [ ] Cookie helper: `ResponseCookie refreshCookie(String token, long maxAgeSec)` + `ResponseCookie clearRefreshCookie()` (httpOnly, sameSite Lax, path `/auth`, secure from config).
- [ ] `/login`: after `authenticate`, `String rt = refreshTokenService.issue(resp.getUserId())`; return `ResponseEntity.ok().header(SET_COOKIE, refreshCookie(rt, ttlSec).toString()).body(resp)`.
- [ ] `POST /refresh`: read `@CookieValue(value="refresh_token", required=false)`; `userId=validate(rt)`; if null → 401; `user=getById(userId)`; `newResp=buildResponse(user)`; `revoke(rt)`; `newRt=issue(userId)`; return body `newResp` + new cookie.
- [ ] `POST /logout`: `revoke(rt)`; return 200 + clear-cookie.
- [ ] Rebuild + live smoke (Task 5).
- [ ] Commit `feat(auth): login sets refresh cookie; /refresh rotate; /logout revoke`.

### Task 4: Frontend — withCredentials + 401 interceptor + logout
**Files:** Modify `frontend/src/api.js` (or api client), the auth/logout handler.
- [ ] axios instance: `withCredentials: true`.
- [ ] Response interceptor: on 401 (not for `/auth/refresh` or `/auth/login`, and not already retried), POST `/auth/refresh`; on success store new access token, set `Authorization`, retry original request; on failure clear session → redirect `/login`.
- [ ] Logout: `POST /auth/logout` then clear local token.
- [ ] `cd frontend && npx vite build` → green.
- [ ] Commit `feat(auth): frontend transparent refresh on 401 + logout revoke`.

### Task 5: Live smoke
- [ ] `curl -i login` → 200, body has `token`, response has `Set-Cookie: refresh_token=...; HttpOnly`.
- [ ] `curl` `/auth/refresh` with the cookie → 200, new `token`, new `Set-Cookie`; reusing the OLD refresh cookie → 401 (rotated out).
- [ ] `curl` `/auth/logout` with cookie → 200 + cookie cleared; subsequent `/auth/refresh` with that cookie → 401.
- [ ] Access token still validates at the gateway for normal API calls.
