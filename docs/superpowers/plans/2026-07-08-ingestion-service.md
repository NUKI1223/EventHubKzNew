# AI Ingestion-Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Python `ingestion-service` that reads public Telegram channels via their `t.me/s` web mirror, extracts real events with an LLM (filtering ads/noise), deduplicates, and feeds candidates into the platform's existing `EventRequest` moderation queue for admin approval.

**Architecture:** New FastAPI service (port 8092, NOT in Eureka) in the same compose/network with its own `ingestion_db`. It talks to the platform only via HTTP (admin "run now" through the gateway) and Kafka (publishes `event.candidate.found`). `event-service` consumes candidates, dedups against existing events, and creates `EventRequest(source=AI_INGEST, requesterId=null)`. A new admin "Sources" tab manages sources and triggers runs.

**Tech Stack:** Python 3.12, FastAPI, httpx, BeautifulSoup4, APScheduler, psycopg 3, google Gemini REST, prometheus-client, pytest; Java 21 / Spring (event-service, gateway); React (admin tab).

**Spec:** `docs/superpowers/specs/2026-07-08-ingestion-service-design.md`

## Global Constraints

- Java: run Maven **from repo root** with `-pl <module>`; never inside a module dir.
- Python: the service lives in `ingestion-service/`; run pytest from that dir (`cd ingestion-service && python -m pytest`).
- Commits: lowercase conventional (`feat(ingest): ...` for Python, `feat(event): ...` etc. for Java); **no Co-Authored-By / attribution.**
- Docker redeploy (Java): `mvn clean package -DskipTests -pl <svc>` → `docker compose build <svc>` → `docker rm -f <container>` → `docker compose up -d <svc>`. For Python: `docker compose build ingestion-service` → `docker rm -f ingestion-service` → `docker compose up -d ingestion-service`.
- `ingestion-service` port **8092**, not in Eureka; the gateway route targets it directly: `uri: http://ingestion-service:8092` (NOT `lb://`).
- Kafka topic `event.candidate.found`; DTO `EventCandidateFoundEvent` in `eventhubkz-common` (same Lombok `@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper=true)` + static `create(...)` + `initBase()` pattern as sibling events).
- **EventRequest bean-validation fires on JPA persist** (Hibernate runs jakarta validation): `title` 3–200, `shortDescription` 10–500, `fullDescription` 20–20000 non-blank, `location` non-blank max 200, `eventDate` `@Future` non-null, `online` boolean. The candidate consumer must only persist candidates that satisfy these; ingestion must drop/skip candidates it cannot make valid (never publish a candidate without a future date and a non-blank location).
- `requesterId` becomes nullable; on approve/reject of a request with `requesterId == null`, DO NOT publish `event-request.reviewed` (no organizer to notify).
- Ingestion is polite: a delay between source fetches, request timeouts, per-source try/except (one bad source never aborts a run), and per-post Gemini failure skips the post not the run.
- The cheap pre-filter runs BEFORE any Gemini call (cost control).

---

### Task 1: EventRequest source fields + nullable requester (event-service)

**Files:**
- Create: `event-service/src/main/java/org/ngcvfb/eventservice/model/RequestSource.java`
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/model/EventRequest.java` (add fields; make requesterId nullable)
- Test: `event-service/src/test/java/org/ngcvfb/eventservice/model/EventRequestSourceTest.java`

**Interfaces:**
- Produces: `RequestSource` enum (`MANUAL`, `AI_INGEST`); `EventRequest` gains `source` (default `MANUAL`), `sourceUrl`, `sourceChannel`, and `requesterId` is now nullable. Consumed by Tasks 2, 11.

- [ ] **Step 1: Write the failing test** — an AI-ingest request persists with a null requester and AI_INGEST source. Use the module's Testcontainers pattern (`PostgresTestcontainer` + `@DataJpaTest`, mirror `event-service/src/test/java/org/ngcvfb/eventservice/repository/EventRequestRepositoryIT.java`):

```java
package org.ngcvfb.eventservice.model;

import org.junit.jupiter.api.Test;
import org.ngcvfb.eventservice.PostgresTestcontainer;
import org.ngcvfb.eventservice.repository.EventRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class EventRequestSourceTest extends PostgresTestcontainer {

    @Autowired EventRequestRepository repo;

    @Test
    void persistsAiIngestRequestWithNullRequester() {
        EventRequest r = EventRequest.builder()
                .title("Almaty Go Meetup")
                .shortDescription("Go community meetup with two talks")
                .fullDescription("A meetup for the Go community in Almaty, two talks and networking.")
                .location("Almaty, SmartPoint")
                .online(false)
                .eventDate(LocalDateTime.now().plusDays(10))
                .tags(Set.of("backend"))
                .source(RequestSource.AI_INGEST)
                .sourceUrl("https://t.me/kzdev/1234")
                .sourceChannel("kzdev")
                .requesterId(null)
                .build();

        EventRequest saved = repo.save(r);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRequesterId()).isNull();
        assertThat(saved.getSource()).isEqualTo(RequestSource.AI_INGEST);
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.PENDING); // @PrePersist default
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -pl event-service -Dtest=EventRequestSourceTest`
Expected: COMPILATION ERROR (`RequestSource` / `source` do not exist)

- [ ] **Step 3: Create `RequestSource`**

```java
package org.ngcvfb.eventservice.model;

public enum RequestSource { MANUAL, AI_INGEST }
```

- [ ] **Step 4: Modify `EventRequest`.** Change the `requesterId` column to nullable and add the three fields. Replace the existing `requesterId` declaration:

```java
    @Column(name = "requester_id")
    private Long requesterId;
```

(remove `nullable = false`). And add, next to it:

```java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private RequestSource source = RequestSource.MANUAL;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "source_channel", length = 200)
    private String sourceChannel;
