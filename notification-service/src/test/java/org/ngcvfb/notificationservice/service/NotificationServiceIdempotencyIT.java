package org.ngcvfb.notificationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ngcvfb.notificationservice.PostgresTestcontainer;
import org.ngcvfb.notificationservice.model.Notification;
import org.ngcvfb.notificationservice.model.NotificationType;
import org.ngcvfb.notificationservice.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the idempotent createIfAbsent flow added to dedupe
 * notifications when Kafka replays detinistic events (commit f464886).
 * Uses a real Postgres via Testcontainers so the existsBy* derived queries
 * exercise the actual database.
 *
 * Uses @DataJpaTest so the slice does not pull in KafkaAutoConfiguration or
 * EurekaClientAutoConfiguration. NotificationService is registered through
 * a targeted @ComponentScan filter.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ContextConfiguration(initializers = PostgresTestcontainer.class)
@ComponentScan(
        basePackageClasses = NotificationService.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = NotificationService.class))
class NotificationServiceIdempotencyIT {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void createIfAbsent_skipsDuplicateOnSecondCall() {
        Long userId = 42L;
        Long eventId = 7L;

        Notification first = notificationService.createIfAbsent(
                userId, "user@example.kz", "Событие создано",
                "Ваше событие опубликовано.",
                NotificationType.EVENT_CREATED, eventId);
        Notification second = notificationService.createIfAbsent(
                userId, "user@example.kz", "Событие создано",
                "Ваше событие опубликовано.",
                NotificationType.EVENT_CREATED, eventId);

        assertThat(first).isNotNull();
        assertThat(second)
                .as("Replay of the same (user, type, relatedId) must be skipped")
                .isNull();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void createIfAbsent_differentRelatedEventIdProducesDistinctRows() {
        Long userId = 42L;

        notificationService.createIfAbsent(userId, "a@b.kz", "1", "1",
                NotificationType.EVENT_CREATED, 1L);
        notificationService.createIfAbsent(userId, "a@b.kz", "2", "2",
                NotificationType.EVENT_CREATED, 2L);

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void createIfAbsent_userRegisteredIsDedupedByUserAndType() {
        Long userId = 99L;

        Notification first = notificationService.createIfAbsent(
                userId, "new@example.kz", "Добро пожаловать",
                "Спасибо за регистрацию.",
                NotificationType.USER_REGISTERED, null);
        Notification second = notificationService.createIfAbsent(
                userId, "new@example.kz", "Добро пожаловать",
                "Спасибо за регистрацию.",
                NotificationType.USER_REGISTERED, null);

        assertThat(first).isNotNull();
        assertThat(second).isNull();
        assertThat(repository.count()).isEqualTo(1);
    }
}
