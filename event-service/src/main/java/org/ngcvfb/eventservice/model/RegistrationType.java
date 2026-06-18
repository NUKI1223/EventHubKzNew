package org.ngcvfb.eventservice.model;

/**
 * Способ регистрации на мероприятие:
 * <ul>
 *   <li>{@code NATIVE} — запись прямо на сайте (registration-service);</li>
 *   <li>{@code EXTERNAL} — регистрация по внешней ссылке организатора;</li>
 *   <li>{@code NONE} — регистрация не требуется.</li>
 * </ul>
 */
public enum RegistrationType {
    NATIVE,
    EXTERNAL,
    NONE
}
