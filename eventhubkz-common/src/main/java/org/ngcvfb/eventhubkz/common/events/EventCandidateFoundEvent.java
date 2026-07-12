package org.ngcvfb.eventhubkz.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Публикуется ingestion-service (Python) при извлечении события из поста Telegram.
 * Потребитель — event-service: создаёт EventRequest(AI_INGEST, PENDING) после дедупа.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventCandidateFoundEvent extends BaseEvent {
    private String title;
    private String shortDescription;
    private String fullDescription;
    private LocalDateTime eventDate;
    private String city;
    private String location;
    private boolean online;
    private Set<String> tags;
    private String externalLink;
    private String mainImageUrl;
    private String sourceUrl;
    private String sourceChannel;

    public static EventCandidateFoundEvent create(String title, String shortDescription,
            String fullDescription, LocalDateTime eventDate, String city, String location,
            boolean online, Set<String> tags, String externalLink, String sourceUrl,
            String sourceChannel) {
        EventCandidateFoundEvent e = EventCandidateFoundEvent.builder()
                .title(title).shortDescription(shortDescription).fullDescription(fullDescription)
                .eventDate(eventDate).city(city).location(location).online(online).tags(tags)
                .externalLink(externalLink).sourceUrl(sourceUrl).sourceChannel(sourceChannel)
                .build();
        e.initBase();
        return e;
    }
}
