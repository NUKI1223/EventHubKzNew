package org.ngcvfb.registrationservice.controller;

import lombok.RequiredArgsConstructor;
import org.ngcvfb.registrationservice.dto.RegistrationView;
import org.ngcvfb.registrationservice.model.EventRegistration;
import org.ngcvfb.registrationservice.service.EventRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final EventRegistrationService registrationService;

    @GetMapping("/event/{eventId}/count")
    public ResponseEntity<Long> getRegistrationCount(@PathVariable Long eventId) {
        return ResponseEntity.ok(registrationService.getRegistrationCount(eventId));
    }

    @GetMapping("/counts")
    public ResponseEntity<Map<Long, Long>> getCounts(@RequestParam List<Long> eventIds) {
        return ResponseEntity.ok(registrationService.getCounts(eventIds));
    }

    // Список записей события (без code — он секрет; владелец берёт свой через /mine).
    // Содержит только userId/status — тот же уровень раскрытия, что и список лайков.
    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<RegistrationView>> getRegistrationsByEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(registrationService.getRegistrationsByEvent(eventId));
    }

    /** Участники с ответами — только организатор/админ (проверка в сервисе через EventClient). */
    @GetMapping("/event/{eventId}/attendees")
    public ResponseEntity<List<org.ngcvfb.registrationservice.dto.AttendeeAnswerView>> attendeeAnswers(
            @PathVariable Long eventId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        return ResponseEntity.ok(registrationService.getAttendeeAnswers(eventId, userId, role));
    }

    // История записей конкретного пользователя — только сам пользователь или админ.
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<RegistrationView>> getRegistrationsByUser(
            @PathVariable Long userId,
            @RequestHeader("X-User-Id") Long requesterId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        requireSelfOrAdmin(userId, requesterId, role);
        return ResponseEntity.ok(registrationService.getRegistrationsByUser(userId));
    }

    @GetMapping("/user/{userId}/events")
    public ResponseEntity<List<Long>> getRegisteredEventIds(
            @PathVariable Long userId,
            @RequestHeader("X-User-Id") Long requesterId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        requireSelfOrAdmin(userId, requesterId, role);
        return ResponseEntity.ok(registrationService.getRegisteredEventIdsByUser(userId));
    }

    private void requireSelfOrAdmin(Long targetUserId, Long requesterId, String role) {
        if (!"ADMIN".equalsIgnoreCase(role) && !Objects.equals(targetUserId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Доступно только самому пользователю");
        }
    }

    @GetMapping("/check")
    public ResponseEntity<Boolean> isRegistered(
            @RequestParam Long userId,
            @RequestParam Long eventId) {
        return ResponseEntity.ok(registrationService.isRegistered(userId, eventId));
    }

    @PostMapping("/event/{eventId}")
    public ResponseEntity<EventRegistration> register(
            @PathVariable Long eventId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader(value = "X-Username", required = false) String username,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, String> answers = null;
        if (body != null && body.get("answers") instanceof Map<?, ?> raw) {
            answers = new java.util.HashMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                answers.put(String.valueOf(e.getKey()), e.getValue() == null ? null : String.valueOf(e.getValue()));
            }
        }
        EventRegistration registration = registrationService.register(userId, eventId, userEmail, username, answers);
        return ResponseEntity.status(HttpStatus.CREATED).body(registration);
    }

    @DeleteMapping("/event/{eventId}")
    public ResponseEntity<Void> cancel(
            @PathVariable Long eventId,
            @RequestHeader("X-User-Id") Long userId) {
        registrationService.cancel(userId, eventId);
        return ResponseEntity.noContent().build();
    }

    /** Запись текущего пользователя на событие (с кодом-билетом) — для построения QR. */
    @GetMapping("/event/{eventId}/mine")
    public ResponseEntity<EventRegistration> myRegistration(
            @PathVariable Long eventId,
            @RequestHeader("X-User-Id") Long userId) {
        EventRegistration reg = registrationService.getMyRegistration(userId, eventId);
        return reg == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(reg);
    }

    /** Отметка прихода по коду (только организатор события или админ). */
    @PostMapping("/checkin")
    public ResponseEntity<EventRegistration> checkIn(
            @RequestBody Map<String, String> body,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        String code = body == null ? null : body.get("code");
        return ResponseEntity.ok(registrationService.checkIn(code, userId, role));
    }
}
