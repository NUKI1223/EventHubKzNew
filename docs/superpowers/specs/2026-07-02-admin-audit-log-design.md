# Admin audit log + user deletion — design

Date: 2026-07-02. Status: approved by user.

## Goal

Before public deployment the admin panel needs (1) a permanent, filterable audit trail
of all business actions on the platform and (2) the ability to hard-delete users.
Audit records survive user deletion — including the record *about* the deletion itself.

Decisions made with the user:
- "Логи" = business-action audit trail (not application/stdout logs — those stay in
  Grafana/docker logs).
- Deletion = hard delete with full cascade; the deleted organizer's events are deleted too.
- An audit entry with a snapshot of the deleted user (username, email, who deleted, reason)
  is kept forever.
- Architecture = new `audit-service` consuming the existing Kafka bus (approach A),
  demonstrating the "new consumer without touching producers" property of the platform.

## 1. audit-service (new, port 8091)

Structural mirror of `registration-service`: Spring Boot 3.4, Eureka client,
Kafka consumers, REST controller, own database.

- **DB:** new `audit_db` on the existing `postgres-notifications` container
  (no new Postgres container; logical database-per-service isolation preserved).
- **Entity `AuditLog`:** `id` (bigserial), `occurredAt` (timestamp), `action` (varchar enum),
  `actorId`, `actorName`, `targetType` (`USER|EVENT|REQUEST`), `targetId`, `targetLabel`
  (event title / username), `details` (text, JSON-ish free form: reason, email,
  admin comment), `dedupKey` (`topic:partition:offset`, unique index), `createdAt`.
- **Idempotency:** insert skipped when `dedupKey` already exists (rebalance/redelivery safe).
- **Retention:** none — audit rows are never deleted or updated.

### Subscribed topics → actions

| Topic | Action(s) |
|---|---|
| `user.registered` | `USER_REGISTERED` |
| `user.deleted` (new) | `USER_DELETED` |
| `event.created` | `EVENT_CREATED` |
| `event.updated` | `EVENT_UPDATED` |
| `event.deleted` | `EVENT_DELETED` |
| `event.liked` | `EVENT_LIKED` |
| `event.registered` | `EVENT_RSVP` |
| `event-request.created` (new) | `REQUEST_CREATED` |
| `event-request.reviewed` | `REQUEST_APPROVED` / `REQUEST_REJECTED` |

## 2. New Kafka topics (DTOs in `eventhubkz-common`)

- `event-request.created` — published by event-service when an organizer submits a request.
  Payload: requestId, requesterId, requesterName, title.
- `user.deleted` — published by user-service after profile deletion.
  Payload: userId, username, email, deletedBy (admin id), reason (nullable).

## 3. User deletion cascade

Entry point: existing `DELETE /api/users/{id}` (user-service, self-or-admin guard
already in place). Extended behavior:

1. Guards: HTTP 400/403 when deleting yourself or another ADMIN.
2. Snapshot username/email before delete.
3. Delete profile row (`users` table is shared with auth-service → login dies immediately),
   JPA cascades clear `user_contacts` / `user_tags`.
4. Publish `user.deleted`.

Choreography — each service cleans its own data on `user.deleted`:

- **like-service:** delete user's likes, evict affected per-event count caches.
- **registration-service:** delete user's registrations, evict count caches.
- **notification-service:** delete user's notifications.
- **event-service:** delete events where `organizerId = userId` via the existing
  `deleteEvent` flow (each emits `event.deleted`, which already cleans the search index
  and other users' registrations); delete the user's event requests.
- **audit-service:** permanent `USER_DELETED` record with snapshot in `details`.

Known limitation (documented, accepted): the deleted user's JWT stays valid up to 1 h;
the gateway does not hit the DB per request. Profile is already gone, so mutating
calls fail on 404/FK.

## 4. Admin API

- `GET /api/admin/audit?page&size&action&actorId&targetType&from&to`
  → `Page<AuditLogDTO>`, newest first (audit-service). Gateway route with
  `AuthenticationFilter`; controller checks `X-User-Role == ADMIN` (HTTP 403 otherwise) —
  same pattern as existing admin endpoints.
- user-service: admin-only paged user list with search by username/email
  (`GET /api/users/admin/list?q&page&size`) for the deletion tab, added only if no
  suitable endpoint exists.

## 5. Frontend — two new AdminDashboard tabs

- **«Журнал» (audit):** table — time, actor, action as colored chip, target
  (link to event/profile when alive), details; filters — action select, user search,
  date range; pagination reusing the existing admin table patterns.
- **«Пользователи» (users):** search box, table (id, username, email, role, created);
  «Удалить» button → confirmation modal with optional "причина" field (flows into
  `user.deleted.reason` → audit details). The button is hidden for your own row and
  for ADMIN rows.
- All strings localized RU + KK (react-i18next), styling matches existing admin components.

## 6. Infrastructure

- docker-compose: `audit-service` (512m heap, depends on kafka/eureka/postgres-notifications),
  Prometheus scrape target, gateway routes.
- README: service table + mermaid diagram + Kafka topic table updated.

## 7. Testing

- audit-service: unit tests for topic→AuditLog mapping and dedup; Testcontainers IT
  for repository filters (existing `PostgresTestcontainer` pattern).
- user-service: unit tests — guards (self/ADMIN) and `user.deleted` publication.
- event-service: unit test for organizer-cascade deletion (mirrors existing service tests).
- like/registration/notification services: consumer unit tests for cleanup handlers.
- Manual E2E on the running stack: delete a seeded user, verify catalog/search/registrations
  cleaned and audit rows present; screenshot both tabs.

## Out of scope

- Application/stdout log viewer in the admin UI (Grafana/docker logs cover it).
- Login/failed-login auditing (high volume; revisit post-deploy if needed).
- Un-ban / restore — deletion is deliberately irreversible; soft-ban was considered
  and rejected by the user.
- Deleting other admins or self-service account deletion UI.
