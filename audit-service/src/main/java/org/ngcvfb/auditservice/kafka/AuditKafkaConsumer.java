package org.ngcvfb.auditservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.ngcvfb.auditservice.service.AuditRecordService;
import org.ngcvfb.eventhubkz.common.events.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditKafkaConsumer {

    private static final String GROUP = "audit-service-group";

    private final AuditRecordService recordService;

    private static String dedup(String topic, int partition, long offset) {
        return topic + ":" + partition + ":" + offset;
    }

    @KafkaListener(topics = "user.registered", groupId = GROUP)
    public void onUserRegistered(@Payload UserRegisteredEvent e,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset) {
        recordService.record(AuditAction.USER_REGISTERED, e.getUserId(), e.getUsername(),
                AuditTargetType.USER, e.getUserId(), e.getUsername(),
                null, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "user.deleted", groupId = GROUP)
    public void onUserDeleted(@Payload UserDeletedEvent e,
                              @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                              @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                              @Header(KafkaHeaders.OFFSET) long offset) {
        String details = "email=" + e.getEmail() + "; reason=" + e.getReason();
        recordService.record(AuditAction.USER_DELETED, e.getDeletedBy(), null,
                AuditTargetType.USER, e.getUserId(), e.getUsername(),
                details, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "event.created", groupId = GROUP)
    public void onEventCreated(@Payload EventCreatedEvent e,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                               @Header(KafkaHeaders.OFFSET) long offset) {
        recordService.record(AuditAction.EVENT_CREATED, e.getOrganizerId(), e.getOrganizerEmail(),
                AuditTargetType.EVENT, e.getId(), e.getTitle(),
                null, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "event.updated", groupId = GROUP)
    public void onEventUpdated(@Payload EventUpdatedEvent e,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                               @Header(KafkaHeaders.OFFSET) long offset) {
        recordService.record(AuditAction.EVENT_UPDATED, null, null,
                AuditTargetType.EVENT, e.getId(), e.getTitle(),
                null, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "event.deleted", groupId = GROUP)
    public void onEventDeleted(@Payload EventDeletedEvent e,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                               @Header(KafkaHeaders.OFFSET) long offset) {
        recordService.record(AuditAction.EVENT_DELETED, null, null,
                AuditTargetType.EVENT, e.getId(), "event #" + e.getId(),
                null, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "event.liked", groupId = GROUP)
    public void onEventLiked(@Payload EventLikedEvent e,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                             @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                             @Header(KafkaHeaders.OFFSET) long offset) {
        recordService.record(AuditAction.EVENT_LIKED, e.getUserId(), e.getUsername(),
                AuditTargetType.EVENT, e.getLikedEventId(), e.getEventTitle(),
                null, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "event.registered", groupId = GROUP)
    public void onEventRegistered(@Payload EventRegisteredEvent e,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset) {
        recordService.record(AuditAction.EVENT_RSVP, e.getUserId(), e.getUsername(),
                AuditTargetType.EVENT, e.getRegisteredEventId(), e.getEventTitle(),
                null, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "event-request.created", groupId = GROUP)
    public void onRequestCreated(@Payload EventRequestCreatedEvent e,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset) {
        recordService.record(AuditAction.REQUEST_CREATED, e.getRequesterId(), e.getRequesterEmail(),
                AuditTargetType.REQUEST, e.getRequestId(), e.getEventTitle(),
                null, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "event-request.reviewed", groupId = GROUP)
    public void onRequestReviewed(@Payload EventRequestReviewedEvent e,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset) {
        AuditAction action = e.isApproved() ? AuditAction.REQUEST_APPROVED
                                            : AuditAction.REQUEST_REJECTED;
        recordService.record(action, null, null,
                AuditTargetType.REQUEST, e.getRequestId(), e.getEventTitle(),
                e.getAdminComment(), e.getTimestamp(), dedup(topic, partition, offset));
    }
}
