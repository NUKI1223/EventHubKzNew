package org.ngcvfb.eventhubkz.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventReminderEvent extends BaseEvent {
    private Long userId;
    private String userEmail;
    private Long relatedEventId;
    private String eventTitle;
    private LocalDateTime eventDate;

    public static EventReminderEvent create(Long userId, String userEmail, Long relatedEventId,
                                            String eventTitle, LocalDateTime eventDate) {
        EventReminderEvent event = EventReminderEvent.builder()
                .userId(userId)
                .userEmail(userEmail)
                .relatedEventId(relatedEventId)
                .eventTitle(eventTitle)
                .eventDate(eventDate)
                .build();
        event.initBase();
        return event;
    }
}