```

Note: `@Builder.Default` is required so the builder keeps the `MANUAL` default (existing human-submission code paths must still get `MANUAL`). Verify the `@PrePersist` that sets `status = PENDING` also defaults `source` to `MANUAL` if null, to protect direct constructor use:

```java
    @PrePersist
    void prePersist() {
        if (status == null) status = RequestStatus.PENDING;
        if (source == null) source = RequestSource.MANUAL;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
```

(merge into the existing `@PrePersist` method — read it first and preserve whatever it already sets, e.g. createdAt.)

- [ ] **Step 5: Run tests**

Run: `mvn -q test -pl event-service -Dtest=EventRequestSourceTest`
Expected: `Tests run: 1, Failures: 0` (Docker required for Testcontainers)

- [ ] **Step 6: Confirm existing event-service tests still pass** (the requesterId nullability + new field must not break `EventRequestServiceCreateTest`, `EventServiceStaffTest`, repository ITs):

Run: `mvn -q test -pl event-service`
Expected: all green.

- [ ] **Step 7: Commit**

```bash
git add event-service/src
git commit -m "feat(event): EventRequest source fields + nullable requester for AI ingestion"
```

---

### Task 2: EventCandidateFoundEvent DTO + guard reviewed-notification for organizer-less requests

**Files:**
- Create: `eventhubkz-common/src/main/java/org/ngcvfb/eventhubkz/common/events/EventCandidateFoundEvent.java`
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/service/EventRequestService.java` (guard the `event-request.reviewed` publish when `requesterId == null`)
- Test: `event-service/src/test/java/org/ngcvfb/eventservice/service/EventRequestNotificationGuardTest.java`

**Interfaces:**
- Produces: `EventCandidateFoundEvent.create(String title, String shortDescription, String fullDescription, LocalDateTime eventDate, String city, String location, boolean online, Set<String> tags, String externalLink, String sourceUrl, String sourceChannel)` — consumed by the Python producer (Task 10, by field name/JSON) and the Java consumer (Task 11). Getters follow Lombok.
- Modifies: `approveRequest`/`rejectRequest` skip the reviewed-notification when the request has no requester.

- [ ] **Step 1: Write `EventCandidateFoundEvent`** (mirror `EventRequestCreatedEvent`'s Lombok+factory shape):

```java
package org.ngcvfb.eventhubkz.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Публикуется ingestion-service (Python) при извлечении события из поста Telegram.
 * Потребитель — event-service: создаёт EventRequest(AI_INGEST, PENDING) после дедупа.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventCandidateFoundEvent extends BaseEvent {
    private String title;
    private String shortDescription;
    private String fullDescription;
    private LocalDateTime eventDate;
    private String city;
    private String location;
    private boolean online;
    private Set<String> tags;
    private String externalLink;
    private String sourceUrl;
    private String sourceChannel;

    public static EventCandidateFoundEvent create(String title, String shortDescription,
            String fullDescription, LocalDateTime eventDate, String city, String location,
            boolean online, Set<String> tags, String externalLink, String sourceUrl,
            String sourceChannel) {
        EventCandidateFoundEvent e = EventCandidateFoundEvent.builder()
                .title(title).shortDescription(shortDescription).fullDescription(fullDescription)
                .eventDate(eventDate).city(city).location(location).online(online).tags(tags)
                .externalLink(externalLink).sourceUrl(sourceUrl).sourceChannel(sourceChannel)
                .build();
        e.initBase();
        return e;
    }
}
```

- [ ] **Step 2: Build common**

Run: `mvn -q clean install -DskipTests -pl eventhubkz-common`
Expected: BUILD SUCCESS

- [ ] **Step 3: Write the failing guard test.** Read `EventRequestService` first to learn the exact method names and the mock deps (`eventRequestRepository`, `kafkaProducer`, `eventService`). The test asserts that reviewing an organizer-less request does NOT publish a reviewed-notification, while a normal one does:

```java
package org.ngcvfb.eventservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.model.RequestSource;
import org.ngcvfb.eventservice.model.RequestStatus;
import org.ngcvfb.eventservice.repository.EventRequestRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventRequestNotificationGuardTest {

    @Mock EventRequestRepository eventRequestRepository;
    @Mock EventKafkaProducer kafkaProducer;
    @Mock EventService eventService;
    @InjectMocks EventRequestService service;

    private EventRequest pending(Long requesterId, RequestSource source) {
        EventRequest r = EventRequest.builder()
                .id(1L).title("t").shortDescription("short desc here")
                .fullDescription("full description that is long enough")
                .location("Almaty").online(false).eventDate(LocalDateTime.now().plusDays(5))
                .status(RequestStatus.PENDING).source(source).requesterId(requesterId).build();
        when(eventRequestRepository.findById(1L)).thenReturn(Optional.of(r));
        when(eventRequestRepository.save(any(EventRequest.class))).thenAnswer(i -> i.getArgument(0));
        return r;
    }

    @Test
    void rejectingAiIngestRequestDoesNotNotify() {
        pending(null, RequestSource.AI_INGEST);
        service.rejectRequest(1L, 99L, "spam");
        verify(kafkaProducer, never()).sendEventRequestReviewed(any());
    }

    @Test
    void rejectingManualRequestNotifies() {
        pending(7L, RequestSource.MANUAL);
        service.rejectRequest(1L, 99L, "no");
        verify(kafkaProducer).sendEventRequestReviewed(any());
    }
}
```

(Adjust the `rejectRequest` signature and mock list to the real class — read it. If `approveRequest` also publishes, add a symmetric guard + test there.)

- [ ] **Step 4: Run to verify it fails**

Run: `mvn -q test -pl event-service -Dtest=EventRequestNotificationGuardTest`
Expected: FAIL — `rejectingAiIngestRequestDoesNotNotify` fails (notification currently always sent).

- [ ] **Step 5: Implement the guard.** In `EventRequestService`, wherever `kafkaProducer.sendEventRequestReviewed(...)` is called (in approve and reject paths), wrap it:

```java
        if (request.getRequesterId() != null) {
            kafkaProducer.sendEventRequestReviewed(EventRequestReviewedEvent.create(...)); // existing args
        } else {
            log.info("Skipping reviewed-notification for organizer-less (AI_INGEST) request {}", request.getId());
        }
```

- [ ] **Step 6: Run tests**

Run: `mvn -q test -pl event-service -Dtest=EventRequestNotificationGuardTest && mvn -q test -pl event-service`
Expected: guard test green; full module green.

- [ ] **Step 7: Commit**

```bash
git add eventhubkz-common/src event-service/src
git commit -m "feat(common): EventCandidateFoundEvent; guard reviewed-notify for organizer-less requests"
```

---

### Task 3: ingestion-service scaffold (FastAPI + Docker + DB + health)

**Files:**
- Create: `ingestion-service/pyproject.toml`, `ingestion-service/Dockerfile`, `ingestion-service/app/__init__.py`, `ingestion-service/app/main.py`, `ingestion-service/app/config.py`, `ingestion-service/tests/__init__.py`, `ingestion-service/tests/test_health.py`
- Modify: `docker-compose.yml` (add `ingestion-service`), `docker-compose.override.yml` (publish 8092 to 127.0.0.1), `db-init/ingestion/01-create-ingestion-db.sql`

**Interfaces:**
- Produces: a runnable FastAPI app on port 8092 with `GET /health` → `{"status":"UP"}`, config read from env, `ingestion_db` created. Later tasks add routers/pipeline.

- [ ] **Step 1: Write the failing health test**

```python
# ingestion-service/tests/test_health.py
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_health_returns_up():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "UP"}
```

- [ ] **Step 2: Create `pyproject.toml`** (deps + pytest config):

```toml
[project]
name = "ingestion-service"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = [
    "fastapi>=0.115",
    "uvicorn[standard]>=0.32",
    "httpx>=0.27",
    "beautifulsoup4>=4.12",
    "apscheduler>=3.10",
    "psycopg[binary]>=3.2",
    "prometheus-client>=0.21",
    "pydantic>=2.9",
]

[project.optional-dependencies]
test = ["pytest>=8.3", "respx>=0.21"]

[tool.pytest.ini_options]
pythonpath = ["."]
testpaths = ["tests"]
```

- [ ] **Step 3: Create `app/config.py`** (env-driven settings):

```python
import os

class Settings:
    db_url = os.getenv("INGESTION_DB_URL", "postgresql://postgres:postgres@localhost:5436/ingestion_db")
    kafka_bootstrap = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    gemini_api_key = os.getenv("GEMINI_API_KEY", "")
    gemini_model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash-lite")
    fetch_delay_seconds = float(os.getenv("INGESTION_FETCH_DELAY", "2.0"))
    http_timeout_seconds = float(os.getenv("INGESTION_HTTP_TIMEOUT", "15.0"))
    schedule_cron = os.getenv("INGESTION_SCHEDULE_CRON", "0 6 * * *")  # daily 06:00

settings = Settings()
```

- [ ] **Step 4: Create `app/main.py`**

```python
from fastapi import FastAPI

app = FastAPI(title="ingestion-service")

@app.get("/health")
def health():
    return {"status": "UP"}
```

- [ ] **Step 5: Run the health test**

Run: `cd ingestion-service && python -m pip install -e ".[test]" -q && python -m pytest tests/test_health.py -q`
Expected: 1 passed.

- [ ] **Step 6: Create `Dockerfile`**

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY pyproject.toml .
RUN pip install --no-cache-dir -e .
COPY app ./app
EXPOSE 8092
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8092"]
```

- [ ] **Step 7: Create `db-init/ingestion/01-create-ingestion-db.sql`**

```sql
-- Fresh-volume init for the postgres-notifications instance (reused for ingestion_db).
-- Existing volume: create manually — docker exec postgres-notifications psql -U postgres -c "CREATE DATABASE ingestion_db;"
CREATE DATABASE ingestion_db;
```

Mount it on `postgres-notifications` in `docker-compose.yml` (add to that service's `volumes:`): `- ./db-init/ingestion:/docker-entrypoint-initdb.d`. (It already mounts `./db-init/audit`; add this second entry.)

- [ ] **Step 8: Add the compose service.** In `docker-compose.yml` after `audit-service`:

```yaml
  ingestion-service:
    build:
      context: ./ingestion-service
      dockerfile: Dockerfile
    container_name: ingestion-service
    mem_limit: 512m
    environment:
      - INGESTION_DB_URL=postgresql://${DB_USER}:${DB_PASSWORD}@postgres-notifications:5432/ingestion_db
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - GEMINI_API_KEY=${GEMINI_API_KEY:-}
    depends_on:
      postgres-notifications:
        condition: service_started
      kafka:
        condition: service_healthy
    networks:
      - eventhub-network
    logging:
      driver: "json-file"
      options:
        max-size: "50m"
        max-file: "3"
```

And in `docker-compose.override.yml` add: `ingestion-service:\n    ports: ["127.0.0.1:8092:8092"]`.

- [ ] **Step 9: Create the DB, build, deploy, smoke**

```bash
docker exec postgres-notifications psql -U postgres -c "CREATE DATABASE ingestion_db;" || true
docker compose build ingestion-service
docker compose up -d ingestion-service
sleep 8
curl -sf http://localhost:8092/health && echo " OK"
```
Expected: `{"status":"UP"} OK`.

- [ ] **Step 10: Commit**

```bash
git add ingestion-service docker-compose.yml docker-compose.override.yml db-init/ingestion
git commit -m "feat(ingest): scaffold FastAPI ingestion-service (port 8092) + ingestion_db"
```

---

### Task 4: DB layer — sources, ingested_posts, ingestion_runs

**Files:**
- Create: `ingestion-service/app/db.py`, `ingestion-service/app/models.py`, `ingestion-service/app/repository.py`
- Test: `ingestion-service/tests/test_repository.py`

**Interfaces:**
- Produces: `Repository` with `add_source(name, tme_url) -> Source`, `list_sources(enabled_only=False) -> list[Source]`, `set_source_enabled(id, enabled)`, `is_post_seen(source_id, post_ref) -> bool`, `mark_post_seen(source_id, post_ref)`, `update_last_seen(source_id, post_ref)`, `start_run(trigger) -> int`, `finish_run(run_id, **counts)`, `latest_run() -> Run | None`. Dataclasses `Source(id,name,tme_url,enabled,last_seen_ref)`, `Run(...)`. Consumed by Tasks 8, 9.
- Uses a psycopg connection; the test runs against a real Postgres via a Testcontainers-equivalent — use the `testing.postgresql` approach OR point at the live `ingestion_db` with a per-test schema. Simplest: use `pytest` fixture spinning a throwaway `postgres:16` container via `subprocess`/`docker` is heavy; instead the test connects to a **SQLite-incompatible** schema, so use a dedicated test DB: fixture creates tables in `ingestion_db` under a transaction rolled back per test.

- [ ] **Step 1: Write the failing test** (fixture opens a psycopg connection to a test Postgres, creates the schema, rolls back per test):

```python
# ingestion-service/tests/test_repository.py
import os, psycopg, pytest
from app.db import create_schema
from app.repository import Repository

TEST_DSN = os.getenv("TEST_DB_URL", "postgresql://postgres:postgres@localhost:5436/ingestion_db")

@pytest.fixture
def repo():
    conn = psycopg.connect(TEST_DSN, autocommit=False)
    create_schema(conn)
    conn.execute("TRUNCATE sources, ingested_posts, ingestion_runs RESTART IDENTITY CASCADE")
    yield Repository(conn)
    conn.rollback()
    conn.close()

def test_add_and_list_sources(repo):
    s = repo.add_source("KZ Dev", "https://t.me/s/kzdev")
    assert s.id is not None and s.enabled is True
    assert [x.name for x in repo.list_sources()] == ["KZ Dev"]

def test_post_seen_dedup(repo):
    s = repo.add_source("KZ Dev", "https://t.me/s/kzdev")
    assert repo.is_post_seen(s.id, "kzdev/10") is False
    repo.mark_post_seen(s.id, "kzdev/10")
    assert repo.is_post_seen(s.id, "kzdev/10") is True

def test_run_lifecycle(repo):
    rid = repo.start_run("MANUAL")
    repo.finish_run(rid, sources_swept=1, posts_fetched=5, passed_prefilter=3,
                    extracted=2, candidates_published=2, error=None)
    latest = repo.latest_run()
    assert latest.candidates_published == 2 and latest.trigger == "MANUAL"
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd ingestion-service && python -m pytest tests/test_repository.py -q`
Expected: FAIL (imports of `create_schema`/`Repository` fail). (Requires the live `ingestion_db` reachable at localhost:5436 via the dev override — confirm `docker compose up -d postgres-notifications` first.)

- [ ] **Step 3: Implement `app/db.py`** (connection + schema DDL):

```python
import psycopg

SCHEMA = """
CREATE TABLE IF NOT EXISTS sources (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    tme_url TEXT NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_ref TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS ingested_posts (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    post_ref TEXT NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (source_id, post_ref)
);
CREATE TABLE IF NOT EXISTS ingestion_runs (
    id BIGSERIAL PRIMARY KEY,
    trigger TEXT NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT now(),
    finished_at TIMESTAMP,
    sources_swept INT DEFAULT 0,
    posts_fetched INT DEFAULT 0,
    passed_prefilter INT DEFAULT 0,
    extracted INT DEFAULT 0,
    candidates_published INT DEFAULT 0,
    error TEXT
);
"""

def connect(dsn: str) -> psycopg.Connection:
    return psycopg.connect(dsn, autocommit=True)

def create_schema(conn: psycopg.Connection) -> None:
    with conn.cursor() as cur:
        cur.execute(SCHEMA)
```

- [ ] **Step 4: Implement `app/models.py`** (dataclasses):

```python
from dataclasses import dataclass
from datetime import datetime

@dataclass
class Source:
    id: int
    name: str
    tme_url: str
    enabled: bool
    last_seen_ref: str | None

@dataclass
class Run:
    id: int
    trigger: str
    candidates_published: int
    finished_at: datetime | None
```

- [ ] **Step 5: Implement `app/repository.py`** with the methods in the Interfaces block (psycopg queries against the schema). Full method bodies:

```python
from app.models import Source, Run

class Repository:
    def __init__(self, conn):
        self.conn = conn

    def add_source(self, name, tme_url) -> Source:
        row = self.conn.execute(
            "INSERT INTO sources(name, tme_url) VALUES (%s,%s) RETURNING id, enabled, last_seen_ref",
            (name, tme_url)).fetchone()
        return Source(id=row[0], name=name, tme_url=tme_url, enabled=row[1], last_seen_ref=row[2])

    def list_sources(self, enabled_only=False) -> list[Source]:
        q = "SELECT id,name,tme_url,enabled,last_seen_ref FROM sources"
        if enabled_only: q += " WHERE enabled = TRUE"
        q += " ORDER BY id"
        return [Source(*r) for r in self.conn.execute(q).fetchall()]

    def set_source_enabled(self, source_id, enabled):
        self.conn.execute("UPDATE sources SET enabled=%s, updated_at=now() WHERE id=%s", (enabled, source_id))

    def is_post_seen(self, source_id, post_ref) -> bool:
        return self.conn.execute(
            "SELECT 1 FROM ingested_posts WHERE source_id=%s AND post_ref=%s", (source_id, post_ref)
        ).fetchone() is not None

    def mark_post_seen(self, source_id, post_ref):
        self.conn.execute(
            "INSERT INTO ingested_posts(source_id, post_ref) VALUES (%s,%s) ON CONFLICT DO NOTHING",
            (source_id, post_ref))

    def update_last_seen(self, source_id, post_ref):
        self.conn.execute("UPDATE sources SET last_seen_ref=%s, updated_at=now() WHERE id=%s", (post_ref, source_id))

    def start_run(self, trigger) -> int:
        return self.conn.execute(
            "INSERT INTO ingestion_runs(trigger) VALUES (%s) RETURNING id", (trigger,)).fetchone()[0]

    def finish_run(self, run_id, **counts):
        self.conn.execute(
            """UPDATE ingestion_runs SET finished_at=now(), sources_swept=%s, posts_fetched=%s,
               passed_prefilter=%s, extracted=%s, candidates_published=%s, error=%s WHERE id=%s""",
            (counts.get("sources_swept",0), counts.get("posts_fetched",0), counts.get("passed_prefilter",0),
             counts.get("extracted",0), counts.get("candidates_published",0), counts.get("error"), run_id))

    def latest_run(self) -> Run | None:
        r = self.conn.execute(
            "SELECT id,trigger,candidates_published,finished_at FROM ingestion_runs ORDER BY id DESC LIMIT 1"
        ).fetchone()
        return Run(*r) if r else None
```

- [ ] **Step 6: Run tests**

Run: `cd ingestion-service && python -m pytest tests/test_repository.py -q`
Expected: 3 passed.

- [ ] **Step 7: Commit**

```bash
git add ingestion-service/app/db.py ingestion-service/app/models.py ingestion-service/app/repository.py ingestion-service/tests/test_repository.py
git commit -m "feat(ingest): db schema + repository (sources, seen-posts, runs)"
```

---

### Task 5: Telegram web-mirror parser (t.me/s → posts)

**Files:**
- Create: `ingestion-service/app/telegram.py`, `ingestion-service/tests/fixtures/tme_kzdev.html`, `ingestion-service/tests/test_telegram.py`

**Interfaces:**
- Produces: `parse_posts(html: str, channel: str) -> list[Post]` where `Post` is a dataclass `{ ref: str, text: str, date: datetime | None, links: list[str] }`; and `async fetch_channel(url: str, timeout: float) -> str` (raw HTML via httpx). `ref` is the Telegram post id form `<channel>/<n>` from the `data-post` attribute. Consumed by Task 8.

- [ ] **Step 1: Save a real fixture.** Fetch a real public channel's web mirror and save it (this grounds the selectors on real markup):

```bash
cd ingestion-service && mkdir -p tests/fixtures
curl -s "https://t.me/s/kzdev" -o tests/fixtures/tme_kzdev.html || curl -s "https://t.me/s/telegram" -o tests/fixtures/tme_kzdev.html
head -c 200 tests/fixtures/tme_kzdev.html
```
(If network is unavailable, hand-write a minimal fixture with 2 `.tgme_widget_message` blocks following the known structure below.)

- [ ] **Step 2: Write the failing test** against the fixture. The known `t.me/s` DOM: each post is a `div.tgme_widget_message` with attribute `data-post="channel/NNNN"`; text is in `div.tgme_widget_message_text`; the time is in `time[datetime]`; links are `<a href>` inside the text div.

```python
# ingestion-service/tests/test_telegram.py
from pathlib import Path
from app.telegram import parse_posts

HTML = (Path(__file__).parent / "fixtures" / "tme_kzdev.html").read_text(encoding="utf-8")

def test_parse_posts_extracts_ref_and_text():
    posts = parse_posts(HTML, "kzdev")
    assert len(posts) >= 1
    p = posts[0]
    assert p.ref.startswith("kzdev/") or "/" in p.ref   # <channel>/<id>
    assert isinstance(p.text, str) and len(p.text) >= 0
    # date parsed from <time datetime=...> when present
    assert p.date is None or hasattr(p.date, "year")
```

- [ ] **Step 3: Run to verify it fails**

Run: `cd ingestion-service && python -m pytest tests/test_telegram.py -q`
Expected: FAIL (module missing).

- [ ] **Step 4: Implement `app/telegram.py`**

```python
from dataclasses import dataclass, field
from datetime import datetime
import httpx
from bs4 import BeautifulSoup

@dataclass
class Post:
    ref: str
    text: str
    date: datetime | None
    links: list[str] = field(default_factory=list)

async def fetch_channel(url: str, timeout: float) -> str:
    async with httpx.AsyncClient(timeout=timeout, headers={"User-Agent": "Mozilla/5.0 EventHubBot"}) as c:
        r = await c.get(url)
        r.raise_for_status()
        return r.text

def parse_posts(html: str, channel: str) -> list[Post]:
    soup = BeautifulSoup(html, "html.parser")
    posts: list[Post] = []
    for block in soup.select("div.tgme_widget_message"):
        ref = block.get("data-post") or ""
        if not ref:
            continue
        text_el = block.select_one("div.tgme_widget_message_text")
        text = text_el.get_text("\n", strip=True) if text_el else ""
        time_el = block.select_one("time[datetime]")
        date = None
        if time_el and time_el.get("datetime"):
            try:
                date = datetime.fromisoformat(time_el["datetime"].replace("Z", "+00:00"))
            except ValueError:
                date = None
        links = [a["href"] for a in (text_el.select("a[href]") if text_el else []) if a.get("href")]
        posts.append(Post(ref=ref, text=text, date=date, links=links))
    return posts
```

- [ ] **Step 5: Run tests**

Run: `cd ingestion-service && python -m pytest tests/test_telegram.py -q`
Expected: passed. (If the real fixture's markup differs, adjust the selectors to match the saved fixture — the fixture is ground truth.)

- [ ] **Step 6: Commit**

```bash
git add ingestion-service/app/telegram.py ingestion-service/tests/test_telegram.py ingestion-service/tests/fixtures
git commit -m "feat(ingest): parse public Telegram channel via t.me/s web mirror"
```

---

### Task 6: Cheap pre-filter (heuristic event detection, no LLM)

**Files:**
- Create: `ingestion-service/app/prefilter.py`, `ingestion-service/tests/test_prefilter.py`

**Interfaces:**
- Produces: `looks_like_event(text: str) -> bool` — True only if the text has BOTH a date-like signal AND an event keyword. Consumed by Task 8 (gates the LLM call).

- [ ] **Step 1: Write the failing test**

```python
# ingestion-service/tests/test_prefilter.py
from app.prefilter import looks_like_event

def test_event_post_passes():
    assert looks_like_event("Митап по Go 15 марта в Алматы, регистрация по ссылке") is True

def test_advertising_dropped():
    assert looks_like_event("Скидка 50% на курсы! Успей купить сегодня") is False

def test_text_without_date_dropped():
    assert looks_like_event("Приходите на наш воркшоп по Kotlin") is False

def test_text_without_event_keyword_dropped():
    assert looks_like_event("Завтра 20 июня будет солнечно") is False
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd ingestion-service && python -m pytest tests/test_prefilter.py -q`
Expected: FAIL (module missing).

- [ ] **Step 3: Implement `app/prefilter.py`**

```python
import re

_EVENT_KEYWORDS = re.compile(
    r"(митап|meetup|конференц|conference|хакатон|hackathon|воркшоп|workshop|"
    r"вебинар|webinar|лекци|семинар|встреч|регистрац|register|доклад|спикер|talk)",
    re.IGNORECASE)

# date signals: dd.mm / dd месяца / ISO / "завтра/сегодня" are NOT enough alone —
# require a concrete date token (day+month) to reduce false positives.
_MONTHS = (r"январ|феврал|март|апрел|ма[йя]|июн|июл|август|сентябр|октябр|ноябр|декабр|"
           r"jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec")
_DATE = re.compile(rf"(\b\d{{1,2}}[.\-/]\d{{1,2}}(?:[.\-/]\d{{2,4}})?\b|\b\d{{1,2}}\s*({_MONTHS})\w*)",
                   re.IGNORECASE)

def looks_like_event(text: str) -> bool:
    if not text:
        return False
    return bool(_EVENT_KEYWORDS.search(text)) and bool(_DATE.search(text))
```

- [ ] **Step 4: Run tests**

Run: `cd ingestion-service && python -m pytest tests/test_prefilter.py -q`
Expected: 4 passed.

- [ ] **Step 5: Commit**

```bash
git add ingestion-service/app/prefilter.py ingestion-service/tests/test_prefilter.py
git commit -m "feat(ingest): heuristic pre-filter to gate LLM calls"
```

---

### Task 7: Gemini extraction (strict JSON, isEvent, no hallucinated fields)

**Files:**
- Create: `ingestion-service/app/extractor.py`, `ingestion-service/tests/test_extractor.py`

**Interfaces:**
- Produces: `extract_event(text: str, api_key: str, model: str, http_post=...) -> Candidate | None` where `Candidate` is a dataclass matching the DTO fields (`title, short_description, full_description, event_date: datetime|None, city, location, online, tags: list[str], external_link`). Returns `None` when the model says `isEvent:false` or JSON is unparseable. `http_post` is an injectable callable (default real Gemini REST) so tests mock it. Consumed by Task 8.

- [ ] **Step 1: Write the failing test** (mock the HTTP call, assert strict parsing + isEvent:false → None):

```python
# ingestion-service/tests/test_extractor.py
import json
from app.extractor import extract_event, Candidate

def _gemini_reply(payload: dict) -> dict:
    # shape of Gemini generateContent response
    return {"candidates": [{"content": {"parts": [{"text": json.dumps(payload)}]}}]}

def test_extracts_event_fields():
    def fake_post(url, headers, json):  # noqa: A002
        return _gemini_reply({
            "isEvent": True, "title": "Go Meetup", "shortDescription": "Two talks",
            "fullDescription": "Go community meetup with two talks and networking in Almaty.",
            "eventDate": "2026-09-15T18:00:00", "city": "Алматы", "location": "SmartPoint",
            "online": False, "tags": ["backend"], "externalLink": "https://ev.kz/go"})
    c = extract_event("митап по go 15 сентября алматы", "k", "m", http_post=fake_post)
    assert isinstance(c, Candidate)
    assert c.title == "Go Meetup" and c.city == "Алматы" and c.online is False
    assert c.event_date.year == 2026 and c.tags == ["backend"]

def test_non_event_returns_none():
    def fake_post(url, headers, json):  # noqa: A002
        return _gemini_reply({"isEvent": False})
    assert extract_event("скидка 50%", "k", "m", http_post=fake_post) is None

def test_unparseable_returns_none():
    def fake_post(url, headers, json):  # noqa: A002
        return {"candidates": [{"content": {"parts": [{"text": "not json"}]}}]}
    assert extract_event("x", "k", "m", http_post=fake_post) is None
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd ingestion-service && python -m pytest tests/test_extractor.py -q`
Expected: FAIL (module missing).

- [ ] **Step 3: Implement `app/extractor.py`** — mirror the Java `AiTagSuggestionService` prompt discipline (strict JSON, temperature low, thinkingBudget 0):

```python
from dataclasses import dataclass
from datetime import datetime
import json
import httpx

API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"

SYSTEM = (
    "Ты извлекаешь IT-мероприятие из поста Telegram. Верни СТРОГО JSON. "
    "Если пост не анонс мероприятия (реклама, новость, мем) — верни {\"isEvent\": false}. "
    "Поля: isEvent(bool), title, shortDescription, fullDescription, eventDate(ISO 8601 или null), "
    "city, location, online(bool), tags(массив строк), externalLink. "
    "НЕ выдумывай данные, которых нет в посте — ставь null. Дату указывай только если она явно есть."
)

@dataclass
class Candidate:
    title: str
    short_description: str
    full_description: str
    event_date: datetime | None
    city: str | None
    location: str | None
    online: bool
    tags: list[str]
    external_link: str | None

def _default_post(url, headers, json):  # noqa: A002
    return httpx.post(url, headers=headers, json=json, timeout=30).json()

def extract_event(text, api_key, model, http_post=_default_post) -> Candidate | None:
    body = {
        "systemInstruction": {"parts": [{"text": SYSTEM}]},
        "contents": [{"role": "user", "parts": [{"text": text}]}],
        "generationConfig": {"responseMimeType": "application/json", "temperature": 0.1,
                             "maxOutputTokens": 800, "thinkingConfig": {"thinkingBudget": 0}},
    }
    try:
        resp = http_post(API_URL.format(model=model), {"x-goog-api-key": api_key}, body)
        raw = resp["candidates"][0]["content"]["parts"][0]["text"]
        data = json.loads(raw)
    except Exception:
        return None
    if not data.get("isEvent"):
        return None
    ed = data.get("eventDate")
    try:
        event_date = datetime.fromisoformat(ed) if ed else None
    except (TypeError, ValueError):
        event_date = None
    tags = data.get("tags") or []
    if not isinstance(tags, list):
        tags = []
    return Candidate(
        title=data.get("title") or "", short_description=data.get("shortDescription") or "",
        full_description=data.get("fullDescription") or "", event_date=event_date,
        city=data.get("city"), location=data.get("location"), online=bool(data.get("online")),
        tags=[str(t) for t in tags], external_link=data.get("externalLink"))
```

- [ ] **Step 4: Run tests**

Run: `cd ingestion-service && python -m pytest tests/test_extractor.py -q`
Expected: 3 passed.

- [ ] **Step 5: Commit**

```bash
git add ingestion-service/app/extractor.py ingestion-service/tests/test_extractor.py
git commit -m "feat(ingest): Gemini event extraction with strict JSON + isEvent gate"
```

---

### Task 8: Candidate validation + Kafka producer

**Files:**
- Create: `ingestion-service/app/validate.py`, `ingestion-service/app/producer.py`, `ingestion-service/tests/test_validate.py`

**Interfaces:**
- Produces: `to_valid_candidate(c: Candidate, post_text: str) -> dict | None` — returns a JSON-ready dict matching `EventCandidateFoundEvent`'s fields ONLY when the candidate satisfies the EventRequest constraints (future date; non-blank title 3–200; shortDescription 10–500 — derive from post if short; fullDescription ≥20 — fall back to `post_text`; non-blank location — fall back to city; else `None`). And `publish_candidate(dict, bootstrap: str)` sending to topic `event.candidate.found`. Consumed by Task 9.
- The dict keys must be the exact JSON field names Spring's `JsonDeserializer` expects on `EventCandidateFoundEvent`: `title, shortDescription, fullDescription, eventDate, city, location, online, tags, externalLink, sourceUrl, sourceChannel`, plus `eventId, timestamp, schemaVersion` from `BaseEvent` (produce them: a uuid, ISO now, and `1`).

- [ ] **Step 1: Write the failing validation test**

```python
# ingestion-service/tests/test_validate.py
from datetime import datetime, timedelta
from app.extractor import Candidate
from app.validate import to_valid_candidate

def _cand(**kw):
    base = dict(title="Go Meetup", short_description="Two talks on Go and tooling",
                full_description="A long enough description of the meetup for validation.",
                event_date=datetime.now()+timedelta(days=5), city="Алматы", location="SmartPoint",
                online=False, tags=["backend"], external_link=None)
    base.update(kw); return Candidate(**base)

def test_valid_candidate_maps_to_dict():
    d = to_valid_candidate(_cand(), "original post text here")
    assert d["title"] == "Go Meetup" and d["online"] is False
    assert d["eventDate"].startswith(str(datetime.now().year))
    assert d["schemaVersion"] == 1 and d["eventId"]

def test_past_date_rejected():
    assert to_valid_candidate(_cand(event_date=datetime.now()-timedelta(days=1)), "x") is None

def test_missing_date_rejected():
    assert to_valid_candidate(_cand(event_date=None), "x") is None

def test_blank_location_falls_back_to_city():
    d = to_valid_candidate(_cand(location=None), "post")
    assert d is not None and d["location"] == "Алматы"

def test_no_location_no_city_rejected():
    assert to_valid_candidate(_cand(location=None, city=None), "post") is None

def test_short_full_description_falls_back_to_post():
    d = to_valid_candidate(_cand(full_description="short"), "a sufficiently long original telegram post body")
    assert d is not None and len(d["fullDescription"]) >= 20
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd ingestion-service && python -m pytest tests/test_validate.py -q`
Expected: FAIL (module missing).

- [ ] **Step 3: Implement `app/validate.py`**

```python
import uuid
from datetime import datetime
from app.extractor import Candidate

def _clip(s: str, lo: int, hi: int) -> str | None:
    s = (s or "").strip()
    if len(s) < lo:
        return None
    return s[:hi]

def to_valid_candidate(c: Candidate, post_text: str) -> dict | None:
    if c.event_date is None or c.event_date <= datetime.now():
        return None
    title = _clip(c.title, 3, 200)
    if title is None:
        return None
    short = _clip(c.short_description, 10, 500) or _clip(post_text, 10, 500)
    if short is None:
        return None
    full = _clip(c.full_description, 20, 20000) or _clip(post_text, 20, 20000)
    if full is None:
        return None
    location = (c.location or c.city or "").strip()
    if not location:
        return None
    return {
        "eventId": str(uuid.uuid4()),
        "timestamp": datetime.now().isoformat(),
        "schemaVersion": 1,
        "title": title, "shortDescription": short, "fullDescription": full,
        "eventDate": c.event_date.isoformat(),
        "city": c.city, "location": location[:200], "online": bool(c.online),
        "tags": c.tags, "externalLink": c.external_link,
        "sourceUrl": None, "sourceChannel": None,  # filled by the orchestrator per post
    }
```

- [ ] **Step 4: Implement `app/producer.py`** (kafka via a minimal client — use `httpx` to Kafka? No — use `kafka-python`). Add `kafka-python>=2.0` to `pyproject.toml` dependencies, then:

```python
import json
from kafka import KafkaProducer

def make_producer(bootstrap: str) -> KafkaProducer:
    return KafkaProducer(bootstrap_servers=bootstrap,
                         value_serializer=lambda v: json.dumps(v).encode("utf-8"),
                         key_serializer=lambda k: (k or "").encode("utf-8"))

def publish_candidate(producer: KafkaProducer, candidate: dict) -> None:
    producer.send("event.candidate.found", key=candidate.get("sourceUrl") or "", value=candidate)
    producer.flush()
```

- [ ] **Step 5: Run tests**

Run: `cd ingestion-service && python -m pytest tests/test_validate.py -q`
Expected: 6 passed.

- [ ] **Step 6: Commit**

```bash
git add ingestion-service/app/validate.py ingestion-service/app/producer.py ingestion-service/tests/test_validate.py ingestion-service/pyproject.toml
git commit -m "feat(ingest): candidate validation (EventRequest constraints) + kafka producer"
```

---

### Task 9: Pipeline orchestration (per-source sweep)

**Files:**
- Create: `ingestion-service/app/pipeline.py`, `ingestion-service/tests/test_pipeline.py`

**Interfaces:**
- Consumes: everything from Tasks 4–8.
- Produces: `async run_sweep(repo, producer, settings, trigger: str, fetcher=fetch_channel, extractor=extract_event) -> dict` (returns the run counts). It: starts a run, for each enabled source fetches HTML → parses posts → skips seen → pre-filters → extracts → validates (stamping sourceUrl/sourceChannel) → publishes → marks seen → advances last_seen → per-source try/except; finishes the run with counts. `fetcher`/`extractor` injected for tests.

- [ ] **Step 1: Write the failing test** (all deps faked — no network, no Kafka, no Gemini):

```python
# ingestion-service/tests/test_pipeline.py
import asyncio
from app.pipeline import run_sweep
from app.models import Source

class FakeRepo:
    def __init__(self):
        self.seen=set(); self.published=[]; self.runs=[]; self.last={}
    def start_run(self,t): self.runs.append(t); return 1
    def list_sources(self, enabled_only=False): return [Source(1,"KZ","https://t.me/s/kz",True,None)]
    def is_post_seen(self,sid,ref): return ref in self.seen
    def mark_post_seen(self,sid,ref): self.seen.add(ref)
    def update_last_seen(self,sid,ref): self.last[sid]=ref
    def finish_run(self, rid, **c): self.finished=c

class FakeProducer:
    def __init__(self): self.sent=[]
    def send(self, topic, key, value): self.sent.append(value)
    def flush(self): pass

def test_sweep_publishes_valid_event(monkeypatch):
    from app import pipeline
    async def fake_fetch(url, timeout): return "<html/>"
    def fake_parse(html, channel):
        from app.telegram import Post
        return [Post(ref="kz/10", text="Митап по Go 15 сентября 2026 Алматы регистрация", date=None)]
    def fake_extract(text, key, model, http_post=None):
        from app.extractor import Candidate
        from datetime import datetime
        return Candidate("Go Meetup","two talks on go","long enough description of the meetup",
                         datetime(2026,9,15,18), "Алматы","SmartPoint",False,["backend"],None)
    monkeypatch.setattr(pipeline, "parse_posts", fake_parse)
    repo, prod = FakeRepo(), FakeProducer()
    class S: fetch_delay_seconds=0; http_timeout_seconds=5; gemini_api_key="k"; gemini_model="m"
    counts = asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=fake_fetch, extractor=fake_extract))
    assert counts["candidates_published"] == 1
    assert prod.sent[0]["title"] == "Go Meetup"
    assert prod.sent[0]["sourceChannel"] == "kz"
    assert prod.sent[0]["sourceUrl"] == "https://t.me/kz/10"

