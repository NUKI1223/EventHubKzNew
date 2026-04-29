package org.ngcvfb.searchservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Configuration
public class ElasticsearchConfig {

    @Bean
    public ElasticsearchCustomConversions elasticsearchCustomConversions() {
        return new ElasticsearchCustomConversions(List.of(new StringToLocalDateTimeConverter()));
    }

    @ReadingConverter
    static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {
        @Override
        public LocalDateTime convert(String source) {
            if (source == null || source.isBlank()) {
                return null;
            }
            try {
                return LocalDateTime.parse(source);
            } catch (Exception ignored) {
                try {
                    return LocalDate.parse(source, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
                } catch (Exception ignored2) {
                    return LocalDateTime.parse(source, DateTimeFormatter.ISO_DATE_TIME);
                }
            }
        }
    }
}
