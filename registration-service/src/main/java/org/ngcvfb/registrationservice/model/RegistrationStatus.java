package org.ngcvfb.registrationservice.model;

/**
 * Статус записи на мероприятие. В рамках ядра (Tier 1) используется только
 * {@code REGISTERED} — отмена удаляет запись. Остальные значения зарезервированы
 * под следующие этапы (лист ожидания, режим одобрения, чек-ин).
 */
public enum RegistrationStatus {
    REGISTERED,
    WAITLISTED,
    PENDING,
    CANCELLED,
    ATTENDED
}
