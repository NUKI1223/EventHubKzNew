package org.ngcvfb.eventservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.eventhubkz.common.events.EventRequestCreatedEvent;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.repository.EventRequestRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventRequestServiceCreateTest {

    @Mock EventRequestRepository eventRequestRepository;
    @Mock EventService eventService;
    @Mock EventKafkaProducer kafkaProducer;

    @InjectMocks EventRequestService service;

    @Test
    void createRequestPublishesAuditEvent() {
        EventRequest req = new EventRequest();
        req.setTitle("Meetup");
        req.setRequesterId(1L);
        req.setRequesterEmail("a@kz");
        req.setRegistrationType("NATIVE");
        when(eventRequestRepository.save(any(EventRequest.class))).thenAnswer(inv -> {
            EventRequest saved = inv.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        service.createRequest(req);

        ArgumentCaptor<EventRequestCreatedEvent> eventCaptor = ArgumentCaptor.forClass(EventRequestCreatedEvent.class);
        verify(kafkaProducer).sendEventRequestCreated(eventCaptor.capture());

        EventRequestCreatedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getRequestId()).isEqualTo(42L);
        assertThat(capturedEvent.getRequesterId()).isEqualTo(1L);
        assertThat(capturedEvent.getRequesterEmail()).isEqualTo("a@kz");
        assertThat(capturedEvent.getEventTitle()).isEqualTo("Meetup");
    }
}
