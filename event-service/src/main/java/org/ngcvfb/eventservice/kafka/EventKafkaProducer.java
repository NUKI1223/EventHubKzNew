package org.ngcvfb.eventservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.EventCreatedEvent;
import org.ngcvfb.eventhubkz.common.events.EventDeletedEvent;
import org.ngcvfb.eventhubkz.common.events.EventUpdatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventKafkaProducer {

    private static final String TOPIC_EVENT_CREATED = "event.created";
    private static final String TOPIC_EVENT_UPDATED = "event.updated";
    private static final String TOPIC_EVENT_DELETED = "event.deleted";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendEventCreated(EventCreatedEvent event) {
        log.info("Sending event.created: {}", event.getId());
        kafkaTemplate.send(TOPIC_EVENT_CREATED, String.valueOf(event.getId()), event);
    }

    public void sendEventUpdated(EventUpdatedEvent event) {
        log.info("Sending event.updated: {}", event.getId());
        kafkaTemplate.send(TOPIC_EVENT_UPDATED, String.valueOf(event.getId()), event);
    }

    public void sendEventDeleted(EventDeletedEvent event) {
        log.info("Sending event.deleted: {}", event.getId());
        kafkaTemplate.send(TOPIC_EVENT_DELETED, String.valueOf(event.getId()), event);
    }
}
