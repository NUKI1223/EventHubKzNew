package org.ngcvfb.notificationservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.EventCreatedEvent;
import org.ngcvfb.eventhubkz.common.events.EventLikedEvent;
import org.ngcvfb.eventhubkz.common.events.EventRegisteredEvent;
import org.ngcvfb.eventhubkz.common.events.EventReminderEvent;
import org.ngcvfb.eventhubkz.common.events.EventRequestReviewedEvent;
import org.ngcvfb.eventhubkz.common.events.SupportMessageResolvedEvent;
import org.ngcvfb.eventhubkz.common.events.UserDeletedEvent;
import org.ngcvfb.eventhubkz.common.events.UserRegisteredEvent;
import org.ngcvfb.notificationservice.model.NotificationType;
import org.ngcvfb.notificationservice.service.NotificationService;
import org.ngcvfb.notificationservice.service.SupportEmailService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;
    private final SupportEmailService supportEmailService;

    @KafkaListener(topics = "event.created", groupId = "notification-service-group")
    public void handleEventCreated(EventCreatedEvent event) {
        log.info("Received event.created: {}", event.getId());
        try {
            notificationService.createIfAbsent(
                    event.getOrganizerId(),
                    event.getOrganizerEmail(),
                    "Событие создано",
                    String.format("Ваше событие \"%s\" успешно создано и опубликовано.", event.getTitle()),
                    NotificationType.EVENT_CREATED,
                    event.getId()
            );
        } catch (Exception e) {
            log.error("Failed to create notification for event: {}", event.getId(), e);
        }
    }

    @KafkaListener(topics = "event.liked", groupId = "notification-service-group")
    public void handleEventLiked(EventLikedEvent event) {
        log.info("Received event.liked: eventId={}, userId={}", event.getLikedEventId(), event.getUserId());
        try {
            // Notify event organizer about the like
            if (event.getOrganizerId() != null && !event.getOrganizerId().equals(event.getUserId())) {
                String username = event.getUsername() != null ? event.getUsername() : "Пользователь";
                notificationService.createNotification(
                        event.getOrganizerId(),
                        event.getOrganizerEmail(),
                        "Новый лайк",
                        String.format("%s понравилось ваше событие \"%s\"", username, event.getEventTitle()),
                        NotificationType.EVENT_LIKED,
                        event.getLikedEventId()
                );
                log.info("Created like notification for organizer: {}", event.getOrganizerId());
            }
        } catch (Exception e) {
            log.error("Failed to create like notification: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "event-request.reviewed", groupId = "notification-service-group")
    public void handleEventRequestReviewed(EventRequestReviewedEvent event) {
        log.info("Received event-request.reviewed: request={}, approved={}", event.getRequestId(), event.isApproved());
        try {
            String title;
            String message;
            NotificationType type;
            if (event.isApproved()) {
                title = "Заявка одобрена";
                message = String.format(
                        "Ваша заявка на мероприятие \"%s\" одобрена. Событие опубликовано.",
                        event.getEventTitle());
                type = NotificationType.EVENT_REQUEST_APPROVED;
            } else {
                title = "Заявка отклонена";
                message = String.format(
                        "Ваша заявка на мероприятие \"%s\" отклонена.", event.getEventTitle());
                if (event.getAdminComment() != null && !event.getAdminComment().isBlank()) {
                    message += " Причина: " + truncate(event.getAdminComment(), 120);
                }
                type = NotificationType.EVENT_REQUEST_REJECTED;
            }
            notificationService.createIfAbsent(
                    event.getRequesterId(),
                    event.getRequesterEmail(),
                    title,
                    message,
                    type,
                    event.getRequestId()
            );
            if (!event.isApproved()) {
                supportEmailService.sendRequestRejected(
                        event.getRequesterEmail(),
                        event.getEventTitle(),
                        event.getAdminComment()
                );
            }
        } catch (Exception e) {
            log.error("Failed to create review notification: {}", e.getMessage(), e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1).trim() + "…";
    }

    @KafkaListener(topics = "support.resolved", groupId = "notification-service-group")
    public void handleSupportResolved(SupportMessageResolvedEvent event) {
        log.info("Received support.resolved: messageId={}, userId={}", event.getMessageId(), event.getUserId());
        if (event.getUserId() == null) {
            log.warn("support.resolved event has no userId; skipping notification");
            return;
        }
        try {
            String reply = event.getAdminReply() == null || event.getAdminReply().isBlank()
                    ? "(без комментария)"
                    : event.getAdminReply();
            String message = "Поддержка ответила на ваш вопрос: " + reply;
            notificationService.createIfAbsent(
                    event.getUserId(),
                    event.getUserEmail(),
                    "Ответ службы поддержки",
                    message,
                    NotificationType.SUPPORT_RESOLVED,
                    event.getMessageId()
            );
            supportEmailService.sendSupportReply(
                    event.getUserEmail(),
                    event.getOriginalMessage(),
                    event.getAdminReply()
            );
        } catch (Exception e) {
            log.error("Failed to create support notification: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "event.reminder", groupId = "notification-service-group")
    public void handleEventReminder(EventReminderEvent event) {
        log.info("Received event.reminder: eventId={}, userId={}", event.getRelatedEventId(), event.getUserId());
        if (event.getUserId() == null) {
            log.warn("event.reminder has no userId; skipping");
            return;
        }
        try {
            // createIfAbsent дедуплицирует по (userId, type, eventId): письмо отправляем
            // только при первом создании, чтобы повторные прогоны планировщика не спамили.
            var created = notificationService.createIfAbsent(
                    event.getUserId(),
                    event.getUserEmail(),
                    "Скоро мероприятие",
                    String.format("Напоминаем: мероприятие \"%s\" уже скоро. Не пропустите!", event.getEventTitle()),
                    NotificationType.EVENT_REMINDER,
                    event.getRelatedEventId()
            );
            if (created != null) {
                supportEmailService.sendEventReminder(
                        event.getUserEmail(),
                        event.getEventTitle(),
                        event.getEventDate()
                );
            }
        } catch (Exception e) {
            log.error("Failed to create reminder notification: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "event.registered", groupId = "notification-service-group")
    public void handleEventRegistered(EventRegisteredEvent event) {
        log.info("Received event.registered: eventId={}, userId={}", event.getRegisteredEventId(), event.getUserId());
        if (event.getUserId() == null) {
            log.warn("event.registered has no userId; skipping");
            return;
        }
        try {
            // createIfAbsent дедуплицирует по (userId, type, eventId): письмо-подтверждение
            // уходит только при первом создании, чтобы переотправки/реплеи не спамили.
            var created = notificationService.createIfAbsent(
                    event.getUserId(),
                    event.getUserEmail(),
                    "Вы записаны на мероприятие",
                    String.format("Вы успешно записались на мероприятие \"%s\". Ждём вас!", event.getEventTitle()),
                    NotificationType.REGISTRATION_CONFIRMED,
                    event.getRegisteredEventId()
            );
            if (created != null) {
                supportEmailService.sendRegistrationConfirmed(
                        event.getUserEmail(),
                        event.getUsername(),
                        event.getEventTitle(),
                        event.getEventDate(),
                        event.getRegisteredAt(),
                        event.getCode(),
                        event.getRegisteredEventId(),
                        event.getEventLocation(),
                        event.isEventOnline()
                );
            }
        } catch (Exception e) {
            log.error("Failed to create registration notification: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "user.registered", groupId = "notification-service-group")
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received user.registered: {}", event.getUserId());
        try {
            notificationService.createIfAbsent(
                    event.getUserId(),
                    event.getEmail(),
                    "Добро пожаловать в EventHub!",
                    "Спасибо за регистрацию! Теперь вы можете находить IT-мероприятия и создавать свои события.",
                    NotificationType.USER_REGISTERED,
                    null
            );
        } catch (Exception e) {
            log.error("Failed to create welcome notification: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "user.deleted", groupId = "notification-service-group")
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Received user.deleted: {}", event.getUserId());
        try {
            notificationService.deleteAllForUser(event.getUserId());
        } catch (Exception e) {
            log.error("Failed to delete notifications for user: {}", event.getUserId(), e);
        }
    }
}
