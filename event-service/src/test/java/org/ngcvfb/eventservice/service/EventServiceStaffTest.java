package org.ngcvfb.eventservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.eventservice.client.RegistrationClient;
import org.ngcvfb.eventservice.client.UserClient;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.Event;
import org.ngcvfb.eventservice.repository.EventRepository;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EventServiceStaffTest {

    // EventService constructor fields: eventRepository, kafkaProducer,
    // registrationClient, userClient (no LikeClient). The test only drives the repo.
    @Mock EventRepository eventRepository;
    @Mock EventKafkaProducer kafkaProducer;
    @Mock RegistrationClient registrationClient;
    @Mock UserClient userClient;

    @InjectMocks EventService eventService;

    private Event eventOwnedBy(long organizerId) {
        Event e = Event.builder().id(1L).title("t").organizerId(organizerId)
                .staffIds(new HashSet<>()).build();
        lenient().when(eventRepository.findById(1L)).thenReturn(Optional.of(e));
        lenient().when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
        return e;
    }

    @Test
    void addStaff_byOrganizer_addsUser() {
        Event e = eventOwnedBy(10L);
        eventService.addStaff(1L, 7L, 10L, "USER");
        assertThat(e.getStaffIds()).contains(7L);
    }

    @Test
    void addStaff_byAdmin_addsUser() {
        Event e = eventOwnedBy(10L);
        eventService.addStaff(1L, 7L, 999L, "ADMIN");
        assertThat(e.getStaffIds()).contains(7L);
    }

    @Test
    void addStaff_byStranger_isForbidden() {
        eventOwnedBy(10L);
        assertThatThrownBy(() -> eventService.addStaff(1L, 7L, 55L, "USER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void removeStaff_byOrganizer_removesUser() {
        Event e = eventOwnedBy(10L);
        e.getStaffIds().add(7L);
        eventService.removeStaff(1L, 7L, 10L, "USER");
        assertThat(e.getStaffIds()).doesNotContain(7L);
    }
}
