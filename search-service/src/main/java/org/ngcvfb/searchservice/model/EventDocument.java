package org.ngcvfb.searchservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ngcvfb.searchservice.config.EventDateConverter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.ValueConverter;

import java.time.LocalDateTime;
import java.util.Set;

@Document(indexName = "events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "russian")
    private String title;

    @Field(type = FieldType.Text, analyzer = "russian")
    private String shortDescription;

    @Field(type = FieldType.Text, analyzer = "russian")
    private String fullDescription;

    @Field(type = FieldType.Keyword)
    private Set<String> tags;

    @Field(type = FieldType.Text)
    private String location;

    @Field(type = FieldType.Boolean)
    private boolean online;

    @Field(type = FieldType.Date, format = {DateFormat.date_optional_time, DateFormat.epoch_millis})
    @ValueConverter(EventDateConverter.class)
    private LocalDateTime eventDate;

    @Field(type = FieldType.Text)
    private String mainImageUrl;

    @Field(type = FieldType.Text)
    private String organizerEmail;

    @Field(type = FieldType.Long)
    private Long organizerId;
}
