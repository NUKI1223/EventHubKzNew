# Flyway Migrations Implementation Plan

> **For agentic workers:** Execute inline in-session (superpowers:executing-plans) — this is live-stack infra work where each task is verified by rebuilding a service and confirming it boots with `ddl-auto: validate`. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Replace `ddl-auto: update` on all 8 DB-backed services with Flyway-managed baselines + `ddl-auto: validate`, eliminating silent schema drift in prod.

**Architecture:** Each of the 6 databases gets one Flyway owner service that ships a `V1__baseline.sql` (the current schema, dumped from the running DB). `baseline-on-migrate` stamps existing populated DBs without re-running V1 (zero data risk); fresh deploys build the schema from V1. The two shared DBs (`users_db`, `events_db`) have one owner each; the co-tenant only validates.

**Tech Stack:** Spring Boot 3.4, Flyway (`flyway-core` + `flyway-database-postgresql`), PostgreSQL, Docker Compose. Host build: `mvn package` then `docker compose build` (jar is copied by the Dockerfile).

## Global Constraints

- Spring Boot 3.4 → Flyway needs BOTH `org.flywaydb:flyway-core` and `org.flywaydb:flyway-database-postgresql` (no explicit version — managed by the parent).
- Every one of the 8 services ends on `spring.jpa.hibernate.ddl-auto: validate`. No `update` may remain.
- Owners: auth (users_db), event (events_db), like, registration, notification, audit. Co-tenants (validate only, no Flyway): user, tag.
- Baseline SQL lives at `<owner>/src/main/resources/db/migration/V1__baseline.sql`.
- Git commits are the user's alone — NO `Co-Authored-By` / attribution lines.
- Java rebuild recipe: `mvn -q clean package -pl <module> -DskipTests` → `docker compose build <svc>` → `docker compose up -d --force-recreate --no-deps <svc>` → wait → check logs + `/health`.
- Boot success = logs show Flyway `Successfully baselined schema ... to version 1` (or `Successfully validated`) AND no `SchemaManagementException` / `Schema-validation:` error AND the app reaches "Started ...Application".

---

### Task 1: Pilot — like-service (single-owner, prove the pattern)

**Files:**
- Modify: `like-service/pom.xml` (add Flyway deps)
- Create: `like-service/src/main/resources/db/migration/V1__baseline.sql` (from pg_dump)
- Modify: `like-service/src/main/resources/application.yml` (flyway config + ddl-auto: validate)

- [ ] **Step 1: Add Flyway dependencies** to `like-service/pom.xml` inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

- [ ] **Step 2: Generate the baseline** from the running DB:

```bash
mkdir -p like-service/src/main/resources/db/migration
docker exec postgres-likes pg_dump -U postgres -d likes_db --schema-only --no-owner --no-privileges \
  | grep -vE '^(SET |SELECT pg_catalog|--|$)' \
  | grep -viE 'flyway_schema_history' \
  > like-service/src/main/resources/db/migration/V1__baseline.sql
head -40 like-service/src/main/resources/db/migration/V1__baseline.sql
```

Expected: `CREATE TABLE` statements for the likes schema, no `SET`/comment noise, no flyway table.

- [ ] **Step 3: Configure Flyway + validate** in `like-service/src/main/resources/application.yml`. Change `ddl-auto: update` → `validate` and add a `flyway:` block under `spring:`:

```yaml
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 1
    locations: classpath:db/migration
  jpa:
    hibernate:
      ddl-auto: validate
```

- [ ] **Step 4: Rebuild + recreate**:

```bash
mvn -q clean package -pl like-service -DskipTests
docker compose build like-service
docker compose up -d --force-recreate --no-deps like-service
sleep 30
```

- [ ] **Step 5: Verify boot + validate**:

```bash
docker logs like-service 2>&1 | grep -iE 'flyway|baselin|validat|Started LikeService|Schema-validation|SchemaManagement' | tail -15
docker ps --filter name=like-service --format '{{.Names}} {{.Status}}'
```

