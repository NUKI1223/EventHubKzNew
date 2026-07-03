package org.ngcvfb.auditservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditLog;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.ngcvfb.auditservice.repository.AuditLogRepository;
import org.ngcvfb.auditservice.repository.AuditLogSpecs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditRecordService {

    private final AuditLogRepository repository;

    @Transactional
    public void record(AuditAction action, Long actorId, String actorName,
                       AuditTargetType targetType, Long targetId, String targetLabel,
                       String details, LocalDateTime occurredAt, String dedupKey) {
        if (repository.existsByDedupKey(dedupKey)) {
            log.info("Skipped duplicate audit record: {}", dedupKey);
            return;
        }
        repository.save(AuditLog.builder()
                .action(action)
                .actorId(actorId).actorName(actorName)
                .targetType(targetType).targetId(targetId).targetLabel(targetLabel)
                .details(details)
                .occurredAt(occurredAt != null ? occurredAt : LocalDateTime.now())
                .dedupKey(dedupKey)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(AuditAction action, Long actorId, AuditTargetType targetType,
                                 LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return repository.findAll(
                AuditLogSpecs.filter(action, actorId, targetType, from, to), pageable);
    }
}
