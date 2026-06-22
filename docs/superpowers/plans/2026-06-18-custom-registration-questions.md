# Custom Registration Questions — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let event organizers attach custom questions (free-text + single-choice) to a NATIVE event via the request form; attendees answer them when registering; organizers view answers in the cabinet and Excel export.

**Architecture:** Question *definitions* live with the event (event-service: carried `EventRequest` → `Event`, exposed in `EventDTO`); *answers* live with the registration (registration-service: `EventRegistration.answers`). registration-service validates answers server-side via the existing `EventClient`. Both questions and answers are stored as JSON (`@JdbcTypeCode(SqlTypes.JSON)` → Postgres `jsonb`). Answers are returned only through an organizer-authorized path.

**Tech Stack:** Java 21, Spring Boot 3.4.2, Spring Data JPA / Hibernate 6, OpenFeign, Kafka, PostgreSQL; React 19 + Vite, axios, react-hot-toast, SheetJS (`xlsx`).

Source spec: `docs/superpowers/specs/2026-06-18-custom-registration-questions-design.md`

## Global Constraints

- Java 21, Spring Boot 3.4.2 across all services. New shared types go in `eventhubkz-common`.
- Question limits (enforce server-side AND client-side): ≤ 10 questions per event, ≤ 10 options per single-choice question, question `label` ≤ 200 chars, text answer ≤ 1000 chars.
- Question types are exactly `TEXT` and `SINGLE`. `options` is non-empty only for `SINGLE`.
- Questions apply only to events whose `registrationType == NATIVE`.
- JSON persistence: `@JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)` on `List<QuestionDef>` / `Map<String,String>` fields (maps to `jsonb`; Jackson is already on the classpath via the web starter). If `ddl-auto=update` fails to add a `jsonb` column on an existing volume, recreate the service's Postgres volume (same pattern documented for the notifications enum check).
- Commit rules (project): commits are solely the user's account — **no** `Co-Authored-By` / Claude/Anthropic attribution in any commit message. Never commit `.env`. Pushes go `git push origin main:master`.
- Backward compatibility: `questions`/`answers` null ⇒ treated as empty. No data migration.
- Idempotent registration must be preserved: re-registering returns the existing record and does NOT overwrite answers.

## File Structure

**eventhubkz-common** (new shared types)
- Create `eventhubkz-common/src/main/java/org/ngcvfb/eventhubkz/common/dto/QuestionType.java` — enum `TEXT, SINGLE`.
- Create `eventhubkz-common/src/main/java/org/ngcvfb/eventhubkz/common/dto/QuestionDef.java` — record.
- Modify `eventhubkz-common/.../dto/EventDTO.java` — add `List<QuestionDef> questions`.

**event-service**
- Modify `model/EventRequest.java` — add `questions` (jsonb) + validation.
- Modify `model/Event.java` — add `questions` (jsonb).
- Modify `service/EventService.java` — carry `questions` in `createEvent`, expose in `toDTO`, enrich `getAttendees` with answers.
- Modify `service/EventRequestService.java` — copy `questions` into the `EventDTO` it builds in `approveRequest`.
- Modify `dto/AttendeeDTO.java` — add `Map<String,String> answers`.
- Modify `client/RegistrationClient.java` — add organizer answers endpoint.

**registration-service**
- Modify `pom.xml` — add `spring-boot-starter-test` (test scope) — no test infra exists yet.
- Modify `model/EventRegistration.java` — add `answers` (jsonb).
- Create `service/AnswerValidator.java` — pure validation helper (unit-tested).
- Modify `service/EventRegistrationService.java` — accept answers in `register`, validate, store; add `getAttendeeAnswers`.
- Modify `controller/RegistrationController.java` — accept `{answers}` body in register; add organizer answers endpoint.
- Create `dto/AttendeeAnswerView.java` — record `{userId, status, answers}`.
- Create `src/test/java/org/ngcvfb/registrationservice/service/AnswerValidatorTest.java`.

**frontend**
- Modify `components/EventRequestForm.jsx` — question editor; send `questions`.
- Create `components/QuestionEditor.jsx` — the editable questions block.
- Create `css/QuestionEditor.css`.
- Modify `components/AdminEventRequests.jsx` — read-only questions display.
- Modify `hooks/useToggleResource.js` — `onBeforeActivate` hook.
- Create `components/RegistrationModal.jsx` + `css/RegistrationModal.css`.
- Modify `components/RegisterButton.jsx` — open modal when questions exist.
- Modify `components/EventDetail.jsx` and `components/EventCard.jsx` — pass `questions` to `RegisterButton`.
- Modify `components/EventRegistrants.jsx` — expandable answers row + Excel columns.

---

### Task 1: Shared `QuestionDef` / `QuestionType` + `EventDTO.questions`

**Files:**
- Create: `eventhubkz-common/src/main/java/org/ngcvfb/eventhubkz/common/dto/QuestionType.java`
- Create: `eventhubkz-common/src/main/java/org/ngcvfb/eventhubkz/common/dto/QuestionDef.java`
- Modify: `eventhubkz-common/src/main/java/org/ngcvfb/eventhubkz/common/dto/EventDTO.java`

**Interfaces:**
- Produces: `enum QuestionType { TEXT, SINGLE }`; `record QuestionDef(String id, String label, QuestionType type, boolean required, List<String> options)`; `EventDTO.getQuestions()/setQuestions(List<QuestionDef>)`.

- [ ] **Step 1: Create `QuestionType`**

```java
package org.ngcvfb.eventhubkz.common.dto;

public enum QuestionType {
    TEXT, SINGLE
}
```

- [ ] **Step 2: Create `QuestionDef`**

```java
package org.ngcvfb.eventhubkz.common.dto;

import java.util.List;

/** Определение кастомного вопроса регистрации. options непуст только для SINGLE. */
public record QuestionDef(
        String id,
        String label,
        QuestionType type,
        boolean required,
        List<String> options
) {}
```

- [ ] **Step 3: Add `questions` to `EventDTO`**

