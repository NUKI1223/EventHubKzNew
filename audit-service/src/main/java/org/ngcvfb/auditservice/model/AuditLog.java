package org.ngcvfb.auditservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Одна строка аудита. Строки никогда не изменяются и не удаляются —
 * в том числе записи об удалённых пользователях.
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_occurred_at", columnList = "occurredAt"),
        @Index(name = "idx_audit_actor", columnList = "actorId"),
        @Index(name = "idx_audit_action", columnList = "action")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;

    private Long actorId;
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private AuditTargetType targetType;

    private Long targetId;
    private String targetLabel;

    @Column(length = 2000)
    private String details;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    // topic:partition:offset — защита от повторной доставки Kafka
    @Column(nullable = false, unique = true, length = 128)
    private String dedupKey;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
