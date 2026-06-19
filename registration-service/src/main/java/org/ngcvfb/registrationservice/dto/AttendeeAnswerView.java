package org.ngcvfb.registrationservice.dto;

import org.ngcvfb.registrationservice.model.EventRegistration;

import java.util.Map;

/** Участник + ответы — отдаётся ТОЛЬКО организатору события/админу. */
public record AttendeeAnswerView(Long userId, String status, Map<String, String> answers) {
    public static AttendeeAnswerView from(EventRegistration r) {
        return new AttendeeAnswerView(
                r.getUserId(),
                r.getStatus() == null ? "REGISTERED" : r.getStatus().name(),
                r.getAnswers());
    }
}
