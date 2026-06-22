# Event Staff & QR Check-in Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an organizer add event staff who can check in attendees (via a deep-link QR scanned by any phone camera) and see attendee answers, with a public profile badge for events a user helps run.

**Architecture:** Staff are stored as `@ElementCollection<Long> staffIds` on `Event` (join table `event_staff`), mirroring the existing tag pattern. The set folds into the shared `EventDTO`, so registration-service authorizes check-in with a `staffIds.contains(me)` check — no extra Feign call. The ticket QR becomes a `/checkin/:code` deep link handled by one auth-gated React page that calls the existing `/checkin` endpoint.

**Tech Stack:** Spring Boot 3.4.2 / Java 21 / JPA / PostgreSQL (event-service, registration-service), Spring Cloud Gateway, React 19 + Vite, qrcode.react, axios.

## Global Constraints

- **No Claude / Co-Authored-By / Anthropic attribution** in any commit message — commits are the user's alone.
- **`.env` must never be staged or committed** (holds secrets). Stage only the files each step names.
- `spring.jpa.hibernate.ddl-auto=update` auto-creates `event_staff`; existing events get an empty staff set — no manual migration.
- `staffIds` uses `@ElementCollection(fetch = FetchType.EAGER)`, exactly like `Event.tags`, so it loads with the event for `toDTO` and authorization.
- **Authorization rules:** attendee surface (check-in, attendee answers, attendee list) = `ADMIN || organizerId == requester || staffIds.contains(requester)`. Event create/update/delete and staff add/remove = `ADMIN || organizerId == requester` (staff explicitly cannot edit the event or manage staff).
- **No gateway changes:** staff add/remove are POST/DELETE under `/api/events/**` (route `event-service-write`, already injects `X-User-Id`/`X-User-Role`); `GET /api/events/staffed-by/{id}` is public (route `event-service-read`).
- Gateway base URL `http://localhost:8180`; auth login is `POST /auth/login` (rewritten to `/api/auth/login`). All seed users' password is `password123`. `dinara.zhumabaeva@example.kz` and `admin@eventhub.kz` are ADMIN; `aidar.kasenov@example.kz`, `alisher.zhakupov@example.kz` are regular USERs.
- Backend code changes require an image rebuild to take effect: `./start.sh --build --no-front`.

---

### Task 1: `staffIds` on Event, EventDTO, toDTO, and repository query

**Files:**
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/model/Event.java`
- Modify: `eventhubkz-common/src/main/java/org/ngcvfb/eventhubkz/common/dto/EventDTO.java`
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/service/EventService.java` (`toDTO`, ~line 304)
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/repository/EventRepository.java`
- Test: `event-service/src/test/java/org/ngcvfb/eventservice/repository/EventStaffRepositoryIT.java`

**Interfaces:**
- Produces: `Event.getStaffIds() : Set<Long>` (never null — initialized to empty set); `EventDTO.getStaffIds()/setStaffIds(Set<Long>)`; `EventRepository.findUpcomingStaffedBy(Long userId, LocalDateTime now) : List<Event>`.

- [ ] **Step 1: Write the failing repository IT**

Create `event-service/src/test/java/org/ngcvfb/eventservice/repository/EventStaffRepositoryIT.java`:

```java
package org.ngcvfb.eventservice.repository;