Expected: Flyway baselines to v1, no `Schema-validation` error, "Started LikeServiceApplication". If a `Schema-validation: missing column/table X` appears, the baseline is missing something the entity expects — the running DB and entity are out of sync (unlikely since dump is from that DB); read the column it names, fix `V1__baseline.sql`, redo Steps 4–5.

- [ ] **Step 6: Commit**:

```bash
git add like-service/pom.xml like-service/src/main/resources/db/migration/V1__baseline.sql like-service/src/main/resources/application.yml
git commit -m "feat(like): Flyway baseline + ddl-auto validate"
```

---

### Task 2: registration-service (single-owner)

Same pattern as Task 1 with these substitutions: service `registration-service`, DB container `postgres-registrations`, DB `registrations_db`, main class `RegistrationServiceApplication`.

- [ ] **Step 1:** Add the two Flyway deps (Task 1 Step 1 XML) to `registration-service/pom.xml`.
- [ ] **Step 2:** Baseline:
```bash
mkdir -p registration-service/src/main/resources/db/migration
docker exec postgres-registrations pg_dump -U postgres -d registrations_db --schema-only --no-owner --no-privileges \
  | grep -vE '^(SET |SELECT pg_catalog|--|$)' | grep -viE 'flyway_schema_history' \
  > registration-service/src/main/resources/db/migration/V1__baseline.sql
```
- [ ] **Step 3:** In `registration-service/src/main/resources/application.yml` set `ddl-auto: validate` and add the `flyway:` block (Task 1 Step 3 YAML).
- [ ] **Step 4:** `mvn -q clean package -pl registration-service -DskipTests && docker compose build registration-service && docker compose up -d --force-recreate --no-deps registration-service && sleep 30`
- [ ] **Step 5:** `docker logs registration-service 2>&1 | grep -iE 'flyway|baselin|validat|Started Registration|Schema-validation' | tail -15` — expect baseline v1 + Started, no validation error.
- [ ] **Step 6:** `git add registration-service/pom.xml registration-service/src/main/resources/db/migration/V1__baseline.sql registration-service/src/main/resources/application.yml && git commit -m "feat(registration): Flyway baseline + ddl-auto validate"`

---

### Task 3: notification-service (single-owner)

Substitutions: service `notification-service`, container `postgres-notifications`, DB `notifications_db`, main class `NotificationServiceApplication`.

- [ ] **Step 1:** Add the two Flyway deps to `notification-service/pom.xml`.
- [ ] **Step 2:** Baseline:
```bash
mkdir -p notification-service/src/main/resources/db/migration
docker exec postgres-notifications pg_dump -U postgres -d notifications_db --schema-only --no-owner --no-privileges \
  | grep -vE '^(SET |SELECT pg_catalog|--|$)' | grep -viE 'flyway_schema_history' \
  > notification-service/src/main/resources/db/migration/V1__baseline.sql
```
- [ ] **Step 3:** `application.yml`: `ddl-auto: validate` + `flyway:` block.
- [ ] **Step 4:** rebuild+recreate notification-service (recipe), `sleep 30`.
- [ ] **Step 5:** logs grep `'flyway|baselin|validat|Started Notification|Schema-validation'` — expect clean.
- [ ] **Step 6:** commit `feat(notification): Flyway baseline + ddl-auto validate`.

---

### Task 4: audit-service (single-owner, separate DB on shared instance)

Substitutions: service `audit-service`, container `postgres-notifications`, DB `audit_db`, main class `AuditServiceApplication`. (Same instance as notifications_db but a different database → its own flyway history.)

