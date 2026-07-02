package org.ngcvfb.eventhubkz.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Публикуется user-service при жёстком удалении пользователя (админом или самим
 * пользователем). Потребители чистят свои данные; audit-service сохраняет запись навсегда.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserDeletedEvent extends BaseEvent {
    private Long userId;
    private String username;
    private String email;
    private Long deletedBy;
    private String reason;

    public static UserDeletedEvent create(Long userId, String username, String email,
                                          Long deletedBy, String reason) {
        UserDeletedEvent event = UserDeletedEvent.builder()
                .userId(userId)
                .username(username)
                .email(email)
                .deletedBy(deletedBy)
                .reason(reason)
                .build();
        event.initBase();
        return event;
    }
}
