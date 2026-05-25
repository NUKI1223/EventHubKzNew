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
public class EventRequestReviewedEvent extends BaseEvent {
    private Long requestId;
    private Long requesterId;
    private String requesterEmail;
    private String eventTitle;
    private boolean approved;
    private String adminComment;

    public static EventRequestReviewedEvent create(Long requestId, Long requesterId,
                                                    String requesterEmail, String eventTitle,
                                                    boolean approved, String adminComment) {
        EventRequestReviewedEvent event = EventRequestReviewedEvent.builder()
                .requestId(requestId)
                .requesterId(requesterId)
                .requesterEmail(requesterEmail)
                .eventTitle(eventTitle)
                .approved(approved)
                .adminComment(adminComment)
                .build();
        event.initBase();
        return event;
    }
}