- [ ] **Step 1:** Add the two Flyway deps to `audit-service/pom.xml`.
- [ ] **Step 2:** Baseline:
```bash
mkdir -p audit-service/src/main/resources/db/migration
docker exec postgres-notifications pg_dump -U postgres -d audit_db --schema-only --no-owner --no-privileges \
  | grep -vE '^(SET |SELECT pg_catalog|--|$)' | grep -viE 'flyway_schema_history' \
  > audit-service/src/main/resources/db/migration/V1__baseline.sql
```
- [ ] **Step 3:** `application.yml`: `ddl-auto: validate` + `flyway:` block.
- [ ] **Step 4:** rebuild+recreate audit-service, `sleep 30`.
- [ ] **Step 5:** logs grep `'flyway|baselin|validat|Started Audit|Schema-validation'`.
- [ ] **Step 6:** commit `feat(audit): Flyway baseline + ddl-auto validate`.

---

### Task 5: events_db shared — event-service (owner) + tag-service (validate)

**Files:** event-service pom + baseline + yaml (owner); tag-service yaml only (co-tenant).

- [ ] **Step 1:** Add the two Flyway deps to `event-service/pom.xml`.
- [ ] **Step 2:** Baseline for the WHOLE events_db (includes event tables AND `tags`):
```bash
mkdir -p event-service/src/main/resources/db/migration
docker exec postgres-events pg_dump -U postgres -d events_db --schema-only --no-owner --no-privileges \
  | grep -vE '^(SET |SELECT pg_catalog|--|$)' | grep -viE 'flyway_schema_history' \
  > event-service/src/main/resources/db/migration/V1__baseline.sql
grep -c 'CREATE TABLE' event-service/src/main/resources/db/migration/V1__baseline.sql   # expect ~7
```
- [ ] **Step 3 (owner):** `event-service/src/main/resources/application.yml` → `ddl-auto: validate` + `flyway:` block (enabled true).
- [ ] **Step 4 (co-tenant):** `tag-service/src/main/resources/application.yml` → `ddl-auto: validate` and `spring.flyway.enabled: false` (no baseline, no deps). Add:
```yaml
  flyway:
    enabled: false
  jpa:
    hibernate:
      ddl-auto: validate
```
- [ ] **Step 5:** Rebuild BOTH:
```bash
mvn -q clean package -pl event-service,tag-service -DskipTests
docker compose build event-service tag-service
docker compose up -d --force-recreate --no-deps event-service tag-service
sleep 40
```
- [ ] **Step 6:** Verify BOTH boot (event has 2 replicas):
```bash
for c in eventhubkz-event-service-1 eventhubkz-event-service-2 tag-service; do
  echo "== $c =="; docker logs "$c" 2>&1 | grep -iE 'baselin|validat|Started (Event|Tag)|Schema-validation' | tail -4
done
```
Expected: event-service baselines events_db to v1 + Started; tag-service validates cleanly + Started (no Schema-validation error against the event-owned schema).

- [ ] **Step 7:** Commit:
```bash
git add event-service/pom.xml event-service/src/main/resources/db/migration/V1__baseline.sql event-service/src/main/resources/application.yml tag-service/src/main/resources/application.yml
git commit -m "feat(event,tag): Flyway baseline on events_db (event owns) + tag validate"
```

---

### Task 6: users_db shared — auth-service (owner) + user-service (validate)

**Files:** auth-service pom + baseline + yaml (owner); user-service yaml only (co-tenant).