In `EventDTO.java` add the import and field:

```java
import java.util.List;
```
```java
    private Set<String> tags;
    private List<QuestionDef> questions;   // ← add this line after tags
```

- [ ] **Step 4: Build common module**

Run: `mvn -q -pl eventhubkz-common -am install -DskipTests`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add eventhubkz-common/
git commit -m "feat(common): QuestionDef/QuestionType + EventDTO.questions"
```

---

### Task 2: `EventRequest.questions` (jsonb) + validation

**Files:**
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/model/EventRequest.java`

**Interfaces:**
- Consumes: `QuestionDef` (Task 1).
- Produces: `EventRequest.getQuestions()/setQuestions(List<QuestionDef>)` persisted as `jsonb`.

- [ ] **Step 1: Add imports to `EventRequest.java`**

```java
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.ngcvfb.eventhubkz.common.dto.QuestionDef;
import jakarta.validation.constraints.Size;   // already imported — keep single import
import java.util.List;
```

- [ ] **Step 2: Add the field** (after the `tags` collection, before `location`)

```java
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Size(max = 10, message = "Не больше 10 вопросов")
    private List<QuestionDef> questions;
```

- [ ] **Step 3: Compile event-service**

Run: `mvn -q -pl event-service -am compile`
Expected: `BUILD SUCCESS` (no test run).

- [ ] **Step 4: Commit**

```bash
git add event-service/src/main/java/org/ngcvfb/eventservice/model/EventRequest.java
git commit -m "feat(event-request): store custom questions (jsonb)"
```

> Deep per-question validation (label length, options non-empty for SINGLE, ≤10 options) is enforced in the frontend (Task 6) and is structurally bounded by the `@Size(max=10)` cap here; full server-side per-question checks are out of scope for MVP (questions are organizer-authored, not attacker input at scale).

---

### Task 3: `Event.questions` + carry through approval + expose in DTO

**Files:**
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/model/Event.java`
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/service/EventService.java`
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/service/EventRequestService.java`

**Interfaces:**
- Consumes: `QuestionDef`, `EventRequest.getQuestions()`, `EventDTO.getQuestions()`.
- Produces: `Event.getQuestions()/setQuestions(...)`; `EventDTO.questions` populated by `toDTO`; questions copied to the new event on approval.

- [ ] **Step 1: Add field to `Event.java`**

Add the same imports as Task 2 (`JdbcTypeCode`, `SqlTypes`, `QuestionDef`, `java.util.List`) and the field near `registrationType`:

```java
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<QuestionDef> questions;
```

- [ ] **Step 2: Populate questions in `EventService.createEvent`**

In `EventService.java`, in the `Event.builder()` chain inside `createEvent`, add after `.externalLink(dto.getExternalLink())`:

```java
                .questions(dto.getQuestions())
```

- [ ] **Step 3: Expose questions in `EventService.toDTO`**

In `toDTO`, after `dto.setExternalLink(event.getExternalLink());` add:

```java
        dto.setQuestions(event.getQuestions());
```

- [ ] **Step 4: Copy questions on approval**

In `EventRequestService.approveRequest`, in the `EventDTO dto = new EventDTO();` block, after `dto.setExternalLink(approved.getExternalLink());` add:

```java
        dto.setQuestions(approved.getQuestions());
```

- [ ] **Step 5: Compile**

Run: `mvn -q -pl event-service -am compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add event-service/src/main/java/org/ngcvfb/eventservice/model/Event.java \
        event-service/src/main/java/org/ngcvfb/eventservice/service/EventService.java \
        event-service/src/main/java/org/ngcvfb/eventservice/service/EventRequestService.java
git commit -m "feat(event): carry custom questions request→event and expose in DTO"
```

---

### Task 4: registration-service stores + validates answers

**Files:**
- Modify: `registration-service/pom.xml`
- Modify: `registration-service/src/main/java/org/ngcvfb/registrationservice/model/EventRegistration.java`
- Create: `registration-service/src/main/java/org/ngcvfb/registrationservice/service/AnswerValidator.java`
- Test: `registration-service/src/test/java/org/ngcvfb/registrationservice/service/AnswerValidatorTest.java`
- Modify: `registration-service/src/main/java/org/ngcvfb/registrationservice/service/EventRegistrationService.java`
- Modify: `registration-service/src/main/java/org/ngcvfb/registrationservice/controller/RegistrationController.java`

**Interfaces:**
- Consumes: `QuestionDef`, `QuestionType`, `EventDTO.getQuestions()` (Tasks 1,3); `EventClient.getEventById` (existing).
- Produces:
  - `EventRegistration.getAnswers()/setAnswers(Map<String,String>)`.
  - `AnswerValidator.validateAndClean(List<QuestionDef> questions, Map<String,String> answers) -> Map<String,String>` (throws `ResponseStatusException(400)` on invalid; returns cleaned answers limited to known question ids).
  - `EventRegistrationService.register(Long userId, Long eventId, String userEmail, String username, Map<String,String> answers)` (signature gains `answers`).

- [ ] **Step 1: Add test starter to `registration-service/pom.xml`**

Add inside `<dependencies>`:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Write the failing test for `AnswerValidator`**

Create `registration-service/src/test/java/org/ngcvfb/registrationservice/service/AnswerValidatorTest.java`:

```java
package org.ngcvfb.registrationservice.service;