import org.junit.jupiter.api.Test;
import org.ngcvfb.eventservice.PostgresTestcontainer;
import org.ngcvfb.eventservice.model.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Same setup as EventRequestRepositoryIT: PostgresTestcontainer is an
// ApplicationContextInitializer, NOT a base class — wire it via initializers.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class EventStaffRepositoryIT {

    @Autowired
    EventRepository repository;

    private Event newEvent(String title, LocalDateTime date, Set<Long> staff) {
        Event e = Event.builder()
                .title(title).shortDescription("short").fullDescription("full description here")
                .location("Almaty").online(false).eventDate(date)
                .organizerId(1L).organizerEmail("org@example.kz")
                .staffIds(staff)
                .build();
        return repository.save(e);
    }

    @Test
    void findUpcomingStaffedBy_returnsOnlyFutureEventsWhereUserIsStaff() {
        LocalDateTime now = LocalDateTime.now();
        newEvent("future-staffed", now.plusDays(5), Set.of(7L));
        newEvent("past-staffed", now.minusDays(5), Set.of(7L));
        newEvent("future-other-staff", now.plusDays(5), Set.of(9L));

        List<Event> result = repository.findUpcomingStaffedBy(7L, now);

        assertThat(result).extracting(Event::getTitle).containsExactly("future-staffed");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl event-service -Dtest=EventStaffRepositoryIT test`
Expected: FAIL — compilation error (`staffIds` builder method and `findUpcomingStaffedBy` do not exist yet).

- [ ] **Step 3: Add `staffIds` to the Event entity**

In `Event.java`, after the `tags` field block (around line 38), add:

```java
    // Сотрудники мероприятия ("доверенные лица"): могут отмечать приход и видеть
    // ответы участников, но не редактируют событие. EAGER — как tags, чтобы
    // подгружались для toDTO и проверки прав.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_staff", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "user_id")
    private Set<Long> staffIds = new HashSet<>();
```

(`Set`, `HashSet`, and the persistence annotations are already imported.)

- [ ] **Step 4: Add `staffIds` to EventDTO**

In `EventDTO.java`, after the `tags` field (line 28), add:

```java
    private Set<Long> staffIds;
```

(`Set` is already imported.)

- [ ] **Step 5: Populate `staffIds` in `toDTO`**

In `EventService.java` `toDTO`, after `dto.setTags(...)` (line 310), add:

```java
        dto.setStaffIds(event.getStaffIds() == null ? java.util.Set.of() : new HashSet<>(event.getStaffIds()));
```

- [ ] **Step 6: Add the repository query**

In `EventRepository.java`, after `findByTag` (uses the same `MEMBER OF` idiom), add:

```java
    @Query("SELECT e FROM Event e WHERE :userId MEMBER OF e.staffIds AND e.eventDate > :now ORDER BY e.eventDate ASC")
    List<Event> findUpcomingStaffedBy(@Param("userId") Long userId, @Param("now") LocalDateTime now);
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn -q -pl event-service -Dtest=EventStaffRepositoryIT test`
Expected: PASS (1 test).

- [ ] **Step 8: Commit**

```bash
git add event-service/src/main/java/org/ngcvfb/eventservice/model/Event.java \
        eventhubkz-common/src/main/java/org/ngcvfb/eventhubkz/common/dto/EventDTO.java \
        event-service/src/main/java/org/ngcvfb/eventservice/service/EventService.java \
        event-service/src/main/java/org/ngcvfb/eventservice/repository/EventRepository.java \
        event-service/src/test/java/org/ngcvfb/eventservice/repository/EventStaffRepositoryIT.java
git commit -m "feat(events): add staffIds to Event and EventDTO with upcoming-staffed query"
```

---

### Task 2: Staff management service methods + endpoints

**Files:**
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/service/EventService.java`
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/controller/EventController.java`
- Test: `event-service/src/test/java/org/ngcvfb/eventservice/service/EventServiceStaffTest.java`

**Interfaces:**
- Consumes: `EventRepository.findUpcomingStaffedBy` (Task 1).
- Produces: `EventService.addStaff(Long eventId, Long userId, Long requesterId, String role)`; `EventService.removeStaff(Long eventId, Long userId, Long requesterId, String role)`; `EventService.getStaffedBy(Long userId) : List<EventDTO>`. Controller routes `POST /api/events/{id}/staff`, `DELETE /api/events/{id}/staff/{userId}`, `GET /api/events/staffed-by/{userId}`.

- [ ] **Step 1: Write the failing service test**

Create `event-service/src/test/java/org/ngcvfb/eventservice/service/EventServiceStaffTest.java`:

```java
package org.ngcvfb.eventservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.eventservice.client.RegistrationClient;
import org.ngcvfb.eventservice.client.UserClient;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.Event;
import org.ngcvfb.eventservice.repository.EventRepository;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceStaffTest {

    // EventService constructor fields: eventRepository, kafkaProducer,
    // registrationClient, userClient (no LikeClient). The test only drives the repo.
    @Mock EventRepository eventRepository;
    @Mock EventKafkaProducer kafkaProducer;
    @Mock RegistrationClient registrationClient;
    @Mock UserClient userClient;

    @InjectMocks EventService eventService;

    private Event eventOwnedBy(long organizerId) {
        Event e = Event.builder().id(1L).title("t").organizerId(organizerId)
                .staffIds(new HashSet<>()).build();
        lenient().when(eventRepository.findById(1L)).thenReturn(Optional.of(e));
        lenient().when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        return e;
    }

    @Test
    void addStaff_byOrganizer_addsUser() {
        Event e = eventOwnedBy(10L);
        eventService.addStaff(1L, 7L, 10L, "USER");
        assertThat(e.getStaffIds()).contains(7L);
    }

    @Test
    void addStaff_byAdmin_addsUser() {
        Event e = eventOwnedBy(10L);
        eventService.addStaff(1L, 7L, 999L, "ADMIN");
        assertThat(e.getStaffIds()).contains(7L);
    }

    @Test
    void addStaff_byStranger_isForbidden() {
        eventOwnedBy(10L);
        assertThatThrownBy(() -> eventService.addStaff(1L, 7L, 55L, "USER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void removeStaff_byOrganizer_removesUser() {
        Event e = eventOwnedBy(10L);
        e.getStaffIds().add(7L);
        eventService.removeStaff(1L, 7L, 10L, "USER");
        assertThat(e.getStaffIds()).doesNotContain(7L);
    }
}
```

If the `EventService` constructor in this repo has a different dependency list, mirror its exact fields as `@Mock`s (the test only exercises `eventRepository`). Verify the field list with: `grep -n "private final" event-service/src/main/java/org/ngcvfb/eventservice/service/EventService.java`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl event-service,eventhubkz-common -am -Dtest=EventServiceStaffTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL — `addStaff`/`removeStaff` do not exist (compilation error).

- [ ] **Step 3: Add the authz helper and staff methods to EventService**

In `EventService.java`, add a private helper near the existing authz check in `getAttendees`, and three methods (place after `getAttendees`):

```java
    private void requireOrganizerOrAdmin(Event event, Long requesterId, String role) {
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        if (!isAdmin && !Objects.equals(event.getOrganizerId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Управлять сотрудниками может только организатор мероприятия");
        }
    }

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public void addStaff(Long eventId, Long userId, Long requesterId, String role) {
        Event event = findEventOrThrow(eventId);
        requireOrganizerOrAdmin(event, requesterId, role);
        if (event.getStaffIds() == null) {
            event.setStaffIds(new HashSet<>());
        }
        event.getStaffIds().add(userId);
        eventRepository.save(event);
        log.info("Added staff {} to event {} by {}", userId, eventId, requesterId);
    }

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public void removeStaff(Long eventId, Long userId, Long requesterId, String role) {
        Event event = findEventOrThrow(eventId);
        requireOrganizerOrAdmin(event, requesterId, role);
        if (event.getStaffIds() != null) {
            event.getStaffIds().remove(userId);
            eventRepository.save(event);
        }
        log.info("Removed staff {} from event {} by {}", userId, eventId, requesterId);
    }

    public List<EventDTO> getStaffedBy(Long userId) {
        return eventRepository.findUpcomingStaffedBy(userId, LocalDateTime.now())
                .stream().map(this::toDTO).toList();
    }
```

Confirm imports exist (`Objects`, `HttpStatus`, `ResponseStatusException`, `LocalDateTime`, `HashSet`, `CacheEvict`, `Transactional` are already used in this file).

- [ ] **Step 4: Add the controller endpoints**

In `EventController.java`, after `getAttendees` (line 70), add:

```java
    @PostMapping("/{id}/staff")
    public ResponseEntity<Void> addStaff(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Long> body,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        eventService.addStaff(id, body.get("userId"), userId, role);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/staff/{staffUserId}")
    public ResponseEntity<Void> removeStaff(
            @PathVariable Long id,
            @PathVariable Long staffUserId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        eventService.removeStaff(id, staffUserId, userId, role);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/staffed-by/{userId}")
    public ResponseEntity<List<EventDTO>> getStaffedBy(@PathVariable Long userId) {
        return ResponseEntity.ok(eventService.getStaffedBy(userId));
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -pl event-service,eventhubkz-common -am -Dtest=EventServiceStaffTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add event-service/src/main/java/org/ngcvfb/eventservice/service/EventService.java \
        event-service/src/main/java/org/ngcvfb/eventservice/controller/EventController.java \
        event-service/src/test/java/org/ngcvfb/eventservice/service/EventServiceStaffTest.java
git commit -m "feat(events): staff add/remove endpoints and staffed-by query"
```

---

### Task 3: Extend attendee-surface authorization to staff

**Files:**
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/service/EventService.java` (`getAttendees`, ~line 211)
- Modify: `registration-service/src/main/java/org/ngcvfb/registrationservice/service/EventRegistrationService.java` (`checkIn` ~line 152, `getAttendeeAnswers` ~line 75)
- Test: `registration-service/src/test/java/org/ngcvfb/registrationservice/service/EventRegistrationAuthTest.java`

**Interfaces:**
- Consumes: `EventDTO.getStaffIds()` (Task 1).
- Produces: `checkIn` and `getAttendeeAnswers` allow a staff member; `getAttendees` (event-service) allows a staff member.

- [ ] **Step 1: Write the failing registration-service authz test**

Create `registration-service/src/test/java/org/ngcvfb/registrationservice/service/EventRegistrationAuthTest.java`:

```java
package org.ngcvfb.registrationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.eventhubkz.common.dto.EventDTO;
import org.ngcvfb.registrationservice.client.EventClient;
import org.ngcvfb.registrationservice.kafka.RegistrationKafkaProducer;
import org.ngcvfb.registrationservice.model.EventRegistration;
import org.ngcvfb.registrationservice.model.RegistrationStatus;
import org.ngcvfb.registrationservice.repository.EventRegistrationRepository;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventRegistrationAuthTest {

    @Mock EventRegistrationRepository registrationRepository;
    @Mock RegistrationKafkaProducer kafkaProducer;
    @Mock EventClient eventClient;
    @Mock AnswerValidator answerValidator;

    @InjectMocks EventRegistrationService service;

    private EventDTO eventWith(long organizerId, Set<Long> staff) {
        EventDTO dto = new EventDTO();
        dto.setId(1L);
        dto.setOrganizerId(organizerId);
        dto.setStaffIds(staff);
        lenient().when(eventClient.getEventById(1L)).thenReturn(dto);
        return dto;
    }

    @Test
    void checkIn_byStaff_isAllowed() {
        eventWith(10L, Set.of(7L));
        EventRegistration reg = EventRegistration.builder()
                .userId(3L).eventId(1L).status(RegistrationStatus.REGISTERED).code("ABC123").build();
        when(registrationRepository.findByCode("ABC123")).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(EventRegistration.class))).thenAnswer(i -> i.getArgument(0));

        EventRegistration result = service.checkIn("ABC123", 7L, "USER");

        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.ATTENDED);
    }

    @Test
    void checkIn_byStranger_isForbidden() {
        eventWith(10L, Set.of(7L));
        EventRegistration reg = EventRegistration.builder()
                .userId(3L).eventId(1L).status(RegistrationStatus.REGISTERED).code("ABC123").build();
        when(registrationRepository.findByCode("ABC123")).thenReturn(Optional.of(reg));

        assertThatThrownBy(() -> service.checkIn("ABC123", 55L, "USER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void getAttendeeAnswers_byStaff_isAllowed() {
        eventWith(10L, Set.of(7L));
        lenient().when(registrationRepository.findByEventId(1L)).thenReturn(List.of());

        assertThat(service.getAttendeeAnswers(1L, 7L, "USER")).isEmpty();
    }

    @Test
    void getAttendeeAnswers_byStranger_isForbidden() {
        eventWith(10L, Set.of(7L));
        assertThatThrownBy(() -> service.getAttendeeAnswers(1L, 55L, "USER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }
}
```

Verify the `EventRegistrationService` constructor field list first: `grep -n "private final" registration-service/src/main/java/org/ngcvfb/registrationservice/service/EventRegistrationService.java`. Add a `@Mock` for any field not listed above (the test only drives repo + eventClient).

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl registration-service,eventhubkz-common -am -Dtest=EventRegistrationAuthTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL — `checkIn`/`getAttendeeAnswers` reject staff (403 thrown for staff id 7).

- [ ] **Step 3: Allow staff in registration-service `checkIn`**

In `EventRegistrationService.java` `checkIn`, replace the authorization block:

```java
        EventDTO event = fetchEvent(reg.getEventId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        if (!isAdmin && !Objects.equals(event.getOrganizerId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Отмечать участников может только организатор мероприятия");
        }
```

with:

```java
        EventDTO event = fetchEvent(reg.getEventId());
        if (!canManageAttendees(event, requesterId, role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Отмечать участников может только организатор или сотрудник мероприятия");
        }
```

- [ ] **Step 4: Allow staff in registration-service `getAttendeeAnswers` and add the helper**

In the same file, replace the authz block in `getAttendeeAnswers`:

```java
        EventDTO event = fetchEvent(eventId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        if (!isAdmin && !Objects.equals(event.getOrganizerId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ответы участников доступны только организатору мероприятия");
        }
```

with:

```java
        EventDTO event = fetchEvent(eventId);
        if (!canManageAttendees(event, requesterId, role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ответы участников доступны только организатору или сотруднику мероприятия");
        }
```

Then add this private helper to the class:

```java
    private boolean canManageAttendees(EventDTO event, Long requesterId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) return true;
        if (Objects.equals(event.getOrganizerId(), requesterId)) return true;
        return event.getStaffIds() != null && event.getStaffIds().contains(requesterId);
    }
```

- [ ] **Step 5: Allow staff in event-service `getAttendees`**

In `EventService.java` `getAttendees` (~line 213), replace:

```java
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        if (!isAdmin && !Objects.equals(event.getOrganizerId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Список участников доступен только организатору мероприятия");
        }
```

with:

```java
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        boolean isOrganizer = Objects.equals(event.getOrganizerId(), requesterId);
        boolean isStaff = event.getStaffIds() != null && event.getStaffIds().contains(requesterId);
        if (!isAdmin && !isOrganizer && !isStaff) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Список участников доступен только организатору или сотруднику мероприятия");
        }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -pl registration-service,eventhubkz-common -am -Dtest=EventRegistrationAuthTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS (4 tests).

- [ ] **Step 7: Compile event-service to confirm the getAttendees change builds**

Run: `mvn -q -pl event-service,eventhubkz-common -am compile`
Expected: no output (success).

- [ ] **Step 8: Commit**

```bash
git add event-service/src/main/java/org/ngcvfb/eventservice/service/EventService.java \
        registration-service/src/main/java/org/ngcvfb/registrationservice/service/EventRegistrationService.java \
        registration-service/src/test/java/org/ngcvfb/registrationservice/service/EventRegistrationAuthTest.java
git commit -m "feat(registration): allow event staff to check in and view attendee answers"
```

---

### Task 4: Ticket QR becomes a check-in deep link

**Files:**
- Modify: `frontend/src/components/EventTicket.jsx` (lines 15-23, the `payload`)

**Interfaces:**
- Produces: the ticket QR now encodes `${origin}/checkin/${code}`.

- [ ] **Step 1: Change the QR payload to a deep link**

In `EventTicket.jsx`, replace the `payload` JSON block (lines 15-23):

```js
  const payload = JSON.stringify({
    type: 'eventhub-ticket',
    code: registration.code,
    eventId: event?.id,
    eventTitle: event?.title,
    name: username || '',
    email: email || '',
    registeredAt: registration.createdAt,
  });
```

with:

```js
  // QR кодирует ссылку отметки прихода: организатор/сотрудник наводит обычную
  // камеру телефона → открывается /checkin/<код> → приход отмечается.
  const payload = `${window.location.origin}/checkin/${registration.code}`;
```

- [ ] **Step 2: Build the frontend to verify it compiles**

Run: `cd frontend && npm run build`
Expected: `✓ built` with no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/EventTicket.jsx
git commit -m "feat(frontend): encode ticket QR as a check-in deep link"
```

---

### Task 5: CheckinPage and route

**Files:**
- Create: `frontend/src/components/CheckinPage.jsx`
- Create: `frontend/src/css/CheckinPage.css`
- Modify: `frontend/src/App.jsx` (import + route, lines 1-22 and ~39)

**Interfaces:**
- Consumes: `POST /api/registrations/checkin { code }` (existing); `PrivateRoute` (existing).
- Produces: route `/checkin/:code` rendering check-in result.

- [ ] **Step 1: Create the CheckinPage component**

Create `frontend/src/components/CheckinPage.jsx`:

```jsx
import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import api from '../api';
import '../css/CheckinPage.css';

const CheckinPage = () => {
  const { code } = useParams();
  const [state, setState] = useState({ status: 'loading', message: '', reg: null });

  useEffect(() => {
    let cancelled = false;
    api.post('/api/registrations/checkin', { code })
      .then(res => {
        if (cancelled) return;
        const already = res.data?.status === 'ATTENDED';
        setState({ status: 'ok', reg: res.data, message: already ? 'already' : 'done' });
      })
      .catch(err => {
        if (cancelled) return;
        const s = err?.response?.status;
        const message = s === 403 ? 'У вас нет прав отмечать участников этого мероприятия'
          : s === 404 ? 'Код не найден'
          : 'Не удалось отметить участника. Попробуйте ещё раз.';
        setState({ status: 'error', message, reg: null });
      });
    return () => { cancelled = true; };
  }, [code]);

  return (
    <div className="checkin">
      <div className={`checkin__card checkin__card--${state.status}`}>
        {state.status === 'loading' && <p className="checkin__msg">Отмечаем…</p>}
        {state.status === 'ok' && (
          <>
            <div className="checkin__icon">✓</div>
            <p className="checkin__msg">
              {state.message === 'already' ? 'Участник уже был отмечен ранее' : 'Приход отмечен'}
            </p>
            <p className="checkin__sub">ID участника: {state.reg?.userId} · код {code}</p>
          </>
        )}
        {state.status === 'error' && (
          <>
            <div className="checkin__icon checkin__icon--err">✕</div>
            <p className="checkin__msg">{state.message}</p>
          </>
        )}
        <Link to="/" className="checkin__home">На главную</Link>
        <p className="checkin__hint">Чтобы отметить следующего — наведите камеру на его QR.</p>
      </div>
    </div>
  );
};

export default CheckinPage;
```

- [ ] **Step 2: Create the CSS**

Create `frontend/src/css/CheckinPage.css`:

```css
.checkin {
  min-height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.checkin__card {
  width: 100%;
  max-width: 420px;
  text-align: center;
  padding: 32px 24px;
  border-radius: var(--r-md, 16px);
  border: 1.5px solid var(--line, #e5e7eb);
  background: var(--surface, #fff);
}
.checkin__card--ok { border-color: #10b981; }
.checkin__card--error { border-color: #ef4444; }
.checkin__icon {
  width: 64px; height: 64px; margin: 0 auto 16px;
  border-radius: 50%; display: flex; align-items: center; justify-content: center;
  font-size: 34px; color: #fff; background: #10b981;
}
.checkin__icon--err { background: #ef4444; }
.checkin__msg { font-size: 18px; font-weight: 700; margin: 0 0 6px; color: var(--ink-900, #111827); }
.checkin__sub { font-size: 13px; color: var(--ink-500, #6b7280); margin: 0 0 16px; }
.checkin__home {
  display: inline-block; margin-top: 8px; padding: 10px 18px;
  border-radius: var(--r-sm, 10px); background: var(--ink-900, #111827); color: #fff;
  text-decoration: none; font-weight: 600; font-size: 14px;
}
.checkin__hint { margin-top: 14px; font-size: 12px; color: var(--ink-400, #9ca3af); }
```

- [ ] **Step 3: Register the route**

In `App.jsx`: add imports near the other component imports (after line 11):

```jsx
import CheckinPage from './components/CheckinPage';
import PrivateRoute from './components/PrivateRoute';
```

Add the route inside `<Routes>` (after the `/events/:id/registrants` route, line 39):

```jsx
        <Route path="/checkin/:code" element={<PrivateRoute><CheckinPage /></PrivateRoute>} />
```

- [ ] **Step 4: Build to verify it compiles**

Run: `cd frontend && npm run build`
Expected: `✓ built` with no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/CheckinPage.jsx frontend/src/css/CheckinPage.css frontend/src/App.jsx
git commit -m "feat(frontend): /checkin/:code page for QR deep-link attendance"
```

---

### Task 6: EventRegistrants — staff access + staff management panel

**Files:**
- Modify: `frontend/src/components/EventRegistrants.jsx`
- Modify: `frontend/src/css/EventLikers.css` (append panel styles)

**Interfaces:**
- Consumes: `event.staffIds` (from `GET /api/events/{id}`, Task 1); `GET /api/users/username/{name}`; `GET /api/users/batch?ids=`; `POST /api/events/{id}/staff`; `DELETE /api/events/{id}/staff/{userId}` (Task 2).
- Produces: organizer/admin manage staff; staff (and organizer/admin) see the attendee table.

- [ ] **Step 1: Widen `isManager` to include staff**

In `EventRegistrants.jsx`, replace the manager computation (line ~53):

```js
        const manager = role === 'ADMIN' || (myId != null && String(evRes.data.organizerId) === String(myId));
        setIsManager(manager);
```

with:

```js
        const staffIds = Array.isArray(evRes.data.staffIds) ? evRes.data.staffIds : [];
        const isOrganizer = myId != null && String(evRes.data.organizerId) === String(myId);
        const isStaff = myId != null && staffIds.map(String).includes(String(myId));
        const manager = role === 'ADMIN' || isOrganizer || isStaff;
        setIsManager(manager);
        setIsOwner(role === 'ADMIN' || isOrganizer);
```

- [ ] **Step 2: Add owner + staff-panel state**

Near the other `useState` declarations (after line 26), add:

```js
  const [isOwner, setIsOwner] = useState(false);
  const [staff, setStaff] = useState([]);
  const [staffInput, setStaffInput] = useState('');
  const [staffBusy, setStaffBusy] = useState(false);
```

- [ ] **Step 3: Add a loader for the staff user list and the add/remove handlers**

Add inside the component (after `fetchAttendees`, before the `useEffect`):

```js
  const loadStaff = useCallback(async (ev) => {
    const ids = Array.isArray(ev?.staffIds) ? ev.staffIds : [];
    if (ids.length === 0) { setStaff([]); return; }
    try {
      const res = await api.get('/api/users/batch', { params: { ids: ids.join(',') } });
      setStaff(Array.isArray(res.data) ? res.data : []);
    } catch { setStaff([]); }
  }, []);

  const addStaff = async (e) => {
    e.preventDefault();
    const name = staffInput.trim();
    if (!name) return;
    setStaffBusy(true);
    try {
      const u = await api.get(`/api/users/username/${encodeURIComponent(name)}`);
      const userId = u.data?.id;
      if (!userId) { toast.error('Пользователь не найден'); return; }
      await api.post(`/api/events/${eventId}/staff`, { userId });
      setStaffInput('');
      setStaff(prev => prev.some(s => s.id === userId) ? prev : [...prev, u.data]);
      toast.success(`${u.data.username} добавлен в сотрудники`);
    } catch (err) {
      toast.error(err?.response?.status === 404 ? 'Пользователь не найден' : 'Не удалось добавить');
    } finally {
      setStaffBusy(false);
    }
  };

  const removeStaff = async (userId) => {
    try {
      await api.delete(`/api/events/${eventId}/staff/${userId}`);
      setStaff(prev => prev.filter(s => s.id !== userId));
    } catch { toast.error('Не удалось убрать сотрудника'); }
  };
```

- [ ] **Step 4: Load staff when the event is the owner's**

In the `load` effect, after `setIsManager(manager)` resolves (just after `await fetchAttendees()` inside the `if (manager)` block), add a call to populate staff for owners:

```js
        if (role === 'ADMIN' || isOrganizer) {
          await loadStaff(evRes.data);
        }
```

Add `loadStaff` to the `load` effect dependency array (the array currently `[eventId, role, myId, fetchAttendees]` → add `loadStaff`).

- [ ] **Step 5: Render the staff panel (owner only)**

In the JSX, inside the `{isManager && (` panel, after the check-in `<form>` (line ~189), add an owner-only block:

```jsx
          {isOwner && (
            <div className="att-staff">
              <div className="att-staff__title">Сотрудники мероприятия</div>
              <div className="att-staff__sub">Могут отмечать приход и видеть ответы. Не редактируют событие.</div>
              <form className="att-staff__add" onSubmit={addStaff}>
                <input
                  className="att-staff__input"
                  type="text"
                  placeholder="username сотрудника"
                  value={staffInput}
                  onChange={e => setStaffInput(e.target.value)}
                />
                <button type="submit" className="att-staff__btn" disabled={staffBusy || !staffInput.trim()}>
                  Добавить
                </button>
              </form>
              {staff.length > 0 && (
                <ul className="att-staff__list">
                  {staff.map(s => (
                    <li key={s.id} className="att-staff__item">
                      <span>{s.username}</span>
                      <button type="button" className="att-staff__remove" onClick={() => removeStaff(s.id)}>×</button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
```

- [ ] **Step 6: Append panel styles**

Append to `frontend/src/css/EventLikers.css`:

```css
.att-staff { margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--line, #e5e7eb); }
.att-staff__title { font-weight: 700; font-size: 14px; color: var(--ink-900, #111827); }
.att-staff__sub { font-size: 12px; color: var(--ink-500, #6b7280); margin: 2px 0 10px; }
.att-staff__add { display: flex; gap: 8px; }
.att-staff__input { flex: 1; padding: 8px 10px; border: 1.5px solid var(--line, #e5e7eb); border-radius: 8px; font-size: 13px; }
.att-staff__btn { padding: 8px 14px; border: none; border-radius: 8px; background: var(--ink-900, #111827); color: #fff; font-weight: 600; font-size: 13px; cursor: pointer; }
.att-staff__btn:disabled { opacity: .5; cursor: default; }
.att-staff__list { list-style: none; padding: 0; margin: 10px 0 0; display: flex; flex-wrap: wrap; gap: 8px; }
.att-staff__item { display: flex; align-items: center; gap: 6px; padding: 4px 6px 4px 12px; background: var(--sky-soft, #eef2ff); border-radius: 999px; font-size: 13px; }
.att-staff__remove { border: none; background: transparent; font-size: 16px; line-height: 1; cursor: pointer; color: var(--ink-500, #6b7280); }
```

- [ ] **Step 7: Build to verify it compiles**

Run: `cd frontend && npm run build`
Expected: `✓ built` with no errors.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/EventRegistrants.jsx frontend/src/css/EventLikers.css
git commit -m "feat(frontend): staff access to attendee view + organizer staff management panel"
```

---

### Task 7: Profile badge — "Помогает на мероприятиях"

**Files:**
- Modify: `frontend/src/components/Profile.jsx`

**Interfaces:**
- Consumes: `GET /api/events/staffed-by/{userId}` (Task 2); `user.id` from `useProfileData`.
- Produces: a sidebar block listing upcoming events the profile user staffs.

- [ ] **Step 1: Fetch staffed events**

In `Profile.jsx`, add state + effect after the existing `useProfileData` line (line ~21):

```jsx
  const [staffedEvents, setStaffedEvents] = useState([]);
  useEffect(() => {
    if (!user?.id) { setStaffedEvents([]); return; }
    api.get(`/api/events/staffed-by/${user.id}`)
      .then(res => setStaffedEvents(Array.isArray(res.data) ? res.data : []))
      .catch(() => setStaffedEvents([]));
  }, [user?.id]);
```

Ensure `useState`, `useEffect` are imported from `react`, `api` from `../api`, and `Link` from `react-router-dom` (add any missing import).

- [ ] **Step 2: Render the badge in the sidebar**

In the sidebar (`pf__sidebar`), after the socials block (around line 96-118, after the tags/socials), add:

```jsx
          {staffedEvents.length > 0 && (
            <div className="pf__staffed">
              <div className="pf__staffed-title">Помогает на мероприятиях</div>
              <ul className="pf__staffed-list">
                {staffedEvents.map(ev => (
                  <li key={ev.id}>
                    <Link to={`/events/${ev.id}`} className="pf__staffed-link">{ev.title}</Link>
                  </li>
                ))}
              </ul>
            </div>
          )}
```

- [ ] **Step 3: Add styles**

Append to the profile stylesheet (find it via the existing `import '../css/...'` in `Profile.jsx`; it is `frontend/src/css/Profile.css`):

```css
.pf__staffed { margin-top: 16px; }
.pf__staffed-title { font-size: 13px; font-weight: 700; color: var(--ink-700, #374151); margin-bottom: 6px; }
.pf__staffed-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 4px; }
.pf__staffed-link { font-size: 13px; color: var(--sky-ink, #4f46e5); text-decoration: none; }
.pf__staffed-link:hover { text-decoration: underline; }
```

Confirm the stylesheet path with: `grep -n "import '../css" frontend/src/components/Profile.jsx`. Use whichever CSS file Profile imports.

- [ ] **Step 4: Build to verify it compiles**

Run: `cd frontend && npm run build`
Expected: `✓ built` with no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/Profile.jsx frontend/src/css/Profile.css
git commit -m "feat(frontend): profile badge for events a user staffs"
```

---

### Task 8: End-to-end verification

**Files:**
- Create: `/tmp/e2e_staff.py` (verification script, not committed)

**Interfaces:**
- Consumes: the full running stack via the gateway.

- [ ] **Step 1: Rebuild and start the backend**

Run: `./start.sh --build --no-front`
Expected: ends with "EventHub.kz поднят"; `docker ps` shows event-service and registration-service Up.

- [ ] **Step 2: Write the E2E script**

Create `/tmp/e2e_staff.py`:

```python
#!/usr/bin/env python3
import requests, sys, time, json
GW = "http://localhost:8180"; PW = "password123"
def login(email):
    r = requests.post(f"{GW}/auth/login", json={"email": email, "password": PW}); r.raise_for_status()
    return r.json()["token"]
def H(t): return {"Authorization": f"Bearer {t}"}
results=[]
def check(n,c,d=""):
    results.append(c); print(("  PASS " if c else "  FAIL ")+n+(f"  -- {d}" if d else ""))

admin=login("admin@eventhub.kz"); org=login("aidar.kasenov@example.kz")
staff_email="alisher.zhakupov@example.kz"; staff=login(staff_email)
stranger=login("timur.akhmetov@example.kz")

# staff + stranger user ids
staff_id=requests.get(f"{GW}/api/users/username/alisher").json()["id"]

# create a NATIVE event via request->approve (reuse request flow)
title=f"E2E Staff {int(time.time())}"
q=[{"id":"q1","label":"Размер","type":"SINGLE","required":True,"options":["S","M"]}]
req={"title":title,"shortDescription":"staff e2e test","fullDescription":"full description for staff e2e",
     "tags":["IT"],"location":"Almaty","online":False,"eventDate":"2027-02-02T10:00:00",
     "registrationDeadline":"2026-12-31T10:00:00","registrationType":"NATIVE","externalLink":"",
     "contactEmail":"aidar.kasenov@example.kz","questions":q}
rid=requests.post(f"{GW}/api/event-requests",headers=H(org),json=req).json()["id"]
requests.post(f"{GW}/api/event-requests/{rid}/approve",headers=H(admin))
# find event
eid=None
for _ in range(15):
    items=requests.get(f"{GW}/api/events",params={},headers=H(org)).json()
    items=items.get("content",items) if isinstance(items,dict) else items
    m=[e for e in items if e.get("title")==title]
    if m: eid=m[0]["id"]; break
    time.sleep(2)
check("event created",eid is not None)
if not eid: print("ABORT"); sys.exit(1)

# attendee registers
att=login("dinara.zhumabaeva@example.kz")  # ADMIN but registering as a normal attendee is fine
reg=requests.post(f"{GW}/api/registrations/event/{eid}",headers=H(att),json={"answers":{"q1":"M"}})
code=reg.json().get("code")
check("attendee registered",reg.status_code==201,f"status={reg.status_code}")

# before adding staff: staff user is forbidden from attendees + checkin
check("staff forbidden before add (attendees)",
      requests.get(f"{GW}/api/events/{eid}/attendees",headers=H(staff)).status_code==403)
check("staff forbidden before add (checkin)",
      requests.post(f"{GW}/api/registrations/checkin",headers=H(staff),json={"code":code}).status_code==403)

# organizer adds staff
add=requests.post(f"{GW}/api/events/{eid}/staff",headers=H(org),json={"userId":staff_id})
check("organizer adds staff -> 204",add.status_code==204,f"status={add.status_code}")

# stranger CANNOT add staff
check("stranger cannot add staff -> 403",
      requests.post(f"{GW}/api/events/{eid}/staff",headers=H(stranger),json={"userId":staff_id}).status_code==403)

# now staff can view attendees (with answers) and check in
av=requests.get(f"{GW}/api/events/{eid}/attendees",headers=H(staff))
check("staff sees attendees -> 200",av.status_code==200,f"status={av.status_code}")
check("staff sees answers",any(a.get("answers",{}).get("q1")=="M" for a in (av.json() or [])))
ci=requests.post(f"{GW}/api/registrations/checkin",headers=H(staff),json={"code":code})
check("staff checks in -> 200",ci.status_code==200 and ci.json().get("status")=="ATTENDED",f"status={ci.status_code}")

# staffed-by lists the event (public)
sb=requests.get(f"{GW}/api/events/staffed-by/{staff_id}").json()
check("staffed-by lists event",any(e.get("id")==eid for e in (sb if isinstance(sb,list) else [])))

# organizer removes staff -> staff forbidden again
requests.delete(f"{GW}/api/events/{eid}/staff/{staff_id}",headers=H(org))
check("staff forbidden after removal",
      requests.get(f"{GW}/api/events/{eid}/attendees",headers=H(staff)).status_code==403)

# cleanup
requests.delete(f"{GW}/api/events/{eid}",headers=H(admin))
print(f"\n=== {sum(results)}/{len(results)} passed ===")
sys.exit(0 if all(results) else 1)
```

- [ ] **Step 2b: Verify staff user id resolves**

The script resolves `alisher` via `/api/users/username/alisher`. If your seed uses a different username for the staff user, adjust `staff_email`, the `login`, and the username lookup to match.

- [ ] **Step 3: Run the E2E**

Run: `python3 /tmp/e2e_staff.py`
Expected: all checks PASS (line `=== N/N passed ===`).

- [ ] **Step 4: Visual check (manual)**

Start the frontend (`./start.sh` or `cd frontend && npm run dev`), then as the organizer open `/events/<id>/registrants` and confirm the "Сотрудники мероприятия" panel adds/removes by username; open a ticket and confirm the QR encodes `/checkin/<code>` (decode it or read the rendered code); visit the staff user's profile and confirm "Помогает на мероприятиях" lists the event.

- [ ] **Step 5: No commit**

This task only verifies; nothing to commit. If a defect surfaces, fix it in the relevant task's files and re-run.

---

## Done criteria

- All unit/IT tests pass (`EventStaffRepositoryIT`, `EventServiceStaffTest`, `EventRegistrationAuthTest`, plus the existing `AnswerValidatorTest`).
- `cd frontend && npm run build` is clean.
- `/tmp/e2e_staff.py` reports all checks passing.
- Organizer can add/remove staff by username; staff can check in (deep-link QR + manual code) and see answers but not edit the event; a stranger is 403 on all staff/attendee operations; the profile badge lists upcoming staffed events.
- After verification, finish the branch via superpowers:finishing-a-development-branch.
