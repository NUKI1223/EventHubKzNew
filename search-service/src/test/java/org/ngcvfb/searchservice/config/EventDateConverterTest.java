package org.ngcvfb.searchservice.config;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EventDateConverterTest {

    private final EventDateConverter converter = new EventDateConverter();

    @Test
    void read_isoLocalDateTime_returnsParsedDateTime() {
        Object result = converter.read("2026-05-20T14:30:00");

        assertEquals(LocalDateTime.of(2026, 5, 20, 14, 30), result);
    }

    @Test
    void read_dateOnly_fallsBackToAtStartOfDay() {
        Object result = converter.read("2026-05-20");

        assertEquals(LocalDateTime.of(2026, 5, 20, 0, 0), result);
    }

    @Test
    void read_epochMillis_returnsUtcDateTime() {
        long epochMillis = Instant.parse("2026-05-20T00:00:00Z").toEpochMilli();

        Object result = converter.read(String.valueOf(epochMillis));

        assertEquals(LocalDateTime.of(2026, 5, 20, 0, 0), result);
    }

    @Test
    void read_null_returnsNull() {
        assertNull(converter.read(null));
    }

    @Test
    void read_garbage_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> converter.read("not-a-date"));
        assertTrue(ex.getMessage().contains("Cannot parse eventDate"));
    }

    @Test
    void write_localDateTime_formatsAsIso() {
        Object result = converter.write(LocalDateTime.of(2026, 5, 20, 14, 30));

        assertEquals("2026-05-20T14:30:00", result);
    }

    @Test
    void write_otherType_passesThrough() {
        Object passthrough = "some-string";

        assertEquals(passthrough, converter.write(passthrough));
    }
}