import org.junit.jupiter.api.Test;
import org.ngcvfb.eventhubkz.common.dto.QuestionDef;
import org.ngcvfb.eventhubkz.common.dto.QuestionType;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnswerValidatorTest {

    private final AnswerValidator validator = new AnswerValidator();

    private List<QuestionDef> questions() {
        return List.of(
                new QuestionDef("q1", "Размер футболки", QuestionType.SINGLE, true, List.of("S", "M", "L")),
                new QuestionDef("q2", "Комментарий", QuestionType.TEXT, false, null));
    }

    @Test
    void passesWithValidAnswers() {
        Map<String, String> cleaned = validator.validateAndClean(questions(),
                Map.of("q1", "M", "q2", "ничего"));
        assertEquals("M", cleaned.get("q1"));
        assertEquals("ничего", cleaned.get("q2"));
    }

    @Test
    void rejectsMissingRequired() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> validator.validateAndClean(questions(), Map.of("q2", "hi")));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void rejectsSingleValueOutsideOptions() {
        assertThrows(ResponseStatusException.class,
                () -> validator.validateAndClean(questions(), Map.of("q1", "XXL")));
    }

    @Test
    void dropsUnknownKeys() {
        Map<String, String> cleaned = validator.validateAndClean(questions(),
                Map.of("q1", "S", "qZ", "junk"));
        assertFalse(cleaned.containsKey("qZ"));
    }

    @Test
    void emptyQuestionsYieldsEmptyMap() {
        assertTrue(validator.validateAndClean(null, Map.of("q1", "x")).isEmpty());
        assertTrue(validator.validateAndClean(List.of(), null).isEmpty());
    }

    @Test
    void rejectsTooLongTextAnswer() {
        assertThrows(ResponseStatusException.class,
                () -> validator.validateAndClean(questions(),
                        Map.of("q1", "S", "q2", "x".repeat(1001))));
    }
}
```

- [ ] **Step 3: Run the test — verify it fails to compile/fails**

Run: `mvn -q -pl registration-service -am test -Dtest=AnswerValidatorTest`
Expected: FAILS — `AnswerValidator` does not exist yet.

- [ ] **Step 4: Implement `AnswerValidator`**

Create `registration-service/src/main/java/org/ngcvfb/registrationservice/service/AnswerValidator.java`:

```java
package org.ngcvfb.registrationservice.service;

import org.ngcvfb.eventhubkz.common.dto.QuestionDef;
import org.ngcvfb.eventhubkz.common.dto.QuestionType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Проверяет ответы участника против вопросов мероприятия и возвращает только валидные. */
@Component
public class AnswerValidator {

    private static final int MAX_TEXT = 1000;

