package org.ngcvfb.auditservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ngcvfb.auditservice.model.AuditLog;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private Long id;
    private String action;
    private Long actorId;
    private String actorName;
    private String targetType;
    private Long targetId;
    private String targetLabel;
    private String details;
    private LocalDateTime occurredAt;

    public static AuditLogDTO from(AuditLog a) {
        return AuditLogDTO.builder()
                .id(a.getId())
                .action(a.getAction().name())
                .actorId(a.getActorId()).actorName(a.getActorName())
                .targetType(a.getTargetType() == null ? null : a.getTargetType().name())
                .targetId(a.getTargetId()).targetLabel(a.getTargetLabel())
                .details(a.getDetails())
                .occurredAt(a.getOccurredAt())
                .build();
    }
}
