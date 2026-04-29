package org.ngcvfb.eventhubkz.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventLikedEvent extends BaseEvent {
    private Long likedEventId;
    private Long userId;
    private String userEmail;
    private String username;
    private Long organizerId;
    private String organizerEmail;
    private String eventTitle;

    public static EventLikedEvent create(Long likedEventId, Long userId, String userEmail,
                                         String username, Long organizerId, String organizerEmail,
                                         String eventTitle) {
        EventLikedEvent event = EventLikedEvent.builder()
                .likedEventId(likedEventId)
                .userId(userId)
                .userEmail(userEmail)
                .username(username)
                .organizerId(organizerId)
                .organizerEmail(organizerEmail)
                .eventTitle(eventTitle)
                .build();
        event.initBase();
        return event;
    }
}
