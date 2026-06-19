package org.ngcvfb.registrationservice.service;

import org.ngcvfb.eventhubkz.common.dto.QuestionDef;
import org.ngcvfb.eventhubkz.common.dto.QuestionType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Проверяет ответы участника против вопросов мероприятия и возвращает только валидные. */
@Component
public class AnswerValidator {

    private static final int MAX_TEXT = 1000;

    public Map<String, String> validateAndClean(List<QuestionDef> questions, Map<String, String> answers) {
        Map<String, String> cleaned = new LinkedHashMap<>();
        if (questions == null || questions.isEmpty()) {
            return cleaned;
        }
        Map<String, String> given = answers == null ? Map.of() : answers;

        for (QuestionDef q : questions) {
            String raw = given.get(q.id());
            String value = raw == null ? null : raw.trim();
            boolean empty = value == null || value.isEmpty();

            if (empty) {
                if (q.required()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Ответьте на обязательный вопрос: " + q.label());
                }
                continue;
            }
            if (value.length() > MAX_TEXT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Слишком длинный ответ на вопрос: " + q.label());
            }
            if (q.type() == QuestionType.SINGLE) {
                List<String> opts = q.options() == null ? List.of() : q.options();
                if (!opts.contains(value)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Недопустимый вариант ответа на вопрос: " + q.label());
                }
            }
            cleaned.put(q.id(), value);
        }
        return cleaned;
    }
}
