package org.ngcvfb.auditservice.repository;

import org.junit.jupiter.api.Test;
import org.ngcvfb.auditservice.PostgresTestcontainer;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditLog;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// Same setup as event-service's *IT classes: PostgresTestcontainer is an
// ApplicationContextInitializer, NOT a base class — wire it via initializers.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class AuditLogRepositoryIT {

    @Autowired AuditLogRepository repository;

    private AuditLog row(AuditAction action, Long actorId, LocalDateTime at, String dedup) {
        return AuditLog.builder()
                .action(action).actorId(actorId).actorName("u" + actorId)
                .targetType(AuditTargetType.EVENT).targetId(1L).targetLabel("Event #1")
                .occurredAt(at).dedupKey(dedup)
                .build();
    }

    @Test
    void filtersByActionActorAndDateRange() {
        LocalDateTime now = LocalDateTime.now();
        repository.save(row(AuditAction.EVENT_LIKED, 1L, now.minusDays(2), "t:0:1"));
        repository.save(row(AuditAction.EVENT_LIKED, 2L, now, "t:0:2"));
        repository.save(row(AuditAction.USER_REGISTERED, 1L, now, "t:0:3"));

        var page = repository.findAll(
                AuditLogSpecs.filter(AuditAction.EVENT_LIKED, 2L, null, now.minusDays(1), null),
                PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getDedupKey()).isEqualTo("t:0:2");
    }

    @Test
    void existsByDedupKeyDetectsDuplicates() {
        repository.save(row(AuditAction.EVENT_CREATED, 1L, LocalDateTime.now(), "topic:0:42"));
        assertThat(repository.existsByDedupKey("topic:0:42")).isTrue();
        assertThat(repository.existsByDedupKey("topic:0:43")).isFalse();
    }
}
