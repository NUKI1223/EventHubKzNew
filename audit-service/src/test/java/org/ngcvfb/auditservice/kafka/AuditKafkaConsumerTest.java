package org.ngcvfb.auditservice.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.ngcvfb.auditservice.service.AuditRecordService;
import org.ngcvfb.eventhubkz.common.events.EventLikedEvent;
import org.ngcvfb.eventhubkz.common.events.EventRequestReviewedEvent;
import org.ngcvfb.eventhubkz.common.events.UserDeletedEvent;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditKafkaConsumerTest {

    @Mock AuditRecordService recordService;
    @InjectMocks AuditKafkaConsumer consumer;

    @Test
    void mapsEventLiked() {
        EventLikedEvent e = EventLikedEvent.create(6L, 1L, "a@kz", "aidar", 2L, "o@kz", "DataFest");

        consumer.onEventLiked(e, "event.liked", 0, 17L);

        verify(recordService).record(eq(AuditAction.EVENT_LIKED), eq(1L), eq("aidar"),
                eq(AuditTargetType.EVENT), eq(6L), eq("DataFest"),
                isNull(), any(), eq("event.liked:0:17"));
    }

    @Test
    void mapsRequestReviewedApprovedAndRejected() {
        EventRequestReviewedEvent approved =
                EventRequestReviewedEvent.create(5L, 1L, "a@kz", "Meetup", true, null);
        EventRequestReviewedEvent rejected =
                EventRequestReviewedEvent.create(6L, 1L, "a@kz", "Spam", false, "спам");

        consumer.onRequestReviewed(approved, "event-request.reviewed", 0, 1L);
        consumer.onRequestReviewed(rejected, "event-request.reviewed", 0, 2L);

        verify(recordService).record(eq(AuditAction.REQUEST_APPROVED), isNull(), isNull(),
                eq(AuditTargetType.REQUEST), eq(5L), eq("Meetup"),
                isNull(), any(), eq("event-request.reviewed:0:1"));
        verify(recordService).record(eq(AuditAction.REQUEST_REJECTED), isNull(), isNull(),
                eq(AuditTargetType.REQUEST), eq(6L), eq("Spam"),
                eq("спам"), any(), eq("event-request.reviewed:0:2"));
    }

    @Test
    void mapsUserDeletedWithSnapshotInDetails() {
        UserDeletedEvent e = UserDeletedEvent.create(7L, "spammer", "s@kz", 2L, "спам-события");

        consumer.onUserDeleted(e, "user.deleted", 0, 3L);

        verify(recordService).record(eq(AuditAction.USER_DELETED), eq(2L), isNull(),
                eq(AuditTargetType.USER), eq(7L), eq("spammer"),
                eq("email=s@kz; reason=спам-события"), any(), eq("user.deleted:0:3"));
    }
}
