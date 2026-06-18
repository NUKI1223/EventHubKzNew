package org.ngcvfb.eventservice.dto;

/**
 * Участник мероприятия для организатора: данные, нужные для связи и выгрузки.
 * Возвращается только организатору события или администратору.
 */
public record AttendeeDTO(Long userId, String username, String email, String status) {
}
