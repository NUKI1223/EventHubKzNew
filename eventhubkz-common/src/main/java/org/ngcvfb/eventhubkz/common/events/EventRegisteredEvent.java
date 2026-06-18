package org.ngcvfb.eventhubkz.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Публикуется registration-service, когда пользователь записывается на мероприятие.
 * Потребитель — notification-service: создаёт уведомление и шлёт письмо-подтверждение
 * участнику. Поле {@code registeredEventId} названо так, чтобы не конфликтовать
 * с {@link BaseEvent#getEventId()} (UUID самого сообщения).
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventRegisteredEvent extends BaseEvent {
    private Long registeredEventId;
    private Long userId;
    private String userEmail;
    private String username;
    private String eventTitle;
    private LocalDateTime eventDate;
    private Long organizerId;
    private String organizerEmail;
    // Данные билета: уникальный код, дата регистрации, место/формат — для письма-билета с QR.
    private String code;
    private LocalDateTime registeredAt;
    private String eventLocation;
    private boolean eventOnline;

    public static EventRegisteredEvent create(Long registeredEventId, Long userId, String userEmail,
                                              String username, String eventTitle, LocalDateTime eventDate,
                                              Long organizerId, String organizerEmail,
                                              String code, LocalDateTime registeredAt,
                                              String eventLocation, boolean eventOnline) {
        EventRegisteredEvent event = EventRegisteredEvent.builder()
                .registeredEventId(registeredEventId)
                .userId(userId)
                .userEmail(userEmail)
                .username(username)
                .eventTitle(eventTitle)
                .eventDate(eventDate)
                .organizerId(organizerId)
                .organizerEmail(organizerEmail)
                .code(code)
                .registeredAt(registeredAt)
                .eventLocation(eventLocation)
                .eventOnline(eventOnline)
                .build();
        event.initBase();
        return event;
    }
}
