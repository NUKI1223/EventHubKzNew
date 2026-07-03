package org.ngcvfb.eventservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.eventhubkz.common.events.EventRequestCreatedEvent;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.repository.EventRequestRepository;

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

        verify(kafkaProducer).sendEventRequestCreated(any(EventRequestCreatedEvent.class));
    }
}
