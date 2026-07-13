# SET 3a — Flyway schema migrations (retire ddl-auto in prod)

Date: 2026-07-13. Status: draft for approval.

## Goal

Replace `spring.jpa.hibernate.ddl-auto: update` (currently on all 8 DB-backed services)
with **Flyway-managed, versioned migrations** + `ddl-auto: validate`. This removes the
single biggest prod risk: `ddl-auto:update` silently fails to evolve a populated schema
(this session alone hit it three times — couldn't add a NOT NULL column, couldn't widen
a column, couldn't drop a NOT NULL) and can drift/lose data. `validate` makes a schema
mismatch a hard boot failure instead of silent corruption.

## Confirmed topology (6 databases, 2 shared)

| Database | Instance | Services on it | Flyway owner |
|---|---|---|---|
| `users_db` | postgres-users | auth-service, user-service | **auth-service** |
| `events_db` | postgres-events | event-service, tag-service | **event-service** |
| `likes_db` | postgres-likes | like-service | like-service |
| `registrations_db` | postgres-registrations | registration-service | registration-service |
| `notifications_db` | postgres-notifications | notification-service | notification-service |
| `audit_db` | postgres-notifications | audit-service | audit-service |

Two databases are **shared**. A DB may have only ONE Flyway history, so exactly one service
per DB **owns** migrations; the co-tenant only validates.

## Ownership rule

- **Owner** (runs Flyway): `spring.flyway.enabled=true`, migrations in
  `classpath:db/migration`, `ddl-auto: validate`. Its `V1__baseline.sql` describes the
  **entire** database schema — including tables whose entities live in the co-tenant
  service (e.g. `event-service` owns `events_db`, so its baseline also contains the `tags`
  table even though the `Tag` entity lives in `tag-service`).
- **Co-tenant** (auth-service's partner `user-service`; event-service's partner
  `tag-service`): `spring.flyway.enabled=false`, `ddl-auto: validate`. It runs no
  migrations; it only asserts its own entities match the schema the owner created.

Owners: auth, event, like, registration, notification, audit (6). Co-tenants: user, tag (2).
Every one of the 8 services switches to `ddl-auto: validate`.

## Baseline generation (the correctness-critical part)

`validate` boots only if the schema exactly matches the JPA mappings. The safest baseline is
the **current running schema**, which Hibernate itself produced via `ddl-auto:update` (and
includes this session's manual fixes: `event_requests.source`, widened `main_image_url`,
nullable `requester_id`). For each of the 6 DBs:

```
docker exec <pg> pg_dump -U postgres -d <db> --schema-only --no-owner --no-privileges \
  --no-comments > <owner-service>/src/main/resources/db/migration/V1__baseline.sql
```

Then hand-clean each dump: drop `SET`/`SELECT pg_catalog...` noise and any
`flyway_schema_history` block; keep `CREATE TABLE`, sequences, PK/FK/unique constraints,
indexes. Result = a self-contained schema a fresh deploy can build.

## Flyway config per owner (example)

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true   # existing populated DBs are stamped at the baseline, V1 is NOT re-run
    baseline-version: 1
    locations: classpath:db/migration
  jpa:
    hibernate:
      ddl-auto: validate
```

- **Existing dev/prod DBs**: `baseline-on-migrate` marks them at V1 without running it (schema
  already exists) → later Vn migrations apply on top.
- **Fresh deploy**: Flyway runs `V1__baseline.sql` to build the schema from nothing.

Co-tenant services set `spring.flyway.enabled=false` + `ddl-auto: validate` only.

Dependency (Spring Boot 3.4 splits out the DB module): add to each owner (and it's harmless
on co-tenants) — `org.flywaydb:flyway-core` + `org.flywaydb:flyway-database-postgresql`.

## Rollout order (de-risked)

1. **Pilot: like-service** (simplest — single owner, small schema). Add dep, generate
   `V1__baseline.sql`, set validate + flyway config, rebuild, confirm it **boots and
   validates** against the existing populated `likes_db`. This proves the exact pattern.
2. **Roll out the other single-owner services**: registration, notification, audit.
3. **Shared DBs last** (highest risk): 
   - `events_db`: event-service (owner, full baseline incl. `tags`) + tag-service (validate).
     Both must boot; verify tag-service validates cleanly.
   - `users_db`: auth-service (owner, baseline = users + user_contacts + user_tags) +
     user-service (validate). Both must boot.
4. **Fresh-DB test**: create a throwaway DB, point a service at it, confirm Flyway builds
   the schema from V1 and the service boots.

## Testing / acceptance

- Every one of the 8 services **starts successfully** with `ddl-auto: validate` (a mapping
  mismatch = boot failure, caught immediately).
- The two co-tenant services (user, tag) validate against the owner-built schema.
- One fresh-DB run proves V1 builds a working schema from scratch.
- No `ddl-auto: update` remains anywhere.

## Out of scope (later)

- Forward migrations (V2+) — none needed yet; the mechanism is in place for the next schema
  change (e.g. the refresh-tokens table in SET 3b will ship as a Flyway `V2__refresh_tokens.sql`
  on `users_db`).
- The ingestion-service (Python) uses its own idempotent `create_schema` + `ALTER … IF NOT
  EXISTS` — out of scope here.

## Risks & mitigations

- **Hibernate `validate` strictness** (type/precision nitpicks): baseline is generated from
  the real Hibernate-built schema, so it matches by construction. Any residual mismatch shows
  as a clear boot error naming the column — fix the baseline and retry.
- **Shared-DB co-tenant drift**: both services are booted in the pilot for that DB before
  moving on.
- **Existing data**: `baseline-on-migrate` never touches existing tables; it only records the
  baseline version. Zero data risk on the running stack.

## Execution

Via subagent-driven-development: one task per service/group (pilot → single-owners →
shared-DB pairs), each rebuilt and boot-verified before the next. Branch: new
`feat/flyway-migrations` off master (after the tested ingestion branch is merged).