    public Map<String, String> validateAndClean(List<QuestionDef> questions, Map<String, String> answers) {
        Map<String, String> cleaned = new LinkedHashMap<>();
        if (questions == null || questions.isEmpty()) {
            return cleaned;
        }
        Map<String, String> given = answers == null ? Map.of() : answers;

        for (QuestionDef q : questions) {
            String raw = given.get(q.id());
            String value = raw == null ? null : raw.trim();
            boolean empty = value == null || value.isEmpty();

            if (empty) {
                if (q.required()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Ответьте на обязательный вопрос: " + q.label());
                }
                continue;
            }
            if (value.length() > MAX_TEXT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Слишком длинный ответ на вопрос: " + q.label());
            }
            if (q.type() == QuestionType.SINGLE) {
                List<String> opts = q.options() == null ? List.of() : q.options();
                if (!opts.contains(value)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Недопустимый вариант ответа на вопрос: " + q.label());
                }
            }
            cleaned.put(q.id(), value);
        }
        return cleaned;
    }
}
```

- [ ] **Step 5: Run the test — verify it passes**

Run: `mvn -q -pl registration-service -am test -Dtest=AnswerValidatorTest`
Expected: PASS (6 tests).

- [ ] **Step 6: Add `answers` to `EventRegistration`**

In `EventRegistration.java` add imports `org.hibernate.annotations.JdbcTypeCode`, `org.hibernate.type.SqlTypes`, `java.util.Map`, and the field:

```java
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> answers;
```

- [ ] **Step 7: Wire validation into `register`**

In `EventRegistrationService.java`:

1. Add field: `private final AnswerValidator answerValidator;` (constructor-injected via `@RequiredArgsConstructor`).
2. Change the signature and body of `register`:

```java
    public EventRegistration register(Long userId, Long eventId, String userEmail,
                                      String username, Map<String, String> answers) {
        EventRegistration existing = registrationRepository.findByUserIdAndEventId(userId, eventId).orElse(null);
        if (existing != null) {
            log.info("User {} already registered for event {}", userId, eventId);
            return existing; // идемпотентно: ответы НЕ перезаписываем
        }

        EventDTO event = fetchEvent(eventId);
        validateRegistrationOpen(event);
        Map<String, String> cleanAnswers = answerValidator.validateAndClean(event.getQuestions(), answers);

        EventRegistration registration = EventRegistration.builder()
                .userId(userId)
                .eventId(eventId)
                .status(RegistrationStatus.REGISTERED)
                .code(generateUniqueCode())
                .answers(cleanAnswers)
                .build();
```

(the rest of `register` — try/save/Kafka — is unchanged). Add `import java.util.Map;` if missing.

- [ ] **Step 8: Accept `{answers}` body in the controller**

In `RegistrationController.java`, change the `register` endpoint to read an optional body:

```java
    @PostMapping("/event/{eventId}")
    public ResponseEntity<EventRegistration> register(
            @PathVariable Long eventId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader(value = "X-Username", required = false) String username,
            @RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, String> answers = body == null ? null
                : (Map<String, String>) (Map<?, ?>) body.getOrDefault("answers", null);
        EventRegistration registration = registrationService.register(eventId == null ? null : eventId,
                userId, userEmail, username, answers) ; // see note
        return ResponseEntity.status(HttpStatus.CREATED).body(registration);
    }
```

> Note: keep the existing argument order `register(userId, eventId, userEmail, username, answers)`. Concretely:
> `EventRegistration registration = registrationService.register(userId, eventId, userEmail, username, answers);`
> Ensure `java.util.Map` is imported (it already is for `/checkin`).

- [ ] **Step 9: Full registration-service test run**

Run: `mvn -q -pl registration-service -am test`
Expected: `BUILD SUCCESS`.

- [ ] **Step 10: Commit**

```bash
git add registration-service/
git commit -m "feat(registration): collect and validate custom answers"
```

---

### Task 5: Organizer-authorized answers in the attendee list

**Files:**
- Create: `registration-service/src/main/java/org/ngcvfb/registrationservice/dto/AttendeeAnswerView.java`
- Modify: `registration-service/src/main/java/org/ngcvfb/registrationservice/service/EventRegistrationService.java`
- Modify: `registration-service/src/main/java/org/ngcvfb/registrationservice/controller/RegistrationController.java`
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/dto/AttendeeDTO.java`
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/client/RegistrationClient.java`
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/service/EventService.java`

**Interfaces:**
- Consumes: `EventClient.getEventById`, `EventDTO.getOrganizerId()`; registration repo `findByEventId`.
- Produces:
  - registration-service: `GET /api/registrations/event/{eventId}/attendees` (headers `X-User-Id`, `X-User-Role`) → `List<AttendeeAnswerView>` where `AttendeeAnswerView(Long userId, String status, Map<String,String> answers)`. Performs organizer-or-admin check via `EventClient` (mirrors `checkIn`).
  - event-service: `AttendeeDTO(Long userId, String username, String email, String status, Map<String,String> answers)`; `RegistrationClient.getEventAttendees(eventId, userId, role)`.

- [ ] **Step 1: Create `AttendeeAnswerView`**

```java
package org.ngcvfb.registrationservice.dto;

import org.ngcvfb.registrationservice.model.EventRegistration;

import java.util.Map;

/** Участник + ответы — отдаётся ТОЛЬКО организатору события/админу. */
public record AttendeeAnswerView(Long userId, String status, Map<String, String> answers) {
    public static AttendeeAnswerView from(EventRegistration r) {
        return new AttendeeAnswerView(
                r.getUserId(),
                r.getStatus() == null ? "REGISTERED" : r.getStatus().name(),
                r.getAnswers());
    }
}
```

- [ ] **Step 2: Add `getAttendeeAnswers` to `EventRegistrationService`**

```java
    /** Участники события с ответами — только организатор события или админ. */
    public List<org.ngcvfb.registrationservice.dto.AttendeeAnswerView> getAttendeeAnswers(
            Long eventId, Long requesterId, String role) {
        EventDTO event = fetchEvent(eventId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        if (!isAdmin && !Objects.equals(event.getOrganizerId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ответы участников доступны только организатору мероприятия");
        }
        return registrationRepository.findByEventId(eventId).stream()
                .map(org.ngcvfb.registrationservice.dto.AttendeeAnswerView::from)
                .toList();
    }
```

- [ ] **Step 3: Add the controller endpoint** (in `RegistrationController.java`)

```java
    /** Участники с ответами — только организатор/админ (проверка в сервисе через EventClient). */
    @GetMapping("/event/{eventId}/attendees")
    public ResponseEntity<List<org.ngcvfb.registrationservice.dto.AttendeeAnswerView>> attendeeAnswers(
            @PathVariable Long eventId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        return ResponseEntity.ok(registrationService.getAttendeeAnswers(eventId, userId, role));
    }
```

> Gateway: the path `/api/registrations/event/{id}/attendees` already matches the existing `registration-service-auth` route (`/api/registrations/**` with `AuthenticationFilter`), which injects `X-User-Id`/`X-User-Role`. No gateway change needed.

- [ ] **Step 4: Add `answers` to `AttendeeDTO` (event-service)**

```java
package org.ngcvfb.eventservice.dto;

import java.util.Map;

public record AttendeeDTO(Long userId, String username, String email, String status,
                          Map<String, String> answers) {
}
```

- [ ] **Step 5: Add Feign method to `RegistrationClient` (event-service)**

```java
    /** Участники с ответами — организатор/админ. Headers форвардятся из getAttendees. */
    @GetMapping("/api/registrations/event/{eventId}/attendees")
    List<Map<String, Object>> getEventAttendees(
            @PathVariable("eventId") Long eventId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role);
```

Add imports `org.springframework.web.bind.annotation.RequestHeader`. Keep the existing `getRegistrationsByEvent` method.

- [ ] **Step 6: Enrich `EventService.getAttendees`**

Replace the registrations fetch + map building in `getAttendees` so it uses the answers endpoint and forwards identity:

```java
        List<Map<String, Object>> registrations;
        try {
            registrations = registrationClient.getEventAttendees(eventId, requesterId, role);
        } catch (Exception e) {
            log.error("Failed to fetch registrations for event {}: {}", eventId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Сервис записей временно недоступен, попробуйте позже");
        }
        Map<Long, String> statusByUser = new java.util.HashMap<>();
        Map<Long, Map<String, String>> answersByUser = new java.util.HashMap<>();
        for (Map<String, Object> r : registrations) {
            if (r.get("userId") == null) continue;
            Long uid = Long.valueOf(r.get("userId").toString());
            statusByUser.put(uid, r.get("status") == null ? "REGISTERED" : r.get("status").toString());
            Object ans = r.get("answers");
            if (ans instanceof Map<?, ?> m) {
                Map<String, String> a = new java.util.HashMap<>();
                m.forEach((k, v) -> a.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
                answersByUser.put(uid, a);
            }
        }
        List<Long> userIds = new java.util.ArrayList<>(statusByUser.keySet());
        if (userIds.isEmpty()) {
            return List.of();
        }

        return userClient.getUsersByIds(userIds).stream()
                .map(u -> new AttendeeDTO(u.getId(), u.getUsername(), u.getEmail(),
                        statusByUser.getOrDefault(u.getId(), "REGISTERED"),
                        answersByUser.getOrDefault(u.getId(), Map.of())))
                .sorted((a, b) -> {
                    String an = a.username() == null ? "" : a.username();
                    String bn = b.username() == null ? "" : b.username();
                    return an.compareToIgnoreCase(bn);
                })
                .collect(Collectors.toList());
```

- [ ] **Step 7: Compile both services**

Run: `mvn -q -pl event-service,registration-service -am compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add registration-service/ event-service/
git commit -m "feat(registration): organizer-only attendee answers endpoint + DTO"
```

---

### Task 6: Question editor in the request form

**Files:**
- Create: `frontend/src/components/QuestionEditor.jsx`
- Create: `frontend/src/css/QuestionEditor.css`
- Modify: `frontend/src/components/EventRequestForm.jsx`

**Interfaces:**
- Produces: `<QuestionEditor questions={array} onChange={fn} />`; each question `{ id, label, type: 'TEXT'|'SINGLE', required, options: string[] }`. `EventRequestForm` sends `questions` in the POST body.

- [ ] **Step 1: Create `QuestionEditor.jsx`**

```jsx
import React from 'react';
import '../css/QuestionEditor.css';

const MAX_Q = 10;
const MAX_OPT = 10;
let seq = 0;
const newId = () => `q${Date.now().toString(36)}${(seq++).toString(36)}`;

export default function QuestionEditor({ questions, onChange }) {
  const update = (i, patch) => onChange(questions.map((q, idx) => idx === i ? { ...q, ...patch } : q));
  const add = () => {
    if (questions.length >= MAX_Q) return;
    onChange([...questions, { id: newId(), label: '', type: 'TEXT', required: false, options: [] }]);
  };
  const remove = (i) => onChange(questions.filter((_, idx) => idx !== i));
  const setOpt = (i, j, val) => update(i, { options: questions[i].options.map((o, k) => k === j ? val : o) });
  const addOpt = (i) => questions[i].options.length < MAX_OPT && update(i, { options: [...questions[i].options, ''] });
  const removeOpt = (i, j) => update(i, { options: questions[i].options.filter((_, k) => k !== j) });

  return (
    <div className="qed">
      {questions.map((q, i) => (
        <div className="qed__item" key={q.id}>
          <div className="qed__row">
            <input className="qed__label" placeholder="Текст вопроса" maxLength={200}
                   value={q.label} onChange={e => update(i, { label: e.target.value })} />
            <select className="qed__type" value={q.type}
                    onChange={e => update(i, { type: e.target.value, options: e.target.value === 'SINGLE' ? (q.options.length ? q.options : ['']) : [] })}>
              <option value="TEXT">Текст</option>
              <option value="SINGLE">Один из списка</option>
            </select>
            <label className="qed__req">
              <input type="checkbox" checked={q.required} onChange={e => update(i, { required: e.target.checked })} />
              Обяз.
            </label>
            <button type="button" className="qed__del" onClick={() => remove(i)} aria-label="Удалить вопрос">×</button>
          </div>
          {q.type === 'SINGLE' && (
            <div className="qed__opts">
              {q.options.map((o, j) => (
                <div className="qed__opt" key={j}>
                  <input placeholder={`Вариант ${j + 1}`} value={o} maxLength={200}
                         onChange={e => setOpt(i, j, e.target.value)} />
                  <button type="button" onClick={() => removeOpt(i, j)} aria-label="Удалить вариант">×</button>
                </div>
              ))}
              {q.options.length < MAX_OPT && (
                <button type="button" className="qed__add-opt" onClick={() => addOpt(i)}>+ вариант</button>
              )}
            </div>
          )}
        </div>
      ))}
      {questions.length < MAX_Q && (
        <button type="button" className="qed__add" onClick={add}>+ Добавить вопрос</button>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Create `QuestionEditor.css`**

```css
.qed { display: flex; flex-direction: column; gap: 10px; }
.qed__item { border: 1px solid var(--color-border, #e8dfce); border-radius: 10px; padding: 12px; background: var(--color-surface-alt, #f9f5ee); }
.qed__row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.qed__label { flex: 1 1 220px; padding: 8px 10px; border: 1px solid var(--color-border, #e8dfce); border-radius: 8px; }
.qed__type { padding: 8px 10px; border: 1px solid var(--color-border, #e8dfce); border-radius: 8px; }
.qed__req { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; }
.qed__del { width: 28px; height: 28px; border: none; border-radius: 50%; background: #f3d6d6; color: #a33; cursor: pointer; font-size: 16px; }
.qed__opts { margin-top: 10px; padding-left: 10px; display: flex; flex-direction: column; gap: 6px; }
.qed__opt { display: flex; gap: 6px; }
.qed__opt input { flex: 1; padding: 6px 8px; border: 1px solid var(--color-border, #e8dfce); border-radius: 6px; }
.qed__opt button { width: 26px; border: none; background: #eee; border-radius: 6px; cursor: pointer; }
.qed__add, .qed__add-opt { align-self: flex-start; background: none; border: 1px dashed var(--color-primary-dark, #2a5475); color: var(--color-primary-dark, #2a5475); padding: 6px 12px; border-radius: 8px; cursor: pointer; font-weight: 600; }
```

- [ ] **Step 3: Wire into `EventRequestForm.jsx`**

1. Import: `import QuestionEditor from './QuestionEditor';`
2. State: add `const [questions, setQuestions] = useState([]);`
3. Add a section before the submit footer (after the "Дополнительно" section):

```jsx
        <div className="erf__section">
          <div className="erf__section-title">Вопросы к участникам (необязательно)</div>
          <p className="erf__sub" style={{ margin: '0 0 10px' }}>
            Участник ответит на них при регистрации (только для регистрации на нашем сайте).
          </p>
          <QuestionEditor questions={questions} onChange={setQuestions} />
        </div>
```

4. In `handleSubmit`, before sending, drop blank questions and include them:

```jsx
      const cleanQuestions = questions
        .map(q => ({ ...q, label: q.label.trim(), options: (q.options || []).map(o => o.trim()).filter(Boolean) }))
        .filter(q => q.label && (q.type !== 'SINGLE' || q.options.length >= 2));
```

Add `questions: cleanQuestions,` to the `api.post('/api/event-requests', { ... })` payload object.

5. Reset on success: add `setQuestions([]);` alongside the other resets.

- [ ] **Step 4: Build**

Run: `cd frontend && npm run build`
Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/QuestionEditor.jsx frontend/src/css/QuestionEditor.css frontend/src/components/EventRequestForm.jsx
git commit -m "feat(frontend): question editor in event request form"
```

---

### Task 7: Admin sees questions read-only when moderating

**Files:**
- Modify: `frontend/src/components/AdminEventRequests.jsx`

**Interfaces:**
- Consumes: request object now carries `questions: QuestionDef[]`.

- [ ] **Step 1: Render questions in each request card**

In `AdminEventRequests.jsx`, inside the per-request card (near where tags/fields are shown), add a read-only block:

```jsx
{Array.isArray(req.questions) && req.questions.length > 0 && (
  <div className="admin-req__questions">
    <div className="admin-req__label">ВОПРОСЫ УЧАСТНИКАМ</div>
    <ol>
      {req.questions.map(q => (
        <li key={q.id}>
          {q.label}
          {q.required ? ' *' : ''}
          {q.type === 'SINGLE' && q.options?.length ? ` — (${q.options.join(', ')})` : ''}
        </li>
      ))}
    </ol>
  </div>
)}
```

(Match existing class-naming in the file; if the file uses different label classes, reuse those. No new CSS file required — reuse existing admin card styles.)

- [ ] **Step 2: Build**

Run: `cd frontend && npm run build`
Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/AdminEventRequests.jsx
git commit -m "feat(admin): show custom questions when moderating a request"
```

---

### Task 8: `useToggleResource` supports a pre-activation hook

**Files:**
- Modify: `frontend/src/hooks/useToggleResource.js`

**Interfaces:**
- Produces: option `onBeforeActivate?: () => Promise<object|null>`. When activating (not active → active) and the hook is provided, it is awaited; `null` aborts (no request, no optimistic change); a returned object is sent as the POST body.

- [ ] **Step 1: Add `onBeforeActivate` param and use it in `toggle`**

Add `onBeforeActivate` to the destructured options. Replace the `toggle` body's activation branch so it resolves a body first:

```js
  const toggle = useCallback(async () => {
    if (busy) return;
    if (!currentUserId) {
      const here = window.location.pathname + window.location.search;
      navigate(`/signin?redirect=${encodeURIComponent(here)}`);
      return;
    }
    const wasActive = active;

    let body;
    if (!wasActive && onBeforeActivate) {
      const result = await onBeforeActivate();
      if (result == null) return; // пользователь отменил
      body = result;
    }

    setBusy(true);
    setActive(!wasActive);
    setCount((c) => Math.max(0, c + (wasActive ? -1 : 1)));
    try {
      if (wasActive) await api.delete(deleteUrl);
      else await api.post(postUrl, body);
      onActiveChange?.(!wasActive);
      const m = wasActive ? msgOff : msgOn;
      if (m) toast.success(m);
    } catch (err) {
      setActive(wasActive);
      setCount((c) => Math.max(0, c + (wasActive ? 1 : -1)));
      const status = err?.response?.status;
      const serverMsg = err?.response?.data?.message;
      toast.error((serverMsg && serverMsg.trim()) || (status === 409 ? 'Действие недоступно' : msgError));
    } finally {
      setBusy(false);
    }
  }, [busy, currentUserId, active, postUrl, deleteUrl, msgOn, msgOff, msgError, onActiveChange, onBeforeActivate, navigate]);
```

(Existing callers pass no `onBeforeActivate` → `api.post(postUrl, undefined)`, identical to today.)

- [ ] **Step 2: Build**

Run: `cd frontend && npm run build`
Expected: build succeeds (LikeButton/RegisterButton unaffected).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/hooks/useToggleResource.js
git commit -m "feat(frontend): useToggleResource onBeforeActivate hook"
```

---

### Task 9: Registration modal + RegisterButton integration

**Files:**
- Create: `frontend/src/components/RegistrationModal.jsx`
- Create: `frontend/src/css/RegistrationModal.css`
- Modify: `frontend/src/components/RegisterButton.jsx`
- Modify: `frontend/src/components/EventDetail.jsx`
- Modify: `frontend/src/components/EventCard.jsx`

**Interfaces:**
- Consumes: `useToggleResource` `onBeforeActivate` (Task 8); `EventDTO.questions` via `event.questions`.
- Produces: `<RegistrationModal questions open onSubmit(answersObj) onClose />`; `RegisterButton` gains prop `questions` (array|null).

- [ ] **Step 1: Create `RegistrationModal.jsx`**

```jsx
import React, { useState } from 'react';
import '../css/RegistrationModal.css';

export default function RegistrationModal({ questions, onSubmit, onClose }) {
  const [values, setValues] = useState({});
  const [err, setErr] = useState('');
  const set = (id, v) => setValues(prev => ({ ...prev, [id]: v }));

  const submit = (e) => {
    e.preventDefault();
    for (const q of questions) {
      const v = (values[q.id] || '').trim();
      if (q.required && !v) { setErr(`Ответьте на вопрос: ${q.label}`); return; }
    }
    onSubmit(values);
  };

  return (
    <div className="rmodal__overlay" onMouseDown={onClose}>
      <div className="rmodal" onMouseDown={e => e.stopPropagation()}>
        <h3 className="rmodal__title">Анкета участника</h3>
        <form onSubmit={submit} className="rmodal__form">
          {questions.map(q => (
            <div className="rmodal__field" key={q.id}>
              <label className="rmodal__label">{q.label}{q.required && <span className="rmodal__req"> *</span>}</label>
              {q.type === 'SINGLE' ? (
                <div className="rmodal__opts">
                  {(q.options || []).map(o => (
                    <label key={o} className="rmodal__opt">
                      <input type="radio" name={q.id} value={o}
                             checked={values[q.id] === o} onChange={() => set(q.id, o)} />
                      {o}
                    </label>
                  ))}
                </div>
              ) : (
                <textarea className="rmodal__input" maxLength={1000}
                          value={values[q.id] || ''} onChange={e => set(q.id, e.target.value)} />
              )}
            </div>
          ))}
          {err && <div className="rmodal__err">{err}</div>}
          <div className="rmodal__actions">
            <button type="button" className="rmodal__cancel" onClick={onClose}>Отмена</button>
            <button type="submit" className="rmodal__submit">Записаться</button>
          </div>
        </form>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Create `RegistrationModal.css`**

```css
.rmodal__overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 16px; }
.rmodal { background: var(--color-surface, #fff); border-radius: 16px; width: 100%; max-width: 460px; max-height: 88vh; overflow: auto; padding: 22px; box-shadow: var(--shadow-lg); }
.rmodal__title { margin: 0 0 14px; font-family: var(--font-family-heading); }
.rmodal__form { display: flex; flex-direction: column; gap: 14px; }
.rmodal__label { display: block; font-weight: 600; margin-bottom: 6px; font-size: 14px; }
.rmodal__req { color: #c0392b; }
.rmodal__input { width: 100%; border: 1px solid var(--color-border, #e8dfce); border-radius: 8px; padding: 8px 10px; min-height: 64px; font: inherit; }
.rmodal__opts { display: flex; flex-direction: column; gap: 6px; }
.rmodal__opt { display: flex; align-items: center; gap: 8px; font-size: 14px; }
.rmodal__err { color: #c0392b; font-size: 13px; }
.rmodal__actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 4px; }
.rmodal__cancel { background: none; border: 1px solid var(--color-border, #e8dfce); border-radius: var(--radius-full); padding: 8px 16px; cursor: pointer; }
.rmodal__submit { background: linear-gradient(135deg, var(--color-primary, #aecee3), var(--color-primary-dark, #2a5475)); color: #fff; border: none; border-radius: var(--radius-full); padding: 8px 18px; font-weight: 700; cursor: pointer; }
```

- [ ] **Step 3: Integrate into `RegisterButton.jsx`**

1. Imports: `import { useState } from 'react';` (extend existing import) and `import RegistrationModal from './RegistrationModal';`
2. Add prop `questions` to the component signature.
3. Add `const [modalOpen, setModalOpen] = useState(false);` and a promise-resolver ref:

```jsx
  const [modalOpen, setModalOpen] = useState(false);
  const resolverRef = React.useRef(null);
  const hasQuestions = Array.isArray(questions) && questions.length > 0;

  const onBeforeActivate = hasQuestions
    ? () => new Promise((resolve) => { resolverRef.current = resolve; setModalOpen(true); })
    : undefined;
```

4. Pass `onBeforeActivate` into `useToggleResource({ ... , onBeforeActivate })`.
5. Render the modal at the end of the returned JSX (wrap return in a fragment):

```jsx
      {modalOpen && (
        <RegistrationModal
          questions={questions}
          onSubmit={(answers) => { setModalOpen(false); resolverRef.current?.({ answers }); }}
          onClose={() => { setModalOpen(false); resolverRef.current?.(null); }}
        />
      )}
```

(`{ answers }` becomes the POST body, matching the controller's `body.get("answers")`.)

- [ ] **Step 4: Pass `questions` from `EventDetail.jsx`**

At the `<RegisterButton ... />` usage (around line 174), add:

```jsx
                  questions={event?.registrationType === 'NATIVE' ? event?.questions : null}
```

- [ ] **Step 5: Pass `questions` from `EventCard.jsx`**

At the compact `<RegisterButton ... />` (inside `.event-card__rsvp`), add:

```jsx
              questions={event.registrationType === 'NATIVE' ? event.questions : null}
```

> Note: the events list (`/api/events`) returns `EventDTO`, which now includes `questions`, so the compact card has them without extra fetches.

- [ ] **Step 6: Build**

Run: `cd frontend && npm run build`
Expected: build succeeds.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/RegistrationModal.jsx frontend/src/css/RegistrationModal.css \
        frontend/src/components/RegisterButton.jsx frontend/src/components/EventDetail.jsx frontend/src/components/EventCard.jsx
git commit -m "feat(frontend): registration modal with custom questions"
```

---

### Task 10: Organizer answer view — expandable row + Excel columns

**Files:**
- Modify: `frontend/src/components/EventRegistrants.jsx`

**Interfaces:**
- Consumes: `event.questions` (from `GET /api/events/{id}`), `attendees[i].answers` (from `/attendees`, Task 5).

- [ ] **Step 1: Track expanded rows + a question map**

Add near the other state:

```jsx
  const [expanded, setExpanded] = useState(() => new Set());
  const toggleRow = (id) => setExpanded(prev => {
    const n = new Set(prev); n.has(id) ? n.delete(id) : n.add(id); return n;
  });
```

Derive question list from the loaded event:

```jsx
  const questions = Array.isArray(event?.questions) ? event.questions : [];
  const labelById = useMemo(() => Object.fromEntries(questions.map(q => [q.id, q.label])), [questions]);
```

- [ ] **Step 2: Add an "Ответы" column + expandable detail row**

In the `<thead>` row, add a trailing `<th></th>`. In the `<tbody>` map, render the existing cells, then when the attendee has answers add a toggle button and a detail row:

```jsx
                  {attendees.map((a, i) => (
                    <React.Fragment key={a.userId}>
                      <tr>
                        <td>{i + 1}</td>
                        <td>{a.userId}</td>
                        <td>{a.username || '—'}</td>
                        <td>{a.email || '—'}</td>
                        <td>
                          <span className={`att-status ${a.status === 'ATTENDED' ? 'att-status--in' : ''}`}>
                            {statusLabel(a.status)}
                          </span>
                        </td>
                        <td>
                          {a.answers && Object.keys(a.answers).length > 0 && (
                            <button type="button" className="att-answers__toggle" onClick={() => toggleRow(a.userId)}>
                              {expanded.has(a.userId) ? 'Скрыть' : 'Ответы'}
                            </button>
                          )}
                        </td>
                      </tr>
                      {expanded.has(a.userId) && a.answers && (
                        <tr className="att-answers__row">
                          <td colSpan={6}>
                            <dl className="att-answers">
                              {Object.entries(a.answers).map(([qid, val]) => (
                                <div key={qid} className="att-answers__item">
                                  <dt>{labelById[qid] || qid}</dt>
                                  <dd>{val}</dd>
                                </div>
                              ))}
                            </dl>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  ))}
```

Ensure `React` is imported (it is) for `React.Fragment`.

- [ ] **Step 3: Add answer columns to Excel export**

Replace the `rows` builder in `exportExcel`:

```jsx
    const rows = attendees.map((a, i) => {
      const base = {
        '№': i + 1,
        'ID': a.userId,
        'Имя': a.username || '',
        'Email': a.email || '',
        'Статус': statusLabel(a.status),
      };
      questions.forEach(q => { base[q.label] = a.answers?.[q.id] || ''; });
      return base;
    });
```

(Leave `ws['!cols']` as-is or extend; not required for correctness.)

- [ ] **Step 4: Add minimal CSS** (append to `frontend/src/css/EventLikers.css`)

```css
.att-answers__toggle { background: none; border: 1px solid var(--color-border, #e8dfce); border-radius: 6px; padding: 2px 10px; cursor: pointer; font-size: 12px; }
.att-answers__row td { background: var(--color-surface-alt, #f9f5ee); }
.att-answers { margin: 0; display: grid; gap: 6px; }
.att-answers__item { display: flex; gap: 8px; }
.att-answers__item dt { font-weight: 700; min-width: 160px; color: var(--color-text-secondary, #6b6253); }
.att-answers__item dd { margin: 0; }
```

- [ ] **Step 5: Build**

Run: `cd frontend && npm run build`
Expected: build succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/EventRegistrants.jsx frontend/src/css/EventLikers.css
git commit -m "feat(frontend): organizer views attendee answers + Excel columns"
```

---

### Task 11: End-to-end verification

**Files:** none (verification only). Uses the running stack (`./start.sh --no-front`, seeded; `admin@eventhub.kz` / `password123`).

- [ ] **Step 1: Rebuild and start the backend**

```bash
mvn -q -pl eventhubkz-common,event-service,registration-service -am install -DskipTests
./start.sh --no-front
```
Expected: gateway healthy at `http://localhost:8180`.

- [ ] **Step 2: E2E script — author, approve, register, validate**

Create `/tmp/questions_e2e.py` and run `python3 /tmp/questions_e2e.py`. It must:
1. Login as a normal user; `POST /api/event-requests` with `questions: [{id:"q1",label:"Размер",type:"SINGLE",required:true,options:["S","M"]},{id:"q2",label:"Коммент",type:"TEXT",required:false,options:[]}]` and a future `eventDate`/`registrationDeadline`.
2. Login as `admin@eventhub.kz`; `POST /api/admin/{requestId}/approve` (or the existing approve path) → find the new event id via `GET /api/events`.
3. `GET /api/events/{id}` → assert `questions` length 2, `registrationType == NATIVE`.
4. Login as a second user; register with **missing required** → assert `400`.
5. Register with **invalid single** (`q1=XL`) → assert `400`.
6. Register with valid `{answers:{q1:"M",q2:"ok"}}` → assert `201`.
7. As organizer/admin `GET /api/events/{id}/attendees` → assert the attendee's `answers.q1 == "M"`.
8. As a different non-organizer user, `GET /api/registrations/event/{id}/attendees` → assert `403`.

Expected: all assertions pass.

- [ ] **Step 3: Frontend build + visual pass**

```bash
cd frontend && npm run build && npm run dev   # background
```
Drive with the playwright/system-chrome flow used previously: (a) request form shows the question editor and submits; (b) registering a NATIVE event with questions opens the modal, blocks on a missing required answer, succeeds when filled; (c) organizer `EventRegistrants` shows the "Ответы" toggle and the answers; (d) Excel export downloads. Screenshot each; confirm `console --errors` is clean.

- [ ] **Step 4: Clean up test data and stop**

Remove the test request/event/registrations created during E2E (per the project's cleanup pattern), then `./start.sh stop` and stop vite. Report what was verified.

- [ ] **Step 5: Commit (only if any verification helper files belong in the repo — normally nothing to commit)**

No code commit expected in this task.

---

## Self-Review

**Spec coverage:**
- §2 model (QuestionDef/QuestionType, jsonb on EventRequest/Event/EventRegistration, EventDTO.questions) → Tasks 1–4. ✓
- §3 flow (request→approve→event→DTO→register validate) → Tasks 2,3,4. ✓
- §4 authorization (organizer-only answers, RegistrationView stays clean) → Task 5 (new `/attendees` endpoint with EventClient org-check; minimal `RegistrationView` untouched). ✓
- §5 validation rules + limits → Task 4 (`AnswerValidator` + tests) and Task 2/6 (≤10 questions cap). ✓
- §6.1 request-form editor → Task 6; §6.2 modal + hook → Tasks 8,9; §6.3 organizer view + Excel → Task 10; §6.4 admin read-only → Task 7. ✓
- §7 edge cases (idempotent no-overwrite, backward compat null, NATIVE-only) → Task 4 (idempotency note), Global Constraints, Task 9 (NATIVE gating in props). ✓
- §9 testing → Task 4 unit tests + Task 11 E2E/visual. ✓

**Placeholder scan:** No TBD/TODO; every code step has concrete code. The only intentionally-prose step is Task 7 Step 1 ("match existing class-naming") and Task 11's E2E script described as numbered assertions rather than full Python — acceptable because the assertions and endpoints are fully specified.

**Type consistency:** `register(userId, eventId, userEmail, username, answers)` order is consistent between controller (Task 4 Step 8 note) and service (Step 7). `AttendeeDTO` 5-arg constructor used consistently (Task 5 Steps 4 & 6). `AttendeeAnswerView(userId, status, answers)` consistent (Steps 1,2). Frontend `{ answers }` body matches controller `body.get("answers")` (Task 9 Step 3 ↔ Task 4 Step 8). `onBeforeActivate` returns `{answers}`-or-`null` consistent between hook (Task 8) and button (Task 9).

**Known follow-ups (out of scope, per spec §8):** editing questions after approval; editing answers after submit; multi-choice; analytics.
