package org.ngcvfb.eventhubkz.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventUpdatedEvent extends BaseEvent {
    private Long id;
    private String title;
    private String shortDescription;
    private String fullDescription;
    private Set<String> tags;
    private String location;
    private boolean online;
    private LocalDateTime eventDate;
    private String mainImageUrl;

    public static EventUpdatedEvent create(Long id, String title, String shortDescription,
                                           String fullDescription, Set<String> tags, String location,
                                           boolean online, LocalDateTime eventDate, String mainImageUrl) {
        EventUpdatedEvent event = EventUpdatedEvent.builder()
                .id(id)
                .title(title)
                .shortDescription(shortDescription)
                .fullDescription(fullDescription)
                .tags(tags)
                .location(location)
                .online(online)
                .eventDate(eventDate)
                .mainImageUrl(mainImageUrl)
                .build();
        event.initBase();
        return event;
    }
}
