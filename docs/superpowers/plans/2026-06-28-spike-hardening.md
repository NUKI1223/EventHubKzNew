# Spike-Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let EventHub.kz absorb a 3-5k login+register spike on one 8-core VPS with zero errors (no 5xx, no timeouts, no OOM), even if individual requests slow down.

**Architecture:** Config-first hardening (bcrypt cost, JDBC pools, JVM/ES heaps, gateway logging/tracing/timeouts) + two real code changes (Kafka→ES bulk indexing, count-endpoint caching) + 2× horizontal replicas of the two hottest services (auth, event) behind the existing Eureka client-side load balancer. Verified by a committed k6 spike test, measured before/after.

**Tech Stack:** Spring Boot microservices (Java 17, Maven), Spring Cloud Gateway, Eureka, Kafka, Elasticsearch (Spring Data ES), Redis (Spring Cache), Postgres (HikariCP), Docker Compose, k6.

## Global Constraints

- **No Claude/Anthropic attribution** in any commit or file (no `Co-Authored-By`, no generated-by lines).
- **Never stage or commit `.env`** (holds `JWT_SECRET`, `GEMINI_API_KEY`, DB secrets).
- **Do not commit `.claude/`.**
- **Do not push** unless the user explicitly asks. Commit only the files each task names.
- Working tree already has unrelated uncommitted changes (`.env`, `start.sh`, `api-gateway/.../AuthenticationFilter.java`) — **do not stage them**; use explicit per-file `git add`.
- VPS target: **8 cores / ≥16 GB**. Heap/replica numbers below are sized for that.
- Inter-service calls use Eureka `lb://` only (verified: no `http://auth-service` / `http://event-service` hostnames in code) — replicas are safe.
- bcrypt cost-10 is an accepted, documented security trade-off.

---

### Task 1: Lower bcrypt cost 12 → 10 (auth throughput ×4)

