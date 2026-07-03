package org.ngcvfb.auditservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditLog;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.ngcvfb.auditservice.repository.AuditLogRepository;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditRecordServiceTest {

    @Mock AuditLogRepository repository;
    @InjectMocks AuditRecordService service;

    @Test
    void recordsNewEntry() {
        when(repository.existsByDedupKey("user.registered:0:5")).thenReturn(false);

        service.record(AuditAction.USER_REGISTERED, 1L, "aidar",
                AuditTargetType.USER, 1L, "aidar", null,
                LocalDateTime.now(), "user.registered:0:5");

        verify(repository).save(any(AuditLog.class));
    }

    @Test
    void skipsDuplicateDelivery() {
        when(repository.existsByDedupKey("user.registered:0:5")).thenReturn(true);

        service.record(AuditAction.USER_REGISTERED, 1L, "aidar",
                AuditTargetType.USER, 1L, "aidar", null,
                LocalDateTime.now(), "user.registered:0:5");

        verify(repository, never()).save(any());
    }
}
