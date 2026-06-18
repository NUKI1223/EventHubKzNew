package org.ngcvfb.registrationservice.dto;

import org.ngcvfb.registrationservice.model.EventRegistration;

import java.time.LocalDateTime;

/**
 * Безопасное представление записи для списков (организатору/публичный список участников).
 * Сознательно НЕ содержит {@code code}: код-билет — это секрет для отметки прихода,
 * он отдаётся только владельцу записи через {@code /event/{id}/mine}.
 */
public record RegistrationView(
        Long id,
        Long userId,
        Long eventId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime checkedInAt
) {
    public static RegistrationView from(EventRegistration r) {
        return new RegistrationView(
                r.getId(),
                r.getUserId(),
                r.getEventId(),
                r.getStatus() == null ? null : r.getStatus().name(),
                r.getCreatedAt(),
                r.getCheckedInAt()
        );
    }
}