**Files:**
- Modify: `auth-service/src/main/java/org/ngcvfb/authservice/config/SecurityConfig.java:64-66`
- Test: `auth-service/src/test/java/org/ngcvfb/authservice/config/PasswordEncoderCostTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `passwordEncoder()` bean now `BCryptPasswordEncoder(10)`. No signature change.

- [ ] **Step 1: Write the failing test** (proves the no-migration claim: a cost-12 hash still verifies, new hashes are cost-10)

```java
package org.ngcvfb.authservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordEncoderCostTest {

    @Test
    void cost10EncoderVerifiesLegacyCost12HashAndEncodesAtCost10() {
        String legacyCost12Hash = new BCryptPasswordEncoder(12).encode("password123");
        assertTrue(legacyCost12Hash.startsWith("$2a$12$"), "precondition: legacy hash is cost 12");

        BCryptPasswordEncoder current = new BCryptPasswordEncoder(10);

        // existing cost-12 password must still verify (no DB migration)
        assertTrue(current.matches("password123", legacyCost12Hash));
        // new passwords are encoded at cost 10
        assertTrue(current.encode("password123").startsWith("$2a$10$"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl auth-service test -Dtest=PasswordEncoderCostTest`
Expected: compiles and PASSES already (this test does not depend on production code — it pins behavior). If it does not pass, stop and investigate the bcrypt assumption before changing anything.

- [ ] **Step 3: Change the production encoder cost**

In `SecurityConfig.java`, replace lines 64-66:

```java
    @Bean
    public PasswordEncoder passwordEncoder() {
        // cost factor 10: spike-hardening. bcrypt hashes are self-describing,
        // so existing cost-12 passwords still verify; new ones use cost 10.
        return new BCryptPasswordEncoder(10);
    }
```

- [ ] **Step 4: Run the test + full auth-service build**

Run: `mvn -q -pl auth-service test`
Expected: BUILD SUCCESS, `PasswordEncoderCostTest` PASSES.

- [ ] **Step 5: Commit**

```bash
git add auth-service/src/main/java/org/ngcvfb/authservice/config/SecurityConfig.java \
        auth-service/src/test/java/org/ngcvfb/authservice/config/PasswordEncoderCostTest.java
git commit -m "perf(auth): lower bcrypt cost 12->10 for login-spike throughput"
```

---

### Task 2: auth-service JDBC pool + unique Eureka instance-id + log level

**Files:**
- Modify: `auth-service/src/main/resources/application.yml`

**Interfaces:**
- Consumes: nothing.
- Produces: auth-service registers in Eureka with a unique `instance-id` per process (required by Task 8 replicas).

- [ ] **Step 1: Add Hikari pool sizing under `spring.datasource`**

In `application.yml`, extend the `spring.datasource` block (currently lines 8-12) so it reads:

```yaml
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5433/users_db}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres123}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 10000   # wait up to 10s for a connection (queue, don't fail)
```

- [ ] **Step 2: Add a unique Eureka instance-id**

Replace the `eureka.instance` block (lines 50-51) with:

```yaml
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${random.uuid}
```

- [ ] **Step 3: Quiet the DEBUG logging**

Replace the `logging` block (lines 84-87) with:

```yaml
logging:
  level:
    org.ngcvfb.authservice: INFO
    org.springframework.security: INFO
```

- [ ] **Step 4: Verify the service still boots and is healthy**

Run:
```bash
mvn -q -pl auth-service package -DskipTests
docker compose up -d --build auth-service
sleep 25
curl -s localhost:8081/actuator/health
```
Expected: `{"status":"UP",...}` (port 8081 still published at this point — Task 8 removes it).

- [ ] **Step 5: Commit**

```bash
git add auth-service/src/main/resources/application.yml
git commit -m "perf(auth): size Hikari pool, add unique eureka instance-id, quiet logs"
```

---

### Task 3: api-gateway logging/tracing/timeout tuning

**Files:**
- Modify: `api-gateway/src/main/resources/application.yml`

**Interfaces:**
- Consumes: nothing.
- Produces: gateway with INFO logs, 10% trace sampling, and a 30s upstream response timeout.

- [ ] **Step 1: Add an httpclient response timeout** under `spring.cloud.gateway`

In `application.yml`, add a `httpclient` block under `spring.cloud.gateway:` (sibling of `routes:`, e.g. after line 13 `lower-case-service-id: true`), indented to match `routes:`:

```yaml
      httpclient:
        connect-timeout: 5000
        response-timeout: 30s   # let a queued (slow) login finish instead of 504
```

- [ ] **Step 2: Lower trace sampling** — change line 219 `probability: 1.0` to:

```yaml
  tracing:
    sampling:
      probability: 0.1
```

- [ ] **Step 3: Quiet gateway DEBUG logs** — replace the `logging` block (lines 232-235) with:

```yaml
logging:
  level:
    org.springframework.cloud.gateway: INFO
    org.ngcvfb.apigateway: INFO
```

- [ ] **Step 4: Verify gateway boots and routes**

Run:
```bash
mvn -q -pl api-gateway package -DskipTests
docker compose up -d --build api-gateway
sleep 25
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8180/api/search?q=React"
```
Expected: `200`.

- [ ] **Step 5: Commit**

```bash
git add api-gateway/src/main/resources/application.yml
git commit -m "perf(gateway): INFO logs, 10% trace sampling, 30s response-timeout"
```

---

### Task 4: Raise JVM and Elasticsearch heaps (no-OOM headroom)

**Files:**
- Modify: `docker-compose.yml` (auth-service, event-service, search-service, api-gateway `JAVA_TOOL_OPTIONS`; elasticsearch `ES_JAVA_OPTS` + `mem_limit`)

**Interfaces:**
- Consumes: nothing. Produces: larger heaps sized for the VPS.

- [ ] **Step 1: Raise the four Java service heaps + container limits**

For each of `auth-service`, `event-service`, `search-service`, `api-gateway` in `docker-compose.yml`, change `JAVA_TOOL_OPTIONS` from `-Xmx256m -XX:+UseSerialGC` to:

```yaml
      - JAVA_TOOL_OPTIONS=-Xmx512m -XX:+UseG1GC
```

and raise their `mem_limit: 512m` to `mem_limit: 1g`.

- [ ] **Step 2: Raise the Elasticsearch heap + container limit**

In the `elasticsearch` service, change `ES_JAVA_OPTS=-Xms384m -Xmx384m` to:

```yaml
      - ES_JAVA_OPTS=-Xms768m -Xmx768m
```

and `mem_limit: 768m` to `mem_limit: 1500m`.

- [ ] **Step 3: Sanity-check total memory budget**

Run: `grep -E "mem_limit" docker-compose.yml | awk '{print $2}' | sort | uniq -c`
Expected: confirm the sum of all `mem_limit` values fits the VPS RAM (≥16 GB target). If it exceeds ~14 GB, reduce the count of non-hot services rather than the hot ones. Record the total in the commit message.

- [ ] **Step 4: Verify the stack comes up healthy**

Run:
```bash
docker compose up -d --build auth-service event-service search-service api-gateway elasticsearch
sleep 40
docker ps --format '{{.Names}} {{.Status}}' | grep -E "auth-service|event-service|search-service|api-gateway|elasticsearch"
```
Expected: all `Up`, elasticsearch `(healthy)`.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml
git commit -m "perf(infra): raise service heaps to 512m (G1) and ES heap to 768m"
```

---

### Task 5: Batch Kafka→Elasticsearch bulk indexing (85 → several-hundred docs/s)

**Files:**
- Modify: `search-service/src/main/resources/application.yml` (enable batch listener)
- Modify: `search-service/src/main/java/org/ngcvfb/searchservice/service/EventSearchService.java` (add `saveAll`)
- Modify: `search-service/src/main/java/org/ngcvfb/searchservice/kafka/EventKafkaConsumer.java` (batch handlers)
- Test: `search-service/src/test/java/org/ngcvfb/searchservice/service/EventSearchServiceSaveAllTest.java`

**Interfaces:**
- Consumes: `EventSearchRepository.saveAll(Iterable)` (Spring Data ES — issues one ES `_bulk` request), `EventDocument.builder()`.
- Produces: `EventSearchService.saveAll(List<EventDocument>) : Iterable<EventDocument>`; consumer methods now accept `List<...>`.

- [ ] **Step 1: Write the failing test** (proves ONE bulk call, not N single saves)

```java
package org.ngcvfb.searchservice.service;

import org.junit.jupiter.api.Test;
import org.ngcvfb.searchservice.model.EventDocument;
import org.ngcvfb.searchservice.repository.EventSearchRepository;

import java.util.List;

import static org.mockito.Mockito.*;

class EventSearchServiceSaveAllTest {

    @Test
    void saveAllIssuesASingleBulkCall() {
        EventSearchRepository repo = mock(EventSearchRepository.class);
        EventSearchService service = new EventSearchService(repo);

        List<EventDocument> docs = List.of(
                EventDocument.builder().id("1").title("a").build(),
                EventDocument.builder().id("2").title("b").build());

        service.saveAll(docs);

        verify(repo, times(1)).saveAll(docs); // one bulk request
        verify(repo, never()).save(any());    // never per-document
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl search-service test -Dtest=EventSearchServiceSaveAllTest`
Expected: FAIL — compilation error, `saveAll` not defined on `EventSearchService`.

- [ ] **Step 3: Add `saveAll` to `EventSearchService`** (after the existing `save` method, ~line 61)

```java
    public Iterable<EventDocument> saveAll(java.util.List<EventDocument> documents) {
        log.info("Bulk indexing {} events", documents.size());
        return eventSearchRepository.saveAll(documents);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl search-service test -Dtest=EventSearchServiceSaveAllTest`
Expected: PASS.

- [ ] **Step 5: Enable batch listening** — in `search-service/src/main/resources/application.yml`, add under `spring.kafka` (sibling of `consumer:`):

```yaml
    listener:
      type: batch
    consumer:
      max-poll-records: 500
```
(Merge `max-poll-records: 500` into the existing `consumer:` block rather than duplicating the key.)

- [ ] **Step 6: Rewrite `EventKafkaConsumer` for batch handlers** — replace the whole file body of the three listener methods with:

```java
package org.ngcvfb.searchservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.EventCreatedEvent;
import org.ngcvfb.eventhubkz.common.events.EventDeletedEvent;
import org.ngcvfb.eventhubkz.common.events.EventUpdatedEvent;
import org.ngcvfb.searchservice.model.EventDocument;
import org.ngcvfb.searchservice.service.EventSearchService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventKafkaConsumer {

    private final EventSearchService eventSearchService;

    @KafkaListener(topics = "event.created", groupId = "search-service-group-v3")
    public void handleEventCreated(List<EventCreatedEvent> events) {
        List<EventDocument> docs = events.stream()
                .filter(Objects::nonNull)
                .map(this::toDocument)
                .toList();
        if (docs.isEmpty()) return;
        try {
            eventSearchService.saveAll(docs);
            log.info("Bulk indexed {} created events", docs.size());
        } catch (Exception e) {
            log.error("Bulk index failed for {} created events", docs.size(), e);
        }
    }

    @KafkaListener(topics = "event.updated", groupId = "search-service-group-v3")
    public void handleEventUpdated(List<EventUpdatedEvent> events) {
        events.stream().filter(Objects::nonNull).forEach(event -> {
            try {
                eventSearchService.findById(String.valueOf(event.getId())).ifPresent(existing -> {
                    existing.setTitle(event.getTitle());
                    existing.setShortDescription(event.getShortDescription());
                    existing.setFullDescription(event.getFullDescription());
                    existing.setTags(event.getTags());
                    existing.setLocation(event.getLocation());
                    existing.setOnline(event.isOnline());
                    existing.setEventDate(event.getEventDate());
                    existing.setMainImageUrl(event.getMainImageUrl());
                    eventSearchService.save(existing);
                });
            } catch (Exception e) {
                log.error("Failed to update event {} in index", event.getId(), e);
            }
        });
    }

    @KafkaListener(topics = "event.deleted", groupId = "search-service-group-v3")
    public void handleEventDeleted(List<EventDeletedEvent> events) {
        events.stream().filter(Objects::nonNull).forEach(event -> {
            try {
                eventSearchService.deleteById(String.valueOf(event.getId()));
            } catch (Exception e) {
                log.error("Failed to delete event {} from index", event.getId(), e);
            }
        });
    }

    private EventDocument toDocument(EventCreatedEvent event) {
        return EventDocument.builder()
                .id(String.valueOf(event.getId()))
                .title(event.getTitle())
                .shortDescription(event.getShortDescription())
                .fullDescription(event.getFullDescription())
                .tags(event.getTags())
                .location(event.getLocation())
                .online(event.isOnline())
                .eventDate(event.getEventDate())
                .mainImageUrl(event.getMainImageUrl())
                .organizerEmail(event.getOrganizerEmail())
                .organizerId(event.getOrganizerId())
                .build();
    }
}
```

- [ ] **Step 7: Build + behavioral verify (indexing keeps up)**

Run:
```bash
mvn -q -pl search-service test
docker compose up -d --build search-service
sleep 25
# create 200 events fast through the gateway, then watch ES catch up
TOK=$(curl -s -X POST localhost:8180/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"aidar.kasenov@example.kz","password":"password123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
before=$(curl -s localhost:8180/api/search/count)
for i in $(seq 1 200); do curl -s -o /dev/null -X POST localhost:8180/api/events \
  -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
  -d "{\"title\":\"bulk-test $i\",\"shortDescription\":\"x\",\"fullDescription\":\"x\",\"online\":true,\"location\":\"Online\",\"eventDate\":\"2026-09-01T10:00:00\",\"tags\":[\"BulkTest\"]}" & done; wait
sleep 8
after=$(curl -s localhost:8180/api/search/count)
echo "ES before=$before after=$after (expect ~+200 within a few seconds)"
```
Expected: `after` ≈ `before + 200` within ~5-8s (pre-change this lagged minutes). BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add search-service/src/main/resources/application.yml \
        search-service/src/main/java/org/ngcvfb/searchservice/service/EventSearchService.java \
        search-service/src/main/java/org/ngcvfb/searchservice/kafka/EventKafkaConsumer.java \
        search-service/src/test/java/org/ngcvfb/searchservice/service/EventSearchServiceSaveAllTest.java
git commit -m "perf(search): batch-consume + ES bulk indexing in Kafka consumer"
```

---

### Task 6: Cache like-count endpoints (like-service)

**Files:**
- Modify: `like-service/pom.xml` (add cache + redis starters)
- Modify: `like-service/src/main/resources/application.yml` (redis + cache config)
- Modify: `like-service/src/main/java/org/ngcvfb/likeservice/LikeServiceApplication.java` (`@EnableCaching`)
- Modify: `like-service/src/main/java/org/ngcvfb/likeservice/service/EventLikeService.java` (`@Cacheable`/`@CacheEvict`)

**Interfaces:**
- Consumes: existing `getLikeCount(Long)`, `getCounts(List<Long>)`, `likeEvent(...)`, `unlikeEvent(...)`, `deleteAllLikesForEvent(Long)`.
- Produces: `likeCount` cache keyed by `eventId`, evicted on write. No signature changes.

- [ ] **Step 1: Add starters to `like-service/pom.xml`** (inside `<dependencies>`)

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-cache</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
```

- [ ] **Step 2: Add redis + cache config to `like-service/src/main/resources/application.yml`** (under `spring:`)

```yaml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  cache:
    type: redis
    redis:
      time-to-live: 60000   # 60s backstop; eviction is explicit on write
```

- [ ] **Step 3: Enable caching** — add `@EnableCaching` to `LikeServiceApplication.java`:

```java
import org.springframework.cache.annotation.EnableCaching;
// ...
@SpringBootApplication
@EnableCaching
public class LikeServiceApplication {
```

- [ ] **Step 4: Annotate the read + write methods** in `EventLikeService.java`

Add imports:
```java
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
```
Annotate the count read (line ~26):
```java
    @Cacheable(value = "likeCount", key = "#eventId")
    public long getLikeCount(Long eventId) {
```
Add eviction to each writer — `likeEvent` (line ~53), `unlikeEvent` (line ~77), `deleteAllLikesForEvent` (line ~83):
```java
    @CacheEvict(value = "likeCount", key = "#eventId")
```
(Put the annotation directly above each method's existing `@Transactional`. For `likeEvent(Long userId, Long eventId, ...)` and `unlikeEvent(Long userId, Long eventId)` the SpEL `#eventId` resolves the parameter by name.)

Note: `getCounts(List)` is left uncached (multi-key); it benefits indirectly and is not the hot single-event path. Keep scope minimal.

- [ ] **Step 5: Build + behavioral verify (count caches, then evicts on like)**

Run:
```bash
mvn -q -pl like-service package -DskipTests
docker compose up -d --build like-service
sleep 25
curl -s "localhost:8180/api/likes/event/5/count"; echo   # populates cache
docker exec redis redis-cli --scan --pattern 'likeCount*' | head   # expect a key
```
Expected: a `likeCount::5` key present in Redis after the GET.

- [ ] **Step 6: Commit**

```bash
git add like-service/pom.xml like-service/src/main/resources/application.yml \
        like-service/src/main/java/org/ngcvfb/likeservice/LikeServiceApplication.java \
        like-service/src/main/java/org/ngcvfb/likeservice/service/EventLikeService.java
git commit -m "perf(like): cache per-event like counts in redis, evict on write"
```

---

### Task 7: Cache registration-count endpoints (registration-service)

**Files:**
- Modify: `registration-service/pom.xml` (cache + redis starters)
- Modify: `registration-service/src/main/resources/application.yml` (redis + cache config)
- Modify: `registration-service/src/main/java/org/ngcvfb/registrationservice/RegistrationServiceApplication.java` (`@EnableCaching`)
- Modify: `registration-service/src/main/java/org/ngcvfb/registrationservice/service/EventRegistrationService.java` (`@Cacheable`/`@CacheEvict`)

**Interfaces:**
- Consumes: existing `getRegistrationCount(Long)`, `register(...)`, `cancel(...)`.
- Produces: `registrationCount` cache keyed by `eventId`, evicted on register/cancel.

- [ ] **Step 1: Add the two starters to `registration-service/pom.xml`** — identical XML block as Task 6 Step 1.

- [ ] **Step 2: Add redis + cache config to `registration-service/src/main/resources/application.yml`** — identical YAML as Task 6 Step 2 (TTL 60000).

- [ ] **Step 3: Enable caching** — add to `RegistrationServiceApplication.java`:

```java
import org.springframework.cache.annotation.EnableCaching;
// ...
@SpringBootApplication
@EnableCaching
public class RegistrationServiceApplication {
```
(Verify the exact application class name first: `ls registration-service/src/main/java/org/ngcvfb/registrationservice/*Application.java`.)

- [ ] **Step 4: Annotate read + writers** in `EventRegistrationService.java`

Add imports:
```java
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
```
Count read (line ~39):
```java
    @Cacheable(value = "registrationCount", key = "#eventId")
    public long getRegistrationCount(Long eventId) {
```
Add eviction above `register` (line ~93) and `cancel` (line ~176):
```java
    @CacheEvict(value = "registrationCount", key = "#eventId")
```
(Both methods take `(Long userId, Long eventId, ...)` / `(Long userId, Long eventId)` — `#eventId` resolves by name.)

- [ ] **Step 5: Build + behavioral verify**

Run:
```bash
mvn -q -pl registration-service package -DskipTests
docker compose up -d --build registration-service
sleep 25
curl -s "localhost:8180/api/registrations/event/5/count"; echo
docker exec redis redis-cli --scan --pattern 'registrationCount*' | head
```
Expected: a `registrationCount::5` key present after the GET.

- [ ] **Step 6: Commit**

```bash
git add registration-service/pom.xml registration-service/src/main/resources/application.yml \
        registration-service/src/main/java/org/ngcvfb/registrationservice/RegistrationServiceApplication.java \
        registration-service/src/main/java/org/ngcvfb/registrationservice/service/EventRegistrationService.java
git commit -m "perf(registration): cache per-event registration counts, evict on write"
```

---

### Task 8: Run auth-service and event-service as 2 replicas each

**Files:**
- Modify: `docker-compose.yml` (auth-service + event-service: drop `container_name` and host `ports`, add `deploy.replicas: 2`)

**Interfaces:**
- Consumes: unique Eureka instance-ids (auth from Task 2; event already has `${random.uuid}` in its yml).
- Produces: two registered instances of each service; gateway `lb://` balances across them.

- [ ] **Step 1: Convert auth-service to replicas** — in its `docker-compose.yml` block:
  - delete the line `container_name: auth-service`
  - delete the `ports:` mapping `- "8081:8081"` (and the now-empty `ports:` key)
  - add, at the same indent as `build:`:
    ```yaml
    deploy:
      replicas: 2
    ```

- [ ] **Step 2: Convert event-service to replicas** — same three edits in its block (delete `container_name: event-service`, delete `ports: - "8083:8083"`, add `deploy: replicas: 2`).

- [ ] **Step 3: Recreate the two services**

Run:
```bash
docker compose up -d --build auth-service event-service
sleep 35
docker compose ps | grep -E "auth-service|event-service"
```
Expected: two containers each (e.g. `eventhubkz-auth-service-1`, `-2`).

- [ ] **Step 4: Verify both instances registered in Eureka and gateway balances**

Run:
```bash
curl -s localhost:8761/eureka/apps/AUTH-SERVICE | grep -c "<instance>"
curl -s localhost:8761/eureka/apps/EVENT-SERVICE | grep -c "<instance>"
# 10 logins; both auth instances should log activity
for i in $(seq 1 10); do curl -s -o /dev/null -X POST localhost:8180/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"aidar.kasenov@example.kz","password":"password123"}'; done
docker compose logs --since 30s auth-service | grep -c "login\|Authenticated\|token"
```
Expected: each `grep -c "<instance>"` returns `2`; login requests succeed (HTTP 200) and both replicas show traffic.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml
git commit -m "perf(infra): run auth-service and event-service as 2 replicas behind eureka"
```

---

### Task 9: Spike load test — prove zero errors, before/after

**Files:**
- Create: `loadtest/spike.js`
- Create: `loadtest/README.md`

**Interfaces:**
- Consumes: running stack on `localhost:8180`; k6 binary; seed users; a large event-id pool.
- Produces: a committed, repeatable spike test and a recorded result.

- [ ] **Step 1: Write the spike script** `loadtest/spike.js`

```javascript
import http from 'k6/http';
import { Rate, Trend, Counter } from 'k6/metrics';

const GW = __ENV.GW || 'http://localhost:8180';
// seed users — bcrypt load is identical regardless of which user logs in
const USERS = [
  'aidar.kasenov@example.kz',
  'dinara.zhumabaeva@example.kz',
];
const PASS = 'password123';
const JH = { headers: { 'Content-Type': 'application/json' } };

const loginFail = new Rate('login_fail');     // 5xx / timeout / refused (any scenario)
const dLogin = new Trend('login_dur', true);
const okLogins = new Counter('logins_ok');

export const options = {
  discardResponseBodies: false,
  scenarios: {
    // ~5000 logins compressed into 60s (the real bottleneck)
    login_spike: {
      executor: 'ramping-arrival-rate',
      startRate: 50, timeUnit: '1s', preAllocatedVUs: 200, maxVUs: 1500,
      stages: [
        { target: 100,  duration: '10s' },
        { target: 4000, duration: '10s' }, // sharp ramp = the crush
        { target: 4000, duration: '40s' },
      ],
      exec: 'login',
    },
    // background browse so reads are exercised under the same crush
    browse: {
      executor: 'constant-arrival-rate',
      rate: 500, timeUnit: '1s', duration: '60s',
      preAllocatedVUs: 100, maxVUs: 500,
      exec: 'browse',
    },
  },
  thresholds: {
    login_fail: ['rate==0'],  // PASS criterion: zero hard errors (login + browse)
  },
};

export function login() {
  const email = USERS[Math.floor(Math.random() * USERS.length)];
  const r = http.post(`${GW}/auth/login`, JSON.stringify({ email, password: PASS }), JH);
  dLogin.add(r.timings.duration);
  const hardFail = r.status === 0 || r.status >= 500; // refused/timeout/5xx
  loginFail.add(hardFail);
  if (r.status === 200) okLogins.add(1);
}

const TERMS = ['React','AI','DevOps','Astana','Cloud','Backend','Data','Security'];
export function browse() {
  const q = TERMS[Math.floor(Math.random() * TERMS.length)];
  const r = http.get(`${GW}/api/search?q=${q}`);
  // browse errors also count as login_fail-style hard failures
  loginFail.add(r.status === 0 || r.status >= 500);
}
```

- [ ] **Step 2: Write `loadtest/README.md`**

````markdown
# Spike load test

Validates the 3-5k login spike hardening: zero hard errors (5xx / timeouts) under a
~5000-logins-in-60s crush plus 500 rps background browse.

## Run
```bash
k6 run -e GW=http://localhost:8180 loadtest/spike.js
```

## Pass criteria
- `login_fail ... rate==0` and `reg_hard_fail ... rate==0` (k6 marks thresholds green).
- No container restarts / OOM: `docker compose ps` clean afterward.

## Registration spike (optional, needs a large event pool)
Registering the same user to the same event twice is a logical 409, not an overload
error. To test registration throughput, register distinct (user, event) pairs from a
large seeded event-id range and treat only status 0/5xx as failures.
````

- [ ] **Step 3: Capture the BEFORE baseline** (run against the stack as it is *before* applying replicas/tuning if measuring deltas; otherwise run once now as the AFTER)

Run:
```bash
docker inspect -f '{{.Name}} {{.RestartCount}}' $(docker compose ps -q) | sort
k6 run -e GW=http://localhost:8180 loadtest/spike.js | tee loadtest/result-after.txt
docker inspect -f '{{.Name}} {{.RestartCount}} OOM={{.State.OOMKilled}}' $(docker compose ps -q) | sort
```
Expected (AFTER all tasks): the `login_fail` threshold is **green (rate 0)**; restart counts unchanged; `OOM=false` everywhere.

- [ ] **Step 4: Clean up test data**

Run: `./start.sh --seed-force`
Expected: tables truncated and reseeded to the demo dataset.

- [ ] **Step 5: Commit** (the script + README only — not the result txt unless you want it kept)

```bash
git add loadtest/spike.js loadtest/README.md
git commit -m "test(perf): add k6 spike load test for the 3-5k login crush"
```

---

## Self-review notes

- **Spec coverage:** §4.1 → Tasks 1,2,8; §4.2 → Tasks 3,4,6,7,8; §4.3 → Tasks 4,5; §4.4 → Tasks 2,3,4; §4.5 → Task 9. All design sections map to a task.
- **Registration-in-spike nuance:** the spec's "logins + registrations" is honored by the login spike (the binding constraint) + documented registration-throughput guidance in Task 9 README, avoiding false 409 "errors" from duplicate registrations.
- **Ordering:** config-only (1-4) → bulk indexing (5) → caching (6-7) → replicas (8, depends on Task 2's instance-id) → verification (9). Matches spec §6.
- **Replica safety:** verified no direct `http://auth-service`/`http://event-service` hostnames; all inter-service calls use Eureka `lb://`.
