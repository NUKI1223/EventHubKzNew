# Event Staff & QR Check-in — Design

**Date:** 2026-06-19
**Status:** Approved (brainstorming complete)
**Feature branch (suggested):** `feature/event-staff-checkin`

## Summary

Let an event organizer add **workers (staff / "доверенные лица")** to their event.
Staff get the same attendee-facing powers as the organizer — check-in and viewing
attendee answers — but cannot edit the event. The ticket QR becomes a **deep link**
so any phone's native camera can scan it and mark a participant as attended. A user's
profile publicly shows the upcoming events they help run.

This builds directly on the just-shipped native registration + custom questions + QR
tickets. It completes the registration loop (register → ticket → check-in) and
distributes the check-in workload beyond a single organizer.

## Goals

- Organizer can add/remove staff on their event by username.
- Staff can: check in attendees (QR or manual code) and see the attendee list,
  their custom-question answers, and the Excel export.
- Staff **cannot**: edit/delete the event or manage the staff list.
- Check-in works from any phone's **native camera** via a deep-link QR — no app,
  no special scanner. Used by staff and organizer alike.
- A user's profile publicly lists the **upcoming/active** events they staff.

## Non-goals (YAGNI)

- No in-app camera scanner page / QR-decode dependency (`html5-qrcode` etc.).
  Native camera + deep link covers it; manual code entry is the no-camera fallback.
- No per-staff granular permissions — one "staff" level for everyone added.
- No staff for past events on the profile (only upcoming/active).
- No invitations/acceptance flow — adding a user makes them staff immediately.
- No co-organizer event editing.

## Architecture decision

**Storage: `staffIds` as `@ElementCollection<Long>` on `Event`** (join table
`event_staff(event_id, user_id)`), mirroring the existing tag pattern.

Rationale:
- The set folds into the shared `EventDTO`, so **registration-service authorizes
  check-in with a trivial `staffIds.contains(requesterId)`** — no extra Feign call.
- "Events I help with" is one JPQL query:
  `select e from Event e join e.staffIds s where s = :uid and e.eventDate > :now`.
- Idiomatic to the codebase; minimal new surface vs. a dedicated `EventStaff`
  entity or a `jsonb` list (which would need native containment queries).

## Data model

- **`Event`** (event-service, `events_db`): add
  `@ElementCollection @CollectionTable(name = "event_staff", joinColumns = @JoinColumn(name = "event_id")) @Column(name = "user_id") private Set<Long> staffIds = new HashSet<>();`
- **`EventDTO`** (eventhubkz-common): add `private Set<Long> staffIds;`
  - Populated in `EventService.toDTO`.
  - Carried through so the frontend can compute `canManage` and registration-service
    can authorize.
- `EventRequest` is **not** touched — staff are added to a live event after approval,
  not requested up front.

## Authorization model

Define a single notion: **"can manage the attendee surface of event E"** =
`role == ADMIN || E.organizerId == me || E.staffIds.contains(me)`.

Applied at:
- registration-service `checkIn(code, requesterId, role)` — currently organizer/admin;
  add staff. It already fetches `EventDTO`; check `staffIds`.
- registration-service `getAttendeeAnswers(eventId, requesterId, role)` — same change.
- event-service `getAttendees(eventId, requesterId, role)` — same change (it proxies
  registration-service; keep both consistent).

**Unchanged (organizer/admin only):** event create/update/delete, and the staff
management endpoints below. Staff explicitly cannot edit the event or manage staff.

## API (event-service)

Staff management — **organizer of the event or admin only**:
- `POST /api/events/{id}/staff` — body `{ "userId": <Long> }`. Adds to `staffIds`
  (idempotent). 403 if requester is not organizer/admin. 404 if event missing.
  (The frontend resolves a typed username → userId via the existing
  `GET /api/users/username/{username}` before calling this.)
- `DELETE /api/events/{id}/staff/{userId}` — removes from `staffIds`. Same authz.
- `GET /api/events/{id}/staff` — returns the staff users (id/username/avatar),
  visible to anyone who can manage the attendee surface (organizer/staff/admin).