def test_seen_post_skipped(monkeypatch):
    from app import pipeline
    async def fake_fetch(url, timeout): return "<html/>"
    def fake_parse(html, channel):
        from app.telegram import Post
        return [Post(ref="kz/10", text="x", date=None)]
    monkeypatch.setattr(pipeline, "parse_posts", fake_parse)
    repo, prod = FakeRepo(), FakeProducer(); repo.seen.add("kz/10")
    class S: fetch_delay_seconds=0; http_timeout_seconds=5; gemini_api_key="k"; gemini_model="m"
    counts = asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=fake_fetch,
                                   extractor=lambda *a, **k: None))
    assert counts["candidates_published"] == 0
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd ingestion-service && python -m pytest tests/test_pipeline.py -q`
Expected: FAIL (module missing).

- [ ] **Step 3: Implement `app/pipeline.py`**

```python
import asyncio
import logging
from app.telegram import fetch_channel, parse_posts
from app.prefilter import looks_like_event
from app.extractor import extract_event
from app.validate import to_valid_candidate
from app.producer import publish_candidate

log = logging.getLogger("pipeline")

async def run_sweep(repo, producer, settings, trigger, fetcher=fetch_channel, extractor=extract_event):
    run_id = repo.start_run(trigger)
    counts = dict(sources_swept=0, posts_fetched=0, passed_prefilter=0, extracted=0, candidates_published=0)
    error = None
    try:
        for src in repo.list_sources(enabled_only=True):
            counts["sources_swept"] += 1
            try:
                channel = src.tme_url.rstrip("/").split("/")[-1]
                html = await fetcher(src.tme_url, settings.http_timeout_seconds)
                posts = parse_posts(html, channel)
                newest_ref = None
                for post in posts:
                    counts["posts_fetched"] += 1
                    if repo.is_post_seen(src.id, post.ref):
                        continue
                    newest_ref = post.ref
                    if not looks_like_event(post.text):
                        repo.mark_post_seen(src.id, post.ref)
                        continue
                    counts["passed_prefilter"] += 1
                    try:
                        cand = extractor(post.text, settings.gemini_api_key, settings.gemini_model)
                    except Exception as e:  # per-post failure never aborts the run
                        log.warning("extract failed for %s: %s", post.ref, e)
                        cand = None
                    if cand is not None:
                        counts["extracted"] += 1
                        payload = to_valid_candidate(cand, post.text)
                        if payload is not None:
                            payload["sourceChannel"] = channel
                            payload["sourceUrl"] = f"https://t.me/{post.ref}"
                            publish_candidate(producer, payload)
                            counts["candidates_published"] += 1
                    repo.mark_post_seen(src.id, post.ref)
                    await asyncio.sleep(0)  # cooperative
                if newest_ref:
                    repo.update_last_seen(src.id, newest_ref)
                await asyncio.sleep(settings.fetch_delay_seconds)
            except Exception as e:  # per-source failure logged, run continues
                log.error("source %s failed: %s", src.tme_url, e)
                error = f"{src.tme_url}: {e}"
    finally:
        repo.finish_run(run_id, error=error, **counts)
    return counts
