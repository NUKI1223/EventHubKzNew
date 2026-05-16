package org.ngcvfb.searchservice.config;

import org.springframework.data.elasticsearch.core.mapping.PropertyValueConverter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Supplier;

public class EventDateConverter implements PropertyValueConverter {

    @Override
    public Object write(Object value) {
        if (value instanceof LocalDateTime ldt) {
            return ldt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        return value;
    }

    @Override
    public Object read(Object value) {
        if (value == null) return null;
        String s = value.toString();
        return tryParse(() -> LocalDateTime.parse(s))
                .or(() -> tryParse(() -> LocalDate.parse(s).atStartOfDay()))
                .or(() -> tryParse(() -> LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(s)), ZoneOffset.UTC)))
                .orElseThrow(() -> new IllegalArgumentException("Cannot parse eventDate: " + s));
    }

    private static Optional<LocalDateTime> tryParse(Supplier<LocalDateTime> parser) {
        try {
            return Optional.of(parser.get());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
