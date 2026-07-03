package org.ngcvfb.eventservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.InjectMocks;
import org.ngcvfb.eventservice.client.RegistrationClient;
import org.ngcvfb.eventservice.client.UserClient;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.Event;
import org.ngcvfb.eventservice.repository.EventRepository;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceUserCascadeTest {

    @Mock EventRepository eventRepository;
    @Mock EventKafkaProducer kafkaProducer;
    @Mock RegistrationClient registrationClient;
    @Mock UserClient userClient;

    @InjectMocks @Spy EventService eventService;

    @Test
    void deletesAllOrganizerEventsViaExistingFlow() {
        Event e1 = Event.builder().id(10L).organizerId(7L).build();
        Event e2 = Event.builder().id(11L).organizerId(7L).build();
        when(eventRepository.findByOrganizerId(7L)).thenReturn(List.of(e1, e2));
        doNothing().when(eventService).deleteEvent(anyLong(), anyLong(), anyString());

        eventService.deleteAllForUser(7L);

        verify(eventService).deleteEvent(10L, 7L, "ADMIN");
        verify(eventService).deleteEvent(11L, 7L, "ADMIN");
    }
}