```

- [ ] **Step 4: Run tests**

Run: `cd ingestion-service && python -m pytest tests/test_pipeline.py -q`
Expected: 2 passed.

- [ ] **Step 5: Commit**

```bash
git add ingestion-service/app/pipeline.py ingestion-service/tests/test_pipeline.py
git commit -m "feat(ingest): pipeline orchestration (fetch→dedup→prefilter→extract→validate→publish)"
```

---

### Task 10: HTTP API (sources CRUD + run) + scheduler + metrics

**Files:**
- Create: `ingestion-service/app/api.py`, `ingestion-service/app/scheduler.py`, `ingestion-service/tests/test_api.py`
- Modify: `ingestion-service/app/main.py` (mount router, metrics, scheduler, DB connection)

**Interfaces:**
- Produces: `GET /api/ingestion/sources`, `POST /api/ingestion/sources {name,tmeUrl}`, `PATCH /api/ingestion/sources/{id} {enabled}`, `POST /api/ingestion/run`, `GET /api/ingestion/status`, `GET /metrics` (prometheus). All admin-gated downstream by the gateway (Task 12) — the service trusts it's called through the gateway (like the Java services trust X-User-Role).

- [ ] **Step 1: Write the failing API test** (FastAPI TestClient with the repo/pipeline faked via dependency override):

```python
# ingestion-service/tests/test_api.py
from fastapi.testclient import TestClient
from app.main import app, get_repo

