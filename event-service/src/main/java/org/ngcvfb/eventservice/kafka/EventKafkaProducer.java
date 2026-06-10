package org.ngcvfb.eventservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.EventCreatedEvent;
import org.ngcvfb.eventhubkz.common.events.EventDeletedEvent;
import org.ngcvfb.eventhubkz.common.events.EventReminderEvent;
import org.ngcvfb.eventhubkz.common.events.EventRequestReviewedEvent;
import org.ngcvfb.eventhubkz.common.events.EventUpdatedEvent;
import org.ngcvfb.eventhubkz.common.events.SupportMessageResolvedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventKafkaProducer {

    private static final String TOPIC_EVENT_CREATED = "event.created";
    private static final String TOPIC_EVENT_UPDATED = "event.updated";
    private static final String TOPIC_EVENT_DELETED = "event.deleted";
    private static final String TOPIC_EVENT_REQUEST_REVIEWED = "event-request.reviewed";
    private static final String TOPIC_SUPPORT_RESOLVED = "support.resolved";
    private static final String TOPIC_EVENT_REMINDER = "event.reminder";

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

    public void sendEventRequestReviewed(EventRequestReviewedEvent event) {
        log.info("Sending event-request.reviewed: request={}, approved={}", event.getRequestId(), event.isApproved());
        kafkaTemplate.send(TOPIC_EVENT_REQUEST_REVIEWED, String.valueOf(event.getRequestId()), event);
    }

    public void sendSupportResolved(SupportMessageResolvedEvent event) {
        log.info("Sending support.resolved: messageId={}, userId={}", event.getMessageId(), event.getUserId());
        kafkaTemplate.send(TOPIC_SUPPORT_RESOLVED, String.valueOf(event.getMessageId()), event);
    }

    public void sendEventReminder(EventReminderEvent event) {
        log.info("Sending event.reminder: eventId={}, userId={}", event.getRelatedEventId(), event.getUserId());
        kafkaTemplate.send(TOPIC_EVENT_REMINDER, String.valueOf(event.getRelatedEventId()), event);
    }
}
