package org.ngcvfb.searchservice.config;

import org.springframework.data.elasticsearch.core.mapping.PropertyValueConverter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

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
        try {
            return LocalDateTime.parse(s);
        } catch (Exception ignored) {
            try {
                return LocalDate.parse(s).atStartOfDay();
            } catch (Exception ignored2) {
                try {
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(s)), ZoneOffset.UTC);
                } catch (Exception finalEx) {
                    throw new IllegalArgumentException("Cannot parse eventDate: " + s);
                }
            }
        }
    }
}
