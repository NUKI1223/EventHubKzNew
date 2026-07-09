package org.ngcvfb.eventservice.model;

import org.junit.jupiter.api.Test;
import org.ngcvfb.eventservice.PostgresTestcontainer;
import org.ngcvfb.eventservice.repository.EventRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class EventRequestSourceTest {

    @Autowired EventRequestRepository repo;

    @Test
    void persistsAiIngestRequestWithNullRequester() {
        EventRequest r = EventRequest.builder()
                .title("Almaty Go Meetup")
                .shortDescription("Go community meetup with two talks")
                .fullDescription("A meetup for the Go community in Almaty, two talks and networking.")
                .location("Almaty, SmartPoint")
                .online(false)
                .eventDate(LocalDateTime.now().plusDays(10))
                .tags(Set.of("backend"))
                .source(RequestSource.AI_INGEST)
                .sourceUrl("https://t.me/kzdev/1234")
                .sourceChannel("kzdev")
                .requesterId(null)
                .build();

        EventRequest saved = repo.save(r);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRequesterId()).isNull();
        assertThat(saved.getSource()).isEqualTo(RequestSource.AI_INGEST);
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.PENDING); // @PrePersist default
    }
}
