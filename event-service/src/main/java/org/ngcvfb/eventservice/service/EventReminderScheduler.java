package org.ngcvfb.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.dto.UserDTO;
import org.ngcvfb.eventhubkz.common.events.EventReminderEvent;
import org.ngcvfb.eventservice.client.LikeClient;
import org.ngcvfb.eventservice.client.RegistrationClient;
import org.ngcvfb.eventservice.client.UserClient;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.Event;
import org.ngcvfb.eventservice.repository.EventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Периодически рассылает напоминания о приближающихся мероприятиях.
 * <p>
 * Аудитория — объединение записавшихся (registration-service) и лайкнувших
 * (like-service); email берётся из user-service. Каждое срабатывание отправляет
 * события в Kafka-топик {@code event.reminder}; дедупликация на стороне
 * notification-service ({@code createIfAbsent}) гарантирует, что пользователь
 * получит ровно одно напоминание на событие, даже если он и записан, и лайкнул,
 * и даже при повторных прогонах планировщика.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventReminderScheduler {

    private final EventRepository eventRepository;
    private final LikeClient likeClient;
    private final RegistrationClient registrationClient;
    private final UserClient userClient;
    private final EventKafkaProducer kafkaProducer;

    @Value("${reminder.window-hours:24}")
    private long windowHours;

    @Scheduled(cron = "${reminder.cron:0 */10 * * * *}")
    public void sendUpcomingEventReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plusHours(windowHours);
        List<Event> upcoming = eventRepository.findByEventDateBetween(now, until);
        if (upcoming.isEmpty()) {
            return;
        }
        log.info("Reminder scan: {} event(s) starting within {}h", upcoming.size(), windowHours);

        for (Event event : upcoming) {
            try {
                remindForEvent(event);
            } catch (Exception e) {
                log.error("Failed to send reminders for event {}: {}", event.getId(), e.getMessage());
            }
        }
    }

    private void remindForEvent(Event event) {
        // Объединяем записавшихся и лайкнувших; дубли уберёт Set, а пересечение
        // аудиторий дополнительно гасит createIfAbsent в notification-service.
        Set<Long> userIds = new LinkedHashSet<>();
        userIds.addAll(extractUserIds(safeRegistrations(event.getId())));
        userIds.addAll(extractUserIds(safeLikes(event.getId())));
        if (userIds.isEmpty()) {
            return;
        }

        List<Long> ids = List.copyOf(userIds);
        Map<Long, String> emailById = userClient.getUsersByIds(ids).stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(UserDTO::getId, UserDTO::getEmail, (a, b) -> a));

        for (Long userId : userIds) {
            kafkaProducer.sendEventReminder(EventReminderEvent.create(
                    userId,
                    emailById.get(userId),
                    event.getId(),
                    event.getTitle(),
                    event.getEventDate()
            ));
        }
        log.info("Queued {} reminder(s) for event {} (\"{}\")", userIds.size(), event.getId(), event.getTitle());
    }

    private List<Long> extractUserIds(List<Map<String, Object>> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .map(r -> r.get("userId"))
                .filter(Objects::nonNull)
                .map(v -> Long.valueOf(v.toString()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> safeRegistrations(Long eventId) {
        try {
            return registrationClient.getRegistrationsByEvent(eventId);
        } catch (Exception e) {
            log.warn("Could not fetch registrations for event {}: {}", eventId, e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> safeLikes(Long eventId) {
        try {
            return likeClient.getLikesByEvent(eventId);
        } catch (Exception e) {
            log.warn("Could not fetch likes for event {}: {}", eventId, e.getMessage());
            return List.of();
        }
    }
}
