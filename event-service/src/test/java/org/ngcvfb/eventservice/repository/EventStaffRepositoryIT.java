package org.ngcvfb.eventservice.repository;

import org.junit.jupiter.api.Test;
import org.ngcvfb.eventservice.PostgresTestcontainer;
import org.ngcvfb.eventservice.model.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Same setup as EventRequestRepositoryIT: PostgresTestcontainer is an
// ApplicationContextInitializer, NOT a base class — wire it via initializers.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class EventStaffRepositoryIT {

    @Autowired
    EventRepository repository;

    private Event newEvent(String title, LocalDateTime date, Set<Long> staff) {
        Event e = Event.builder()
                .title(title).shortDescription("short").fullDescription("full description here")
                .location("Almaty").online(false).eventDate(date)
                .organizerId(1L).organizerEmail("org@example.kz")
                .staffIds(staff)
                .build();
        return repository.save(e);
    }

    @Test
    void findUpcomingStaffedBy_returnsOnlyFutureEventsWhereUserIsStaff() {
        LocalDateTime now = LocalDateTime.now();
        newEvent("future-staffed", now.plusDays(5), Set.of(7L));
        newEvent("past-staffed", now.minusDays(5), Set.of(7L));
        newEvent("future-other-staff", now.plusDays(5), Set.of(9L));

        List<Event> result = repository.findUpcomingStaffedBy(7L, now);

        assertThat(result).extracting(Event::getTitle).containsExactly("future-staffed");
    }
}
