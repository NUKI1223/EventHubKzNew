package org.ngcvfb.eventservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.ngcvfb.eventservice.client.RegistrationClient;
import org.ngcvfb.eventservice.client.UserClient;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.Event;
import org.ngcvfb.eventservice.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Кэш "events" должен инвалидироваться каждым мутатором счётчиков —
 * иначе API до истечения TTL отдаёт устаревший DTO (см. баг с viewsCount).
 */
@SpringJUnitConfig(EventServiceCacheTest.Cfg.class)
class EventServiceCacheTest {

    @Configuration
    @EnableCaching
    static class Cfg {
        @Bean CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("events", "eventsByTag");
        }
        @Bean EventRepository eventRepository() { return Mockito.mock(EventRepository.class); }
        @Bean EventKafkaProducer kafkaProducer() { return Mockito.mock(EventKafkaProducer.class); }
        @Bean RegistrationClient registrationClient() { return Mockito.mock(RegistrationClient.class); }
        @Bean UserClient userClient() { return Mockito.mock(UserClient.class); }
        @Bean EventService eventService(EventRepository repo, EventKafkaProducer kafka,
                                        RegistrationClient reg, UserClient user) {
            return new EventService(repo, kafka, reg, user);
        }
    }

    @Autowired EventService eventService;
    @Autowired EventRepository eventRepository;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("events").clear();
        Event event = Event.builder().id(6L).title("t").viewCount(0).likeCount(0).build();
        when(eventRepository.findById(6L)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void incrementViewCount_evictsCachedEvent() {
        eventService.getEventById(6L);
        assertThat(cacheManager.getCache("events").get(6L)).as("event cached after read").isNotNull();

        eventService.incrementViewCount(6L);

        assertThat(cacheManager.getCache("events").get(6L))
                .as("stale DTO must be evicted so the next read sees the new view count")
                .isNull();
    }

    @Test
    void incrementLikeCount_evictsCachedEvent() {
        eventService.getEventById(6L);
        eventService.incrementLikeCount(6L);
        assertThat(cacheManager.getCache("events").get(6L)).isNull();
    }
}
