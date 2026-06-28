# Performance Design — Survive a 3-5k login+register spike with zero errors

**Date:** 2026-06-28
**Status:** Approved design (pending implementation plan)
**Scope owner:** EventHub.kz (diploma project)

## 1. Goal & success criteria

Make the platform handle a **traffic spike of 3,000-5,000 people logging in and registering
for an event within ~1-2 minutes** (registration opens / hyped meetup drops) **with zero
errors**. Latency is explicitly *not* the bar — a login may take a few seconds during the
crush, as long as it **completes** and nobody receives a 5xx, a timeout, or a refused
connection.

**Pass criteria (verified by load test):**
- ~5,000 logins + registrations compressed into ~60s → **0% error rate** (no 5xx, no
  gateway timeouts, no connection refusals, no OOM-killed containers).
- Every request eventually completes.
- Elasticsearch indexing lag after the write spike **converges within seconds**, not minutes.
- Before/after numbers captured per change so gains are attributable.

**Out of scope (future work):** refresh tokens / longer-lived access tokens, async
registration via queue, cloud/k8s autoscaling, RS256 JWT migration.

## 2. Context — measured baseline (from the 2026-06-25 stress test)

The system never crashed under load; it degrades gracefully. The limiters that threaten the
"zero errors" goal under a spike:

| Path | Measured ceiling | Limiter |
|------|------------------|---------|
| Login (`POST /auth/login`) | ~100-135 logins/s | `BCryptPasswordEncoder(12)` — ~75ms CPU/op; auth-service hit 1015% CPU |
| Reads (search / event-by-id) | ~2,000-2,500 req/s | api-gateway CPU (384%), inflated by DEBUG logging + full tracing |
| Sync writes (`POST /api/events`) | 800+/s, p95 20ms | not a bottleneck |
| **Async ES indexing** | **~85 docs/s** | `EventKafkaConsumer` does one `save()` per message (no bulk); ES heap hit 96% (384m) and GC-stalled |

Current config facts:
- All services single-instance; **no `cpus:` limits**, `mem_limit: 512m`, `-Xmx256m` heaps;
  ES heap 384m / container 768m.
- Only event-service uses Redis `@Cacheable`. No Hikari / Tomcat / gateway-timeout tuning
  (all framework defaults: Hikari pool 10, Tomcat 200 threads).
- Gateway routes already use Eureka client-side LB (`uri: lb://AUTH-SERVICE`, `lb://EVENT-SERVICE`).

## 3. Deployment assumption

Target is **one real VPS, ≥8 cores / ≥16 GB RAM**. Replica counts and heap sizes below are
sized for that. They scale with the actual box — fewer cores → fewer replicas, same approach.

## 4. Design

### 4.1 Auth spike (primary limiter)

- **Lower bcrypt cost 12 → 10** in `auth-service/.../config/SecurityConfig.java`
  (`new BCryptPasswordEncoder(10)`). ~4× per-process login throughput (~135/s → ~500/s).
  - **No password migration needed:** bcrypt hashes are self-describing (`$2a$12$…`), so
    `BCryptPasswordEncoder.matches()` reads the cost from the stored hash and still verifies
    existing cost-12 passwords. Only newly set/changed passwords use cost-10.
  - Cost-10 is a standard, safe default.
- **Run 2 replicas of auth-service** behind Eureka. Gateway already balances via
  `lb://AUTH-SERVICE`. Each replica has its own JDBC pool and heap → spreads bcrypt across
  cores and isolates failure.
  - Implementation: docker-compose replicas with **dynamic `server.port`** and distinct
    Eureka instance-ids so both register (must remove the fixed host port mapping / let them
    share via Eureka, not a published port). Detail to resolve in the plan.
- **Hikari (users_db)**: `maximum-pool-size: 20`, `connection-timeout: 10000` — under spike a
  thread **waits** for a connection instead of erroring.

### 4.2 Read path

- **Gateway**: set log level to `INFO` (from DEBUG) and **sample tracing** (~10%,
  `management.tracing.sampling.probability: 0.1`). The DEBUG/trace overhead was a large part
  of the 384% gateway CPU.
- **Run 2 replicas of event-service** behind `lb://EVENT-SERVICE` (same replica mechanism as auth).
- **Extend Redis caching** to the cheap count endpoints — like counts and registration counts
  (`/api/likes/counts`, `/api/registrations/counts` and the per-event count variants).
  Add `@Cacheable` in like-service and registration-service with a short TTL and eviction on
  write. (Both services already have Redis configured.)
- **Raise event-service heap** (`-Xmx256m` → `-Xmx512m`, `mem_limit` to match).

### 4.3 Write + indexing path

- **Rewrite `search-service/.../kafka/EventKafkaConsumer`** to **batch-consume + ES `_bulk`**:
  - Enable Kafka batch listening (`spring.kafka.listener.type: batch`, tuned
    `max-poll-records`).
  - Index a batch per poll via the Elasticsearch bulk API instead of one
    `eventSearchService.save()` per message.
  - Target: lift ~85 docs/s to several hundred docs/s so a registration/event write spike
    indexes within seconds.
- **Raise ES heap** `-Xmx384m → -Xmx768m` and `mem_limit` 768m → ~1.5g (it GC-stalled at 96%).
- Registration writes already emit to Kafka (buffers fine); ensure registration-service
  Hikari pool is tuned like auth (4.1).

### 4.4 Zero-error guarantee (backpressure & headroom)

- **Generous gateway response timeout** (e.g. 20-30s, `spring.cloud.gateway.httpclient.response-timeout`)
  so a queued 2-3s login completes instead of returning 504.
- **Raised JVM heaps** across hot services (auth, event, search, gateway) for request-backlog
  headroom → no OOM under the crush. Sized to fit the VPS RAM budget.
- **Frontend**: confirm `frontend/src/api.js` adds no retry storm (current behavior: single
  redirect to `/signin` on 401 — acceptable, no change planned).

### 4.5 Verification

- Reuse the k6 harness from the 2026-06-25 stress test. Add a **spike scenario**:
  ~5,000 logins + registrations within ~60s (and a mixed browse background).
- Run **before and after** the changes; record error rate, completion, and ES indexing-lag
  convergence.
- **Done = pass criteria in §1 met.** Clean up test data afterward (`./start.sh --seed-force`).

## 5. Risks & notes

- **Replica registration on one host** is the fiddliest part (Eureka instance-ids, no fixed
  published ports) — resolve concretely in the implementation plan.
- **Cache invalidation** for counts must evict on like/register/unlike/unregister, or counts
  go stale; short TTL is the backstop.
- **bcrypt-10** is a deliberate, documented security trade-off (accepted by project owner).
- VPS RAM must hold the extra replicas + larger heaps; sizing is a plan-time calculation
  against the real box.

## 6. Implementation order (high level)

1. Config-only hardening (bcrypt-10, pools, gateway log/trace/timeout, heaps) — lowest risk.
2. ES bulk-indexing consumer rewrite.
3. Count-endpoint caching (like-service, registration-service).
4. auth-service + event-service replicas + Eureka/compose wiring.
5. Spike load test + before/after measurement.