Profile badge — **public**:
- `GET /api/events/staffed-by/{userId}?upcoming=true` — minimal event cards
  (id, title, eventDate) where the user is staff and `eventDate > now`. No auth
  required (mirrors other public read endpoints).

Gateway: these live under `/api/events/**`, already routed; the new
`/api/events/*/staff` and `/api/events/staffed-by/*` paths need to be reachable
through the existing event-service routes (verify predicates — the `event-attendees`
route uses `/api/events/*/attendees`, so add analogous auth-required entries for the
write/staff paths and a public entry for `staffed-by`).

## Check-in flow

**Ticket QR becomes a deep link.** `EventTicket.jsx` encodes
`${window.location.origin}/checkin/${registration.code}` instead of the JSON blob.
The human-readable code stays printed on the ticket as a manual fallback.

**Single route `/checkin/:code` → `CheckinPage`:**
- Behind `PrivateRoute`. If not logged in, redirect to sign-in and return here after.
- On mount, `POST /api/registrations/checkin { code }` (the existing endpoint).
- The endpoint authorizes the **scanner** (organizer/staff/admin), so a random
  person who opens the link gets a clean 403.
- Renders a large result card:
  - 200 + REGISTERED→ATTENDED: "✓ «Имя» отмечен".
  - 200 already ATTENDED: "Уже отмечен ранее" (idempotent, no error).
  - 403: "У вас нет прав отмечать участников этого мероприятия".
  - 404: "Код не найден".
- Includes a hint to scan the next ticket (just re-point the camera → new link).

**Manual fallback unchanged:** the existing code-entry box in `EventRegistrants`
stays for no-camera situations; it now also works for staff (authz extended).

Both paths hit the one `/checkin` endpoint → single authorization + idempotency path.
Re-scanning an already-checked-in ticket is a no-op (already handled in `checkIn`).

## Frontend

- **`EventTicket.jsx`** — change QR `value` to the deep link; keep printed code.
- **`CheckinPage.jsx`** (new) + route `/checkin/:code` — described above. Needs a
  small CSS file for the result card.
- **`EventRegistrants.jsx`** (manage view):
  - `canManage = role === 'ADMIN' || organizerId == myId || staffIds.includes(myId)`
    (replaces the current organizer/admin-only `isManager`). Drives access to the
    attendee table, answers, Excel, and manual check-in.
  - New **"Сотрудники мероприятия"** panel — visible to the **organizer/admin only**
    (not staff): username input → resolve → add; list staff (avatar + name) with a
    remove (×). Uses `POST/DELETE /api/events/{id}/staff` and `GET .../staff`.
- **`Profile.jsx`** — new **"Помогает на мероприятиях"** block: fetch
  `GET /api/events/staffed-by/{userId}?upcoming=true`, render event links, hide if
  empty.

## Error handling

- Add staff: username not found → frontend toast "Пользователь не найден" (from the
  `/api/users/username` 404) before calling the staff endpoint. Adding the organizer
  themselves or an existing staff → idempotent no-op (no error).
- `CheckinPage`: distinct messages per status (200/already/403/404) as above; network
  error → generic retry message.
- Authz failures everywhere return 403 with a clear message; the frontend never shows
  manage controls it can't use (gated on `canManage` / organizer).

## Testing

- **registration-service** (unit, follows existing `AnswerValidatorTest` style):
  `checkIn` and `getAttendeeAnswers` authorize a **staff** member (allowed),
  reject a **stranger** (403), allow organizer and admin. Idempotent re-check-in.
- **E2E through the gateway** (extends the existing Python scripts):
  organizer adds staff → staff can `GET /attendees` (200 with answers) and
  `POST /checkin` (200) → stranger is 403 → remove staff → that user is now 403 →
  `GET /api/events/staffed-by/{staffId}?upcoming=true` lists the event →
  ticket QR value is the `/checkin/<code>` deep link.
- **Frontend**: `vite build` clean; visual check of CheckinPage result states, the
  staff panel (add/remove), and the profile badge.

## Rollout / compatibility

- `ddl-auto=update` creates `event_staff`; existing events get an empty staff set.
- Old tickets encode JSON; only newly rendered tickets use the deep link. The manual
  code path still works for any ticket, so no migration of issued tickets is needed.
- Default behavior is unchanged for events with no staff.
