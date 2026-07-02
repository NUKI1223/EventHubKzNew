package org.ngcvfb.eventhubkz.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Публикуется event-service при подаче организатором заявки на мероприятие.
 * Потребитель — audit-service (REQUEST_CREATED).
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventRequestCreatedEvent extends BaseEvent {
    private Long requestId;
    private Long requesterId;
    private String requesterEmail;
    private String eventTitle;

    public static EventRequestCreatedEvent create(Long requestId, Long requesterId,
                                                  String requesterEmail, String eventTitle) {
        EventRequestCreatedEvent event = EventRequestCreatedEvent.builder()
                .requestId(requestId)
                .requesterId(requesterId)
                .requesterEmail(requesterEmail)
                .eventTitle(eventTitle)
                .build();
        event.initBase();
        return event;
    }
}