- [ ] **Step 1:** Add the two Flyway deps to `auth-service/pom.xml`.
- [ ] **Step 2:** Baseline for the WHOLE users_db (users + user_contacts + user_tags):
```bash
mkdir -p auth-service/src/main/resources/db/migration
docker exec postgres-users pg_dump -U postgres -d users_db --schema-only --no-owner --no-privileges \
  | grep -vE '^(SET |SELECT pg_catalog|--|$)' | grep -viE 'flyway_schema_history' \
  > auth-service/src/main/resources/db/migration/V1__baseline.sql
grep -c 'CREATE TABLE' auth-service/src/main/resources/db/migration/V1__baseline.sql   # expect 3
```
- [ ] **Step 3 (owner):** `auth-service/src/main/resources/application.yml` → `ddl-auto: validate` + `flyway:` block (enabled true).
- [ ] **Step 4 (co-tenant):** `user-service/src/main/resources/application.yml` → `ddl-auto: validate` + `spring.flyway.enabled: false`.
- [ ] **Step 5:** Rebuild BOTH (auth has 2 replicas):
```bash
mvn -q clean package -pl auth-service,user-service -DskipTests
docker compose build auth-service user-service
docker compose up -d --force-recreate --no-deps auth-service user-service
sleep 40
```
- [ ] **Step 6:** Verify BOTH boot:
```bash
for c in eventhubkz-auth-service-1 eventhubkz-auth-service-2 user-service; do
  echo "== $c =="; docker logs "$c" 2>&1 | grep -iE 'baselin|validat|Started (Auth|User)|Schema-validation' | tail -4
done
```
Expected: auth baselines users_db to v1 + Started; user-service validates cleanly + Started.

- [ ] **Step 7:** Smoke the critical path (login still works after auth touched):
```bash
curl -s -o /dev/null -w "login: %{http_code}\n" -X POST http://localhost:8180/auth/login \
  -H "Content-Type: application/json" -d '{"email":"dinara.zhumabaeva@example.kz","password":"password123"}'
```
Expected: `login: 200`.

- [ ] **Step 8:** Commit:
```bash
git add auth-service/pom.xml auth-service/src/main/resources/db/migration/V1__baseline.sql auth-service/src/main/resources/application.yml user-service/src/main/resources/application.yml
git commit -m "feat(auth,user): Flyway baseline on users_db (auth owns) + user validate"
```

---

### Task 7: Fresh-DB verification + final sweep

- [ ] **Step 1: Prove V1 builds a schema from scratch.** Use likes_db's baseline against a throwaway DB:
```bash
docker exec postgres-likes psql -U postgres -c "DROP DATABASE IF EXISTS flyway_test; CREATE DATABASE flyway_test;"
docker exec -i postgres-likes psql -U postgres -d flyway_test < like-service/src/main/resources/db/migration/V1__baseline.sql
docker exec postgres-likes psql -U postgres -d flyway_test -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';"
docker exec postgres-likes psql -U postgres -c "DROP DATABASE flyway_test;"
```
Expected: the baseline applies without error and creates the expected tables.

- [ ] **Step 2: Confirm no `ddl-auto: update` remains** anywhere:
```bash
grep -rn "ddl-auto" */src/main/resources/application*.yml
```
Expected: every line reads `ddl-auto: validate` (8 services). Zero `update`.

- [ ] **Step 3: Full-stack health check** — every DB service is up and healthy:
```bash
docker ps --format '{{.Names}} {{.Status}}' | grep -E "auth|user|event|tag|like|registration|notification|audit"
```
Expected: all Up.

- [ ] **Step 4: Commit any residual + finish.** (No code change expected here; this task is verification.)

---

## Self-Review Notes

- **Spec coverage:** ownership (owners run Flyway, co-tenants validate) → Tasks 1–6; baseline-from-dump → every task Step 2; rollout order (pilot → single-owners → shared last) → Task order; fresh-DB test + no-update sweep → Task 7. All spec sections mapped.
- **Shared DBs:** events_db owner=event (baseline incl. `tags`), tag validates (Task 5); users_db owner=auth (baseline incl. contacts/tags), user validates (Task 6). Co-tenants get `flyway.enabled=false` + validate.
- **Data safety:** `baseline-on-migrate` only stamps existing DBs; V1 never re-runs on them. The manual schema fixes from the ingestion work (source column, widened main_image_url, nullable requester_id) are already in events_db and captured by its dump.
- **Replicas:** auth and event run 2 replicas — verify both.
- **Rollback:** if a service won't validate, revert its application.yml to `ddl-auto: update` and rebuild to restore service while the baseline is fixed.
