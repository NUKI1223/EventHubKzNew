package org.ngcvfb.eventservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.model.RequestSource;
import org.ngcvfb.eventservice.model.RequestStatus;
import org.ngcvfb.eventservice.repository.EventRequestRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventRequestNotificationGuardTest {

    @Mock EventRequestRepository eventRequestRepository;
    @Mock EventKafkaProducer kafkaProducer;
    @Mock EventService eventService;
    @InjectMocks EventRequestService service;

    private EventRequest pending(Long requesterId, RequestSource source) {
        EventRequest r = EventRequest.builder()
                .id(1L).title("t").shortDescription("short desc here")
                .fullDescription("full description that is long enough")
                .location("Almaty").online(false).eventDate(LocalDateTime.now().plusDays(5))
                .status(RequestStatus.PENDING).source(source).requesterId(requesterId).build();
        when(eventRequestRepository.findById(1L)).thenReturn(Optional.of(r));
        when(eventRequestRepository.save(any(EventRequest.class))).thenAnswer(i -> i.getArgument(0));
        return r;
    }

    @Test
    void rejectingAiIngestRequestDoesNotNotify() {
        pending(null, RequestSource.AI_INGEST);
        service.rejectRequest(1L, 99L, "spam");
        verify(kafkaProducer, never()).sendEventRequestReviewed(any());
    }

    @Test
    void rejectingManualRequestNotifies() {
        pending(7L, RequestSource.MANUAL);
        service.rejectRequest(1L, 99L, "no");
        verify(kafkaProducer).sendEventRequestReviewed(any());
    }

    @Test
    void approvingAiIngestRequestDoesNotNotify() {
        pending(null, RequestSource.AI_INGEST);
        service.approveRequest(1L, 99L);
        verify(kafkaProducer, never()).sendEventRequestReviewed(any());
    }

    @Test
    void approvingManualRequestNotifies() {
        pending(7L, RequestSource.MANUAL);
        service.approveRequest(1L, 99L);
        verify(kafkaProducer).sendEventRequestReviewed(any());
    }
}
