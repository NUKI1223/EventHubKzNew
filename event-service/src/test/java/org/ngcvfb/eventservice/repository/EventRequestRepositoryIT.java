package org.ngcvfb.eventservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ngcvfb.eventservice.PostgresTestcontainer;
import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.model.RequestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test exercising EventRequestRepository against a real Postgres
 * spun up via Testcontainers. Covers the sort-newest-first contract that was
 * fixed in commit 7449111 (admin lists).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class EventRequestRepositoryIT {

    @Autowired
    private EventRequestRepository repository;

    @Autowired
    private TestEntityManager em;

    private EventRequest make(String title, RequestStatus status,
                              LocalDateTime createdAt, long requesterId) {
        EventRequest r = EventRequest.builder()
                .title(title)
                .shortDescription("Короткое описание мероприятия " + title)
                .fullDescription("Полное описание мероприятия " + title
                        + " содержит достаточное количество текста для валидации.")
                .location("Алматы")
                .online(false)
                .eventDate(createdAt.plusYears(1))
                .requesterId(requesterId)
                .requesterEmail("user" + requesterId + "@example.kz")
                .build();
        EventRequest saved = em.persistAndFlush(r);
        // EventRequest.@PrePersist forces status=PENDING and createdAt=now();
        // override both back to the deterministic values the test needs.
        saved.setStatus(status);
        saved.setCreatedAt(createdAt);
        em.merge(saved);
        em.flush();
        em.clear();
        return saved;
    }

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void findAllSortDesc_returnsNewestFirst() {
        LocalDateTime base = LocalDateTime.of(2026, 3, 1, 10, 0);
        make("old",    RequestStatus.PENDING, base,              1);
        make("middle", RequestStatus.PENDING, base.plusDays(5),  2);
        make("newest", RequestStatus.PENDING, base.plusDays(10), 3);

        List<EventRequest> all = repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        assertThat(all).hasSize(3);
        assertThat(all.get(0).getTitle()).isEqualTo("newest");
        assertThat(all.get(1).getTitle()).isEqualTo("middle");
        assertThat(all.get(2).getTitle()).isEqualTo("old");
    }

    @Test
    void findByRequesterIdOrderByCreatedAtDesc_filtersAndSorts() {
        LocalDateTime base = LocalDateTime.of(2026, 4, 1, 10, 0);
        make("user1-old", RequestStatus.PENDING, base,              1);
        make("user1-new", RequestStatus.PENDING, base.plusDays(7),  1);
        make("user2-only", RequestStatus.PENDING, base.plusDays(3), 2);

        List<EventRequest> mine = repository.findByRequesterIdOrderByCreatedAtDesc(1L);

        assertThat(mine).extracting(EventRequest::getTitle)
                .containsExactly("user1-new", "user1-old");
    }

    @Test
    void findByStatus_paginatesPendingFirst() {
        LocalDateTime base = LocalDateTime.of(2026, 5, 1, 10, 0);
        make("approved-1", RequestStatus.APPROVED, base,              1);
        make("pending-1",  RequestStatus.PENDING,  base.plusDays(1),  2);
        make("pending-2",  RequestStatus.PENDING,  base.plusDays(2),  3);
        make("rejected",   RequestStatus.REJECTED, base.plusDays(3),  4);

        var page = repository.findByStatus(RequestStatus.PENDING,
                PageRequest.of(0, 10,
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(EventRequest::getTitle)
                .containsExactly("pending-2", "pending-1");
    }
}
