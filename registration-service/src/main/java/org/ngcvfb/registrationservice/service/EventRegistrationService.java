package org.ngcvfb.registrationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.dto.EventDTO;
import org.ngcvfb.eventhubkz.common.events.EventRegisteredEvent;
import org.ngcvfb.registrationservice.client.EventClient;
import org.ngcvfb.registrationservice.dto.RegistrationView;
import org.ngcvfb.registrationservice.kafka.RegistrationKafkaProducer;
import org.ngcvfb.registrationservice.model.EventRegistration;
import org.ngcvfb.registrationservice.model.RegistrationStatus;
import org.ngcvfb.registrationservice.repository.EventRegistrationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventRegistrationService {

    private final EventRegistrationRepository registrationRepository;
    private final RegistrationKafkaProducer kafkaProducer;
    private final EventClient eventClient;
    private final AnswerValidator answerValidator;

    public boolean isRegistered(Long userId, Long eventId) {
        return registrationRepository.existsByUserIdAndEventId(userId, eventId);
    }

    public long getRegistrationCount(Long eventId) {
        return registrationRepository.countByEventId(eventId);
    }

    /** Счётчики записей для набора событий одним запросом (для списка): eventId -> count. */
    public java.util.Map<Long, Long> getCounts(List<Long> eventIds) {
        java.util.Map<Long, Long> result = new java.util.HashMap<>();
        if (eventIds == null || eventIds.isEmpty()) return result;
        for (Object[] row : registrationRepository.countsByEventIds(eventIds)) {
            result.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return result;
    }

    public List<Long> getRegisteredEventIdsByUser(Long userId) {
        return registrationRepository.findEventIdsByUserId(userId);
    }

    public List<RegistrationView> getRegistrationsByEvent(Long eventId) {
        return registrationRepository.findByEventId(eventId).stream()
                .map(RegistrationView::from)
                .toList();
    }

    public List<RegistrationView> getRegistrationsByUser(Long userId) {
        return registrationRepository.findByUserId(userId).stream()
                .map(RegistrationView::from)
                .toList();
    }

    /** Запись текущего пользователя на событие (для построения QR-билета). */
    public EventRegistration getMyRegistration(Long userId, Long eventId) {
        return registrationRepository.findByUserIdAndEventId(userId, eventId).orElse(null);
    }

    /** Участники события с ответами — только организатор события или админ. */
    public List<org.ngcvfb.registrationservice.dto.AttendeeAnswerView> getAttendeeAnswers(
            Long eventId, Long requesterId, String role) {
        EventDTO event = fetchEvent(eventId);
        if (!canManageAttendees(event, requesterId, role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ответы участников доступны только организатору или сотруднику мероприятия");
        }
        return registrationRepository.findByEventId(eventId).stream()
                .map(org.ngcvfb.registrationservice.dto.AttendeeAnswerView::from)
                .toList();
    }

    private boolean canManageAttendees(EventDTO event, Long requesterId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) return true;
        if (Objects.equals(event.getOrganizerId(), requesterId)) return true;
        return event.getStaffIds() != null && event.getStaffIds().contains(requesterId);
    }

    public EventRegistration register(Long userId, Long eventId, String userEmail, String username,
                                      Map<String, String> answers) {

        EventRegistration existing = registrationRepository.findByUserIdAndEventId(userId, eventId).orElse(null);
        if (existing != null) {
            log.info("User {} already registered for event {}", userId, eventId);
            return existing;
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

        EventRegistration saved;
        try {
            saved = registrationRepository.save(registration);
        } catch (DataIntegrityViolationException e) {
            EventRegistration concurrent = registrationRepository
                    .findByUserIdAndEventId(userId, eventId).orElse(null);
            if (concurrent != null) {
                log.info("Concurrent registration detected for user {} event {} — returning existing",
                        userId, eventId);
                return concurrent;
            }
            throw e;
        }
        log.info("User {} registered for event {}", userId, eventId);

        kafkaProducer.sendEventRegistered(EventRegisteredEvent.create(
                eventId,
                userId,
                userEmail,
                username,
                event.getTitle(),
                event.getEventDate(),
                event.getOrganizerId(),
                event.getOrganizerEmail(),
                saved.getCode(),
                saved.getCreatedAt(),
                event.getLocation(),
                event.isOnline()
        ));

        return saved;
    }

    /**
     * Отметка участника как пришедшего по коду-билету. Доступно только организатору
     * события (или администратору). Идемпотентно: повторный ввод кода не ломает статус.
     */
    @Transactional
    public EventRegistration checkIn(String code, Long requesterId, String role) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Код не указан");
        }
        EventRegistration reg = registrationRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Код не найден"));

        EventDTO event = fetchEvent(reg.getEventId());
        if (!canManageAttendees(event, requesterId, role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Отмечать участников может только организатор или сотрудник мероприятия");
        }

        if (reg.getStatus() == RegistrationStatus.ATTENDED) {
            return reg; // уже отмечен — возвращаем как есть
        }
        reg.setStatus(RegistrationStatus.ATTENDED);
        reg.setCheckedInAt(LocalDateTime.now());
        EventRegistration saved = registrationRepository.save(reg);
        log.info("Checked in user {} for event {} (code {})", reg.getUserId(), reg.getEventId(), code);
        return saved;
    }

    @Transactional
    public void cancel(Long userId, Long eventId) {
        registrationRepository.deleteByUserIdAndEventId(userId, eventId);
        log.info("User {} cancelled registration for event {}", userId, eventId);
    }

    @Transactional
    public void deleteAllRegistrationsForEvent(Long eventId) {
        registrationRepository.deleteByEventId(eventId);
        log.info("Deleted all registrations for event {}", eventId);
    }


    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!registrationRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Не удалось сгенерировать код билета, попробуйте ещё раз");
    }

    private EventDTO fetchEvent(Long eventId) {
        try {
            EventDTO event = eventClient.getEventById(eventId);
            if (event == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Мероприятие не найдено");
            }
            return event;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch event {} for registration: {}", eventId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Не удалось проверить мероприятие, попробуйте позже");
        }
    }

    private void validateRegistrationOpen(EventDTO event) {
        // Запись доступна только для мероприятий с нативной регистрацией.
        String type = event.getRegistrationType();
        if (type != null && !"NATIVE".equalsIgnoreCase(type)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Для этого мероприятия запись на сайте недоступна");
        }
        // Дедлайн: если задан — он, иначе дата самого мероприятия.
        LocalDateTime deadline = event.getRegistrationDeadline() != null
                ? event.getRegistrationDeadline()
                : event.getEventDate();
        if (deadline != null && LocalDateTime.now().isAfter(deadline)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Регистрация закрыта");
        }
    }
}