class FakeRepo:
    def __init__(self): self._s=[]
    def list_sources(self, enabled_only=False): return self._s
    def add_source(self, name, url):
        from app.models import Source
        s=Source(len(self._s)+1,name,url,True,None); self._s.append(s); return s
    def set_source_enabled(self, sid, enabled): pass
    def latest_run(self): return None

def test_add_and_list_source():
    app.dependency_overrides[get_repo] = lambda: FakeRepo()
    c = TestClient(app)
    r = c.post("/api/ingestion/sources", json={"name":"KZ","tmeUrl":"https://t.me/s/kz"})
    assert r.status_code == 201 and r.json()["name"] == "KZ"
    app.dependency_overrides.clear()
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd ingestion-service && python -m pytest tests/test_api.py -q`
Expected: FAIL (`get_repo` / routes missing).

- [ ] **Step 3: Implement `app/api.py`** (router with Pydantic models):

```python
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

router = APIRouter(prefix="/api/ingestion")

class SourceIn(BaseModel):
    name: str
    tmeUrl: str

class EnabledIn(BaseModel):
    enabled: bool

def build_router(get_repo, run_sweep_now):
    @router.get("/sources")
    def list_sources(repo=Depends(get_repo)):
        return [vars(s) for s in repo.list_sources()]

    @router.post("/sources", status_code=201)
    def add_source(body: SourceIn, repo=Depends(get_repo)):
        return vars(repo.add_source(body.name, body.tmeUrl))

    @router.patch("/sources/{sid}")
    def set_enabled(sid: int, body: EnabledIn, repo=Depends(get_repo)):
        repo.set_source_enabled(sid, body.enabled)
        return {"ok": True}

    @router.post("/run")
    async def run_now():
        counts = await run_sweep_now("MANUAL")
        return counts

    @router.get("/status")
    def status(repo=Depends(get_repo)):
        r = repo.latest_run()
        return vars(r) if r else {"trigger": None}

    return router
