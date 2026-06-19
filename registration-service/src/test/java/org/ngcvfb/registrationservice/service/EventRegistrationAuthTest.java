package org.ngcvfb.registrationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.eventhubkz.common.dto.EventDTO;
import org.ngcvfb.registrationservice.client.EventClient;
import org.ngcvfb.registrationservice.kafka.RegistrationKafkaProducer;
import org.ngcvfb.registrationservice.model.EventRegistration;
import org.ngcvfb.registrationservice.model.RegistrationStatus;
import org.ngcvfb.registrationservice.repository.EventRegistrationRepository;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventRegistrationAuthTest {

    @Mock EventRegistrationRepository registrationRepository;
    @Mock RegistrationKafkaProducer kafkaProducer;
    @Mock EventClient eventClient;
    @Mock AnswerValidator answerValidator;

    @InjectMocks EventRegistrationService service;

    private EventDTO eventWith(long organizerId, Set<Long> staff) {
        EventDTO dto = new EventDTO();
        dto.setId(1L);
        dto.setOrganizerId(organizerId);
        dto.setStaffIds(staff);
        lenient().when(eventClient.getEventById(1L)).thenReturn(dto);
        return dto;
    }

    @Test
    void checkIn_byStaff_isAllowed() {
        eventWith(10L, Set.of(7L));
        EventRegistration reg = EventRegistration.builder()
                .userId(3L).eventId(1L).status(RegistrationStatus.REGISTERED).code("ABC123").build();
        when(registrationRepository.findByCode("ABC123")).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(EventRegistration.class))).thenAnswer(i -> i.getArgument(0));

        EventRegistration result = service.checkIn("ABC123", 7L, "USER");

        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.ATTENDED);
    }

    @Test
    void checkIn_byStranger_isForbidden() {
        eventWith(10L, Set.of(7L));
        EventRegistration reg = EventRegistration.builder()
                .userId(3L).eventId(1L).status(RegistrationStatus.REGISTERED).code("ABC123").build();
        when(registrationRepository.findByCode("ABC123")).thenReturn(Optional.of(reg));

        assertThatThrownBy(() -> service.checkIn("ABC123", 55L, "USER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void getAttendeeAnswers_byStaff_isAllowed() {
        eventWith(10L, Set.of(7L));
        lenient().when(registrationRepository.findByEventId(1L)).thenReturn(List.of());

        assertThat(service.getAttendeeAnswers(1L, 7L, "USER")).isEmpty();
    }

    @Test
    void getAttendeeAnswers_byStranger_isForbidden() {
        eventWith(10L, Set.of(7L));
        assertThatThrownBy(() -> service.getAttendeeAnswers(1L, 55L, "USER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }
}
