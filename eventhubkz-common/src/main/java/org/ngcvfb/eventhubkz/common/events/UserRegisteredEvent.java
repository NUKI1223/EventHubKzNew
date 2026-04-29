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
public class UserRegisteredEvent extends BaseEvent {
    private Long userId;
    private String username;
    private String email;

    public static UserRegisteredEvent create(Long userId, String username, String email) {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(userId)
                .username(username)
                .email(email)
                .build();
        event.initBase();
        return event;
    }
}