```

- [ ] **Step 4: Wire `app/main.py`** — DB connection, `get_repo` dependency, metrics, scheduler, router. Full file:

```python
from contextlib import asynccontextmanager
from fastapi import FastAPI
from prometheus_client import make_asgi_app
from app.config import settings
from app.db import connect, create_schema
from app.repository import Repository
from app.api import build_router
from app.pipeline import run_sweep
from app.producer import make_producer
from app.scheduler import start_scheduler

_conn = None
_producer = None

def get_repo() -> Repository:
    return Repository(_conn)

async def run_sweep_now(trigger: str):
    return await run_sweep(get_repo(), _producer, settings, trigger)

@asynccontextmanager
async def lifespan(app: FastAPI):
    global _conn, _producer
    _conn = connect(settings.db_url)
    create_schema(_conn)
    _producer = make_producer(settings.kafka_bootstrap)
    scheduler = start_scheduler(settings.schedule_cron, run_sweep_now)
    yield
    scheduler.shutdown(wait=False)
    _producer.close()
    _conn.close()

app = FastAPI(title="ingestion-service", lifespan=lifespan)
app.include_router(build_router(get_repo, run_sweep_now))
app.mount("/metrics", make_asgi_app())

@app.get("/health")
def health():
    return {"status": "UP"}
```

Note: the health test from Task 3 imports `app` at module load; `lifespan` only runs under a real server / TestClient context, so `get_repo` is safe to override in tests. Verify `tests/test_health.py` still passes after this change (TestClient triggers lifespan; if the DB isn't reachable in the unit-test env, guard `connect` or keep the health route independent — it is).

- [ ] **Step 5: Implement `app/scheduler.py`**

```python
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
import asyncio

def start_scheduler(cron_expr: str, run_sweep_now):
    sched = BackgroundScheduler()
    def job():
        asyncio.run(run_sweep_now("SCHEDULED"))
    sched.add_job(job, CronTrigger.from_crontab(cron_expr), id="sweep", replace_existing=True)
    sched.start()
    return sched
```

- [ ] **Step 6: Run tests**

Run: `cd ingestion-service && python -m pytest tests/test_api.py tests/test_health.py -q`
Expected: passed. (If TestClient's lifespan tries to connect to a DB that isn't up in the unit env, run these two tests with the dev stack's `postgres-notifications` reachable, or split the health test to not trigger lifespan — acceptable either way; note the outcome.)

- [ ] **Step 7: Commit**

```bash
git add ingestion-service/app/api.py ingestion-service/app/scheduler.py ingestion-service/app/main.py ingestion-service/tests/test_api.py
git commit -m "feat(ingest): sources CRUD + run endpoint + scheduler + metrics"
```

---

### Task 11: event-service consumes candidates → EventRequest with event-level dedup

**Files:**
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/repository/EventRequestRepository.java` (dedup lookup), `event-service/src/main/java/org/ngcvfb/eventservice/repository/EventRepository.java` (dedup lookup)
- Create: `event-service/src/main/java/org/ngcvfb/eventservice/kafka/CandidateKafkaConsumer.java`, `event-service/src/main/java/org/ngcvfb/eventservice/service/CandidateService.java`
- Test: `event-service/src/test/java/org/ngcvfb/eventservice/service/CandidateServiceTest.java`

**Interfaces:**
- Consumes: `EventCandidateFoundEvent` (Task 2); `EventRequest`/`RequestSource` (Task 1).
- Produces: `CandidateService.ingest(EventCandidateFoundEvent)` → creates `EventRequest(source=AI_INGEST, status=PENDING, requesterId=null)` unless a normalized-key duplicate already exists in `Event` or a PENDING `EventRequest`. `CandidateKafkaConsumer` listens on `event.candidate.found`, group `event-service-group`.

- [ ] **Step 1: Write the failing test**

```java
package org.ngcvfb.eventservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.eventhubkz.common.events.EventCandidateFoundEvent;
import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.model.RequestSource;
import org.ngcvfb.eventservice.repository.EventRepository;
import org.ngcvfb.eventservice.repository.EventRequestRepository;

import java.time.LocalDateTime;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock EventRequestRepository requestRepo;
    @Mock EventRepository eventRepo;
    @InjectMocks CandidateService service;

    private EventCandidateFoundEvent candidate() {
        return EventCandidateFoundEvent.create("Go Meetup", "short desc here",
                "full description long enough", LocalDateTime.now().plusDays(7),
                "Алматы", "SmartPoint", false, Set.of("backend"), null,
                "https://t.me/kz/10", "kz");
    }

    @Test
    void newCandidateCreatesAiIngestRequest() {
        when(eventRepo.existsByNormalizedKey(anyString())).thenReturn(false);
        when(requestRepo.existsPendingByNormalizedKey(anyString())).thenReturn(false);

        service.ingest(candidate());

        verify(requestRepo).save(argThat((EventRequest r) ->
                r.getSource() == RequestSource.AI_INGEST
                && r.getRequesterId() == null
                && r.getSourceChannel().equals("kz")));
    }

    @Test
    void duplicateInCatalogIsSkipped() {
        when(eventRepo.existsByNormalizedKey(anyString())).thenReturn(true);
        service.ingest(candidate());
        verify(requestRepo, never()).save(any());
    }

    @Test
    void duplicatePendingRequestIsSkipped() {
        when(eventRepo.existsByNormalizedKey(anyString())).thenReturn(false);
        when(requestRepo.existsPendingByNormalizedKey(anyString())).thenReturn(true);
        service.ingest(candidate());
        verify(requestRepo, never()).save(any());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -pl event-service -Dtest=CandidateServiceTest`
