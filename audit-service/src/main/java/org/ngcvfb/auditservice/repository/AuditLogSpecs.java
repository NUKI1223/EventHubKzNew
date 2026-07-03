package org.ngcvfb.auditservice.repository;

import jakarta.persistence.criteria.Predicate;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditLog;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class AuditLogSpecs {

    private AuditLogSpecs() {}

    public static Specification<AuditLog> filter(AuditAction action, Long actorId,
                                                 AuditTargetType targetType,
                                                 LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null)     predicates.add(cb.equal(root.get("action"), action));
            if (actorId != null)    predicates.add(cb.equal(root.get("actorId"), actorId));
            if (targetType != null) predicates.add(cb.equal(root.get("targetType"), targetType));
            if (from != null)       predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null)         predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
