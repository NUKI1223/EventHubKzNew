package org.ngcvfb.searchservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.EventCreatedEvent;
import org.ngcvfb.eventhubkz.common.events.EventDeletedEvent;
import org.ngcvfb.eventhubkz.common.events.EventUpdatedEvent;
import org.ngcvfb.searchservice.model.EventDocument;
import org.ngcvfb.searchservice.service.EventSearchService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventKafkaConsumer {

    private final EventSearchService eventSearchService;

    @KafkaListener(topics = "event.created", groupId = "search-service-group-v3")
    public void handleEventCreated(List<EventCreatedEvent> events) {
        List<EventDocument> docs = events.stream()
                .filter(Objects::nonNull)
                .map(this::toDocument)
                .toList();
        if (docs.isEmpty()) return;
        try {
            eventSearchService.saveAll(docs);
            log.info("Bulk indexed {} created events", docs.size());
        } catch (Exception e) {
            log.error("Bulk index failed for {} created events", docs.size(), e);
        }
    }

    @KafkaListener(topics = "event.updated", groupId = "search-service-group-v3")
    public void handleEventUpdated(List<EventUpdatedEvent> events) {
        events.stream().filter(Objects::nonNull).forEach(event -> {
            try {
                eventSearchService.findById(String.valueOf(event.getId())).ifPresent(existing -> {
                    existing.setTitle(event.getTitle());
                    existing.setShortDescription(event.getShortDescription());
                    existing.setFullDescription(event.getFullDescription());
                    existing.setTags(event.getTags());
                    existing.setLocation(event.getLocation());
                    existing.setOnline(event.isOnline());
                    existing.setEventDate(event.getEventDate());
                    existing.setMainImageUrl(event.getMainImageUrl());
                    eventSearchService.save(existing);
                });
            } catch (Exception e) {
                log.error("Failed to update event {} in index", event.getId(), e);
            }
        });
    }

    @KafkaListener(topics = "event.deleted", groupId = "search-service-group-v3")
    public void handleEventDeleted(List<EventDeletedEvent> events) {
        events.stream().filter(Objects::nonNull).forEach(event -> {
            try {
                eventSearchService.deleteById(String.valueOf(event.getId()));
            } catch (Exception e) {
                log.error("Failed to delete event {} from index", event.getId(), e);
            }
        });
    }

    private EventDocument toDocument(EventCreatedEvent event) {
        return EventDocument.builder()
                .id(String.valueOf(event.getId()))
                .title(event.getTitle())
                .shortDescription(event.getShortDescription())
                .fullDescription(event.getFullDescription())
                .tags(event.getTags())
                .location(event.getLocation())
                .online(event.isOnline())
                .eventDate(event.getEventDate())
                .mainImageUrl(event.getMainImageUrl())
                .organizerEmail(event.getOrganizerEmail())
                .organizerId(event.getOrganizerId())
                .build();
    }
}