Expected: COMPILATION ERROR (`CandidateService`, repo methods missing).

- [ ] **Step 3: Add the dedup repository queries.** In `EventRepository`:

```java
    @Query("SELECT COUNT(e) > 0 FROM Event e WHERE LOWER(TRIM(e.title)) = :key")
    boolean existsByNormalizedTitle(@Param("key") String key);
```

Wait — the normalized key is title+date+city; a title-only match is too broad. Use a service-computed key comparison. Simpler and correct: query candidates by title+eventDate window and compare in the service. Define in `EventRepository`:

```java
    List<Event> findByTitleIgnoreCaseAndEventDateBetween(String title, LocalDateTime from, LocalDateTime to);
```

and in `EventRequestRepository`:

```java
    List<EventRequest> findByStatusAndTitleIgnoreCaseAndEventDateBetween(
            RequestStatus status, String title, LocalDateTime from, LocalDateTime to);
```

The service computes the day window `[eventDate.toLocalDate().atStartOfDay(), +1 day]` and checks whether any returned row has the same normalized city — this is the "normalized key" in practice and avoids a brittle string column. (Replace the `existsByNormalizedKey`/`existsPendingByNormalizedKey` names in the test with these; adjust the test's `when(...)` to stub the `findBy...` methods returning empty vs a matching row.)

- [ ] **Step 4: Implement `CandidateService`**

```java
package org.ngcvfb.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.EventCandidateFoundEvent;
import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.model.RequestSource;
import org.ngcvfb.eventservice.model.RequestStatus;
import org.ngcvfb.eventservice.repository.EventRepository;
import org.ngcvfb.eventservice.repository.EventRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandidateService {

    private final EventRequestRepository requestRepo;
    private final EventRepository eventRepo;

    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(); }

    @Transactional
    public void ingest(EventCandidateFoundEvent c) {
        LocalDateTime from = c.getEventDate().toLocalDate().atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        String city = norm(c.getCity());

        boolean dupEvent = eventRepo
                .findByTitleIgnoreCaseAndEventDateBetween(c.getTitle(), from, to).stream()
                .anyMatch(e -> norm(e.getLocation()).contains(city) || city.isEmpty());
        boolean dupPending = requestRepo
                .findByStatusAndTitleIgnoreCaseAndEventDateBetween(RequestStatus.PENDING, c.getTitle(), from, to)
                .stream().anyMatch(r -> norm(r.getLocation()).contains(city) || city.isEmpty());
        if (dupEvent || dupPending) {
            log.info("Skipping duplicate AI candidate: {} @ {}", c.getTitle(), c.getEventDate());
            return;
        }

        EventRequest r = EventRequest.builder()
                .title(c.getTitle()).shortDescription(c.getShortDescription())
                .fullDescription(c.getFullDescription()).location(c.getLocation())
                .online(c.isOnline()).eventDate(c.getEventDate())
                .tags(c.getTags() == null ? new HashSet<>() : new HashSet<>(c.getTags()))
                .externalLink(c.getExternalLink())
                .source(RequestSource.AI_INGEST).sourceUrl(c.getSourceUrl())
                .sourceChannel(c.getSourceChannel()).requesterId(null)
                .status(RequestStatus.PENDING).build();
        requestRepo.save(r);
        log.info("Created AI_INGEST EventRequest from {}", c.getSourceChannel());
    }
}
```

- [ ] **Step 5: Implement `CandidateKafkaConsumer`**

```java
package org.ngcvfb.eventservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.EventCandidateFoundEvent;
import org.ngcvfb.eventservice.service.CandidateService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CandidateKafkaConsumer {

    private final CandidateService candidateService;

    @KafkaListener(topics = "event.candidate.found", groupId = "event-service-group")
    public void handle(EventCandidateFoundEvent event) {
        log.info("Received event.candidate.found from {}", event.getSourceChannel());
        try {
            candidateService.ingest(event);
        } catch (Exception e) {
            log.error("Failed to ingest candidate from {}", event.getSourceChannel(), e);
        }
    }
}
```

(Adjust the `CandidateServiceTest` stubs to the `findBy...` methods per Step 3.)

- [ ] **Step 6: Run tests**

Run: `mvn -q test -pl event-service -Dtest=CandidateServiceTest && mvn -q test -pl event-service`
Expected: green.

- [ ] **Step 7: Commit**

```bash
git add event-service/src
git commit -m "feat(event): consume event.candidate.found → AI_INGEST EventRequest with dedup"
```

---

### Task 12: Gateway routes + prometheus scrape for ingestion-service

**Files:**
- Modify: `api-gateway/src/main/resources/application.yml` (routes to `http://ingestion-service:8092`, admin-gated)
- Modify: `monitoring/prometheus/prometheus.yml` (scrape job)

**Interfaces:**
- Produces: `/api/ingestion/**` reachable through the gateway with `AuthenticationFilter` (admin enforced downstream — but the Python service has no role check, so the gateway MUST restrict to ADMIN here). Consumed by the frontend (Task 13).

- [ ] **Step 1: Add the gateway route.** Because the Python service does not itself check `X-User-Role`, the gateway must both authenticate AND authorize admin. Add a route with `AuthenticationFilter` and, since there is no downstream role check, also confirm admin at the edge. Spring Cloud Gateway has no built-in role predicate; reuse the pattern of routing then relying on the service — but here we add a header-based guard is not possible declaratively. Simplest robust option: keep `AuthenticationFilter` (ensures a valid JWT and injects `X-User-Role`), and have the Python endpoints reject non-ADMIN by reading `X-User-Role` (add that check in `app/api.py`). Update the route:

```yaml
        # Ingestion Service (admin-only; Python service reads X-User-Role, gateway injects it)
        - id: ingestion-service
          uri: http://ingestion-service:8092
          predicates:
            - Path=/api/ingestion/**
          filters:
            - name: AuthenticationFilter
```

Place it among the other admin routes (order doesn't collide — no other route matches `/api/ingestion/**`).

- [ ] **Step 2: Add the admin check to the Python API.** In `app/api.py`, add a dependency that 403s non-admins, and apply it to the router. Add:

```python
from fastapi import Header

def require_admin(x_user_role: str = Header(default="")):
    if x_user_role.upper() != "ADMIN":
        raise HTTPException(status_code=403, detail="admin only")
```

Apply it: `router = APIRouter(prefix="/api/ingestion", dependencies=[Depends(require_admin)])`. Add a test to `tests/test_api.py`:

```python
def test_non_admin_forbidden():
    app.dependency_overrides[get_repo] = lambda: FakeRepo()
    c = TestClient(app)
    r = c.get("/api/ingestion/sources")  # no X-User-Role header
    assert r.status_code == 403
    app.dependency_overrides.clear()
```

(Update the earlier `test_add_and_list_source` to send `headers={"X-User-Role":"ADMIN"}`.)

- [ ] **Step 3: Add the prometheus job.** In `monitoring/prometheus/prometheus.yml` after the audit-service job:

```yaml
  - job_name: 'ingestion-service'
    metrics_path: '/metrics'
    static_configs:
      - targets: ['ingestion-service:8092']
    scrape_interval: 30s
```

- [ ] **Step 4: Rebuild + redeploy gateway and ingestion-service; smoke.**

```bash
mvn clean package -DskipTests -pl api-gateway && docker compose build api-gateway && docker rm -f api-gateway && docker compose up -d api-gateway
docker compose build ingestion-service && docker rm -f ingestion-service && docker compose up -d ingestion-service
sleep 30
# admin JWT:
TOKEN=$(curl -s -X POST http://localhost:8180/auth/login -H "Content-Type: application/json" -d '{"email":"dinara.zhumabaeva@example.kz","password":"password123"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['token'])")
curl -s -o /dev/null -w "admin sources: %{http_code}\n" http://localhost:8180/api/ingestion/sources -H "Authorization: Bearer $TOKEN"
curl -s -o /dev/null -w "no-auth: %{http_code}\n" http://localhost:8180/api/ingestion/sources
```
Expected: `admin sources: 200`; `no-auth: 401` (gateway rejects missing JWT).

- [ ] **Step 5: Run the Python API tests**

Run: `cd ingestion-service && python -m pytest tests/test_api.py -q`
Expected: passed (incl. the 403 test).

- [ ] **Step 6: Commit**

```bash
git add api-gateway/src/main/resources/application.yml monitoring/prometheus/prometheus.yml ingestion-service/app/api.py ingestion-service/tests/test_api.py
git commit -m "feat(infra): gateway route + admin guard + prometheus for ingestion-service"
```

---

### Task 13: Admin "Sources" tab + mark AI candidates in Requests

**Files:**
- Create: `frontend/src/components/AdminIngestionSources.jsx`
- Modify: `frontend/src/components/AdminDashboard.jsx` (register tab), `frontend/src/components/AdminEventRequests.jsx` (show source marker + link), `frontend/src/i18n/locales/ru.json`, `frontend/src/i18n/locales/kk.json`

**Interfaces:**
- Consumes: `/api/ingestion/sources`, `/api/ingestion/run`, `/api/ingestion/status` (Task 12) via `api`. The requests list from event-service now includes `source`, `sourceUrl`, `sourceChannel` on each request (Task 1 added them to the entity → they flow through the existing request DTO/serialization — verify the admin requests endpoint returns them; if it maps through a DTO, add the fields there).

- [ ] **Step 1: i18n keys.** Append inside `"admin"` in `ru.json` (text edit, validate):

```json
    "tabSources": "Источники (парсер)",
    "sourcesAdd": "Добавить канал",
    "sourcesName": "Название",
    "sourcesUrl": "Ссылка t.me/s/…",
    "sourcesEnabled": "Активен",
    "sourcesRun": "Запустить парсер",
    "sourcesRunning": "Идёт сбор…",
    "sourcesLastRun": "Последний запуск",
    "sourcesFound": "найдено кандидатов",
    "sourcesEmpty": "Источников пока нет",
    "sourcesLoadError": "Не удалось загрузить источники",
    "reqFromParser": "Найдено парсером",
    "reqSourceLink": "Оригинал поста"
```

`kk.json`:

```json
    "tabSources": "Дереккөздер (парсер)",
    "sourcesAdd": "Арна қосу",
    "sourcesName": "Атауы",
    "sourcesUrl": "Сілтеме t.me/s/…",
    "sourcesEnabled": "Белсенді",
    "sourcesRun": "Парсерді іске қосу",
    "sourcesRunning": "Жинау жүріп жатыр…",
    "sourcesLastRun": "Соңғы іске қосу",
    "sourcesFound": "кандидат табылды",
    "sourcesEmpty": "Әзірге дереккөздер жоқ",
    "sourcesLoadError": "Дереккөздерді жүктеу мүмкін болмады",
    "reqFromParser": "Парсер тапты",
    "reqSourceLink": "Түпнұсқа посты"
```

- [ ] **Step 2: Create `AdminIngestionSources.jsx`** (mirrors `AdminAuditLog.jsx` structure — api/toast/skeleton/error):

```jsx
import React, { useState, useEffect, useCallback } from 'react';
import api from '../api';
import toast from 'react-hot-toast';
import Skeleton from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';
import { useTranslation } from 'react-i18next';

const AdminIngestionSources = () => {
  const { t } = useTranslation();
  const [sources, setSources] = useState([]);
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [running, setRunning] = useState(false);
  const [name, setName] = useState('');
  const [url, setUrl] = useState('');

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const [s, st] = await Promise.all([
        api.get('/api/ingestion/sources'),
        api.get('/api/ingestion/status'),
      ]);
      setSources(s.data); setStatus(st.data);
    } catch (e) { setError(t('admin.sourcesLoadError')); }
    finally { setLoading(false); }
  }, [t]);

  useEffect(() => { load(); }, [load]);

  const add = async (e) => {
    e.preventDefault();
    if (!name.trim() || !url.trim()) return;
    try { await api.post('/api/ingestion/sources', { name: name.trim(), tmeUrl: url.trim() });
      setName(''); setUrl(''); load(); }
    catch { toast.error(t('admin.sourcesLoadError')); }
  };

  const toggle = async (s) => {
    try { await api.patch(`/api/ingestion/sources/${s.id}`, { enabled: !s.enabled }); load(); }
    catch { toast.error(t('admin.sourcesLoadError')); }
  };

  const run = async () => {
    setRunning(true);
    try { const r = await api.post('/api/ingestion/run');
      toast.success(`${t('admin.sourcesFound')}: ${r.data.candidates_published ?? 0}`); load(); }
    catch { toast.error(t('admin.sourcesLoadError')); }
    finally { setRunning(false); }
  };

  if (error) return <PageError message={error} onRetry={load} />;

  return (
    <div className="adm-sources">
      <form className="adm-sources__add" onSubmit={add}>
        <input value={name} onChange={e => setName(e.target.value)} placeholder={t('admin.sourcesName')} />
        <input value={url} onChange={e => setUrl(e.target.value)} placeholder={t('admin.sourcesUrl')} />
        <button type="submit">{t('admin.sourcesAdd')}</button>
        <button type="button" onClick={run} disabled={running}>
          {running ? t('admin.sourcesRunning') : t('admin.sourcesRun')}
        </button>
      </form>
      {status && status.trigger && (
        <p className="adm-sources__status">
          {t('admin.sourcesLastRun')}: {status.candidates_published ?? 0} {t('admin.sourcesFound')}
        </p>
      )}
      {loading ? <Skeleton /> : sources.length === 0 ? (
        <EmptyState icon="inbox" title={t('admin.sourcesEmpty')} />
      ) : (
        <table className="adm-sources__table">
          <thead><tr><th>{t('admin.sourcesName')}</th><th>t.me</th><th>{t('admin.sourcesEnabled')}</th></tr></thead>
          <tbody>
            {sources.map(s => (
              <tr key={s.id}>
                <td>{s.name}</td>
                <td><a href={s.tme_url} target="_blank" rel="noreferrer">{s.tme_url}</a></td>
                <td><input type="checkbox" checked={s.enabled} onChange={() => toggle(s)} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default AdminIngestionSources;
```

- [ ] **Step 3: Register the tab** in `AdminDashboard.jsx` — import, add `{ key: 'sources', labelKey: 'admin.tabSources' }` to `TABS`, and `{tab === 'sources' && <AdminIngestionSources />}`.

- [ ] **Step 4: Mark AI candidates in `AdminEventRequests.jsx`.** Where each request renders, if `request.source === 'AI_INGEST'` show a small badge `t('admin.reqFromParser')` and, when `request.sourceUrl`, a link `t('admin.reqSourceLink')` → `request.sourceUrl`. Read the component to place it in the request card header. (Verify the admin requests API returns `source`/`sourceUrl` — if it serializes the entity directly they're present; if via a DTO, add the fields to that DTO and its mapper.)

- [ ] **Step 5: Build**

Run: `cd frontend && npx vite build`
Expected: `✓ built`; both locale JSONs valid.

- [ ] **Step 6: Commit**

```bash
git add frontend/src
git commit -m "feat(admin): ingestion sources tab + AI-candidate markers in requests"
```

---

### Task 14: E2E verification + README + compose count

**Files:**
- Modify: `README.md` (service table row, Kafka topic, mermaid node, container count), and this task also verifies the whole flow live.

- [ ] **Step 1: End-to-end (live).** Add a real source and run the parser, or inject a candidate directly to prove the Java side. Preferred: publish a synthetic `event.candidate.found` to Kafka and verify an `EventRequest(AI_INGEST, PENDING)` appears; then via the admin UI approve it and confirm an `Event` is created. Concretely:

```bash
# add a real source through the API and run a sweep
TOKEN=$(curl -s -X POST http://localhost:8180/auth/login -H "Content-Type: application/json" -d '{"email":"dinara.zhumabaeva@example.kz","password":"password123"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['token'])")
curl -s -X POST http://localhost:8180/api/ingestion/sources -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"KZ Dev","tmeUrl":"https://t.me/s/kzdev"}'
curl -s -X POST http://localhost:8180/api/ingestion/run -H "Authorization: Bearer $TOKEN"
sleep 5
# verify a PENDING AI_INGEST request landed
docker exec postgres-events psql -U postgres -d events_db -tAc "SELECT count(*) FROM event_requests WHERE source='AI_INGEST'"
```
Expected: the run returns counts; the count query returns ≥ 0 (≥ 1 if the channel had a valid upcoming event). If 0 because the live channel had no parseable upcoming event, fall back to publishing a synthetic candidate to `event.candidate.found` via a small script and re-check — the goal is to prove the consumer path.

- [ ] **Step 2: Browser check.** Open `/admin` → «Источники (парсер)»: the added source shows, «Запустить парсер» works, last-run status shows. In «Заявки», any AI candidate shows the "Найдено парсером" badge + original-post link. Zero console errors. Screenshot both.

- [ ] **Step 3: README.** Add to the service table: `| ingestion-service | 8092 | AI parser: reads Telegram channels → candidate events (Python) |`; add `event.candidate.found` to the Kafka topics table (producer ingestion-service, consumer event-service); add an `INGEST[ingestion-service]` node to the mermaid diagram feeding Kafka; note the polyglot addition (Python) in the tech-stack table; bump the container count. Update the intro's service count.

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: document ingestion-service and the AI candidate pipeline"
```

---

## Self-Review Notes

- **Spec coverage:** Python service scaffold (T3), db layer (T4), t.me/s parser (T5), pre-filter (T6), Gemini extraction (T7), validation+producer (T8), pipeline (T9), API+scheduler+metrics (T10); EventRequest changes (T1), candidate DTO + notify-guard (T2), consumer+dedup (T11); gateway+prometheus (T12); admin UI (T13); E2E+README (T14). Every spec section maps to a task.
- **The EventRequest-validation trap** (fullDescription≥20, location non-blank, eventDate @Future fire on JPA persist) is handled in T8's `to_valid_candidate` (drops invalid candidates, falls back for description/location) — the single most likely source of runtime failures if missed.
- **Non-Eureka gateway routing** (`uri: http://ingestion-service:8092`, not `lb://`) and the **admin guard living in the Python service** (gateway can't do a role predicate declaratively) are both called out in T12.
- **Config-vs-unit split:** Python has real pytest units for every pure unit (parser, prefilter, extractor, validate, pipeline, repo, api). T11 has Java unit tests. T12/T14 are live integration. T5 depends on a saved real fixture (ground truth for selectors).
- **Placeholder scan:** the dedup repo-method naming was corrected inline in T11 Step 3 (title+date-window + city compare, not a brittle normalized-string column) — the test stubs must match those `findBy...` methods.
- **Rebuild gotchas:** T1/T2/T11 change Java baked into jars → rebuild event-service; T12 rebuilds gateway + ingestion; ingestion_db needs manual CREATE on the existing volume (T3 Step 9).
