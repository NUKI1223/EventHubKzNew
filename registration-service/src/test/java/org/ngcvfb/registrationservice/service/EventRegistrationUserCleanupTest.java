package org.ngcvfb.registrationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.registrationservice.client.EventClient;
import org.ngcvfb.registrationservice.kafka.RegistrationKafkaProducer;
import org.ngcvfb.registrationservice.model.EventRegistration;
import org.ngcvfb.registrationservice.repository.EventRegistrationRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventRegistrationUserCleanupTest {

    @Mock EventRegistrationRepository registrationRepository;
    @Mock RegistrationKafkaProducer kafkaProducer;
    @Mock EventClient eventClient;
    @Mock AnswerValidator answerValidator;
    @Mock CacheManager cacheManager;
    @Mock Cache cache;

    @InjectMocks EventRegistrationService service;

    @Test
    void deletesRegistrationsAndEvictsCounters() {
        EventRegistration r1 = new EventRegistration(); r1.setUserId(7L); r1.setEventId(10L);
        when(registrationRepository.findByUserId(7L)).thenReturn(List.of(r1));
        when(cacheManager.getCache("registrationCount")).thenReturn(cache);

        service.deleteAllRegistrationsForUser(7L);

        verify(registrationRepository).deleteByUserId(7L);
        verify(cache).evict(10L);
    }
}
