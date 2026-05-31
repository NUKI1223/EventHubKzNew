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
public class SupportMessageResolvedEvent extends BaseEvent {
    private Long messageId;
    private Long userId;
    private String userEmail;
    private String originalMessage;
    private String adminReply;

    public static SupportMessageResolvedEvent create(Long messageId, Long userId,
                                                      String userEmail, String originalMessage,
                                                      String adminReply) {
        SupportMessageResolvedEvent event = SupportMessageResolvedEvent.builder()
                .messageId(messageId)
                .userId(userId)
                .userEmail(userEmail)
                .originalMessage(originalMessage)
                .adminReply(adminReply)
                .build();
        event.initBase();
        return event;
    }
}
