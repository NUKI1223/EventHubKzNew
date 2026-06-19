package org.ngcvfb.registrationservice.service;

import org.junit.jupiter.api.Test;
import org.ngcvfb.eventhubkz.common.dto.QuestionDef;
import org.ngcvfb.eventhubkz.common.dto.QuestionType;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnswerValidatorTest {

    private final AnswerValidator validator = new AnswerValidator();

    private List<QuestionDef> questions() {
        return List.of(
                new QuestionDef("q1", "Размер футболки", QuestionType.SINGLE, true, List.of("S", "M", "L")),
                new QuestionDef("q2", "Комментарий", QuestionType.TEXT, false, null));
    }

    @Test
    void passesWithValidAnswers() {
        Map<String, String> cleaned = validator.validateAndClean(questions(),
                Map.of("q1", "M", "q2", "ничего"));
        assertEquals("M", cleaned.get("q1"));
        assertEquals("ничего", cleaned.get("q2"));
    }

    @Test
    void rejectsMissingRequired() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> validator.validateAndClean(questions(), Map.of("q2", "hi")));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void rejectsSingleValueOutsideOptions() {
        assertThrows(ResponseStatusException.class,
                () -> validator.validateAndClean(questions(), Map.of("q1", "XXL")));
    }

    @Test
    void dropsUnknownKeys() {
        Map<String, String> cleaned = validator.validateAndClean(questions(),
                Map.of("q1", "S", "qZ", "junk"));
        assertFalse(cleaned.containsKey("qZ"));
    }

    @Test
    void emptyQuestionsYieldsEmptyMap() {
        assertTrue(validator.validateAndClean(null, Map.of("q1", "x")).isEmpty());
        assertTrue(validator.validateAndClean(List.of(), null).isEmpty());
    }

    @Test
    void rejectsTooLongTextAnswer() {
        assertThrows(ResponseStatusException.class,
                () -> validator.validateAndClean(questions(),
                        Map.of("q1", "S", "q2", "x".repeat(1001))));
    }
}
