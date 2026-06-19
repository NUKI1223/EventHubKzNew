package org.ngcvfb.eventhubkz.common.dto;

import java.util.List;

/** Определение кастомного вопроса регистрации. options непуст только для SINGLE. */
public record QuestionDef(
        String id,
        String label,
        QuestionType type,
        boolean required,
        List<String> options
) {}
