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

@Component
@RequiredArgsConstructor
@Slf4j
public class EventKafkaConsumer {

    private final EventSearchService eventSearchService;

    @KafkaListener(topics = "event.created", groupId = "search-service-group-v3")
    public void handleEventCreated(EventCreatedEvent event) {
        if (event == null) { log.warn("Skipping null event.created message (deserialization error)"); return; }
        log.info("Received event.created: {}", event.getId());
        try {
            EventDocument document = EventDocument.builder()
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

            eventSearchService.save(document);
            log.info("Event indexed successfully: {}", event.getId());
        } catch (Exception e) {
            log.error("Failed to index event: {}", event.getId(), e);
        }
    }

    @KafkaListener(topics = "event.updated", groupId = "search-service-group-v3")
    public void handleEventUpdated(EventUpdatedEvent event) {
        if (event == null) { log.warn("Skipping null event.updated message"); return; }
        log.info("Received event.updated: {}", event.getId());
        try {
            eventSearchService.findById(String.valueOf(event.getId()))
                    .ifPresent(existing -> {
                        existing.setTitle(event.getTitle());
                        existing.setShortDescription(event.getShortDescription());
                        existing.setFullDescription(event.getFullDescription());
                        existing.setTags(event.getTags());
                        existing.setLocation(event.getLocation());
                        existing.setOnline(event.isOnline());
                        existing.setEventDate(event.getEventDate());
                        existing.setMainImageUrl(event.getMainImageUrl());

                        eventSearchService.save(existing);
                        log.info("Event updated in index: {}", event.getId());
                    });
        } catch (Exception e) {
            log.error("Failed to update event in index: {}", event.getId(), e);
        }
    }

    @KafkaListener(topics = "event.deleted", groupId = "search-service-group-v3")
    public void handleEventDeleted(EventDeletedEvent event) {
        if (event == null) { log.warn("Skipping null event.deleted message"); return; }
        log.info("Received event.deleted: {}", event.getId());
        try {
            eventSearchService.deleteById(String.valueOf(event.getId()));
            log.info("Event deleted from index: {}", event.getId());
        } catch (Exception e) {
            log.error("Failed to delete event from index: {}", event.getId(), e);
        }
    }
}
