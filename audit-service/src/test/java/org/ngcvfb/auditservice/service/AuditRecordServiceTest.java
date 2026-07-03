package org.ngcvfb.auditservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditLog;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.ngcvfb.auditservice.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditRecordServiceTest {

    @Mock AuditLogRepository repository;
    @InjectMocks AuditRecordService service;

    @Test
    void recordsNewEntry() {
        when(repository.existsByDedupKey("user.registered:0:5")).thenReturn(false);

        LocalDateTime now = LocalDateTime.now();
        service.record(AuditAction.USER_REGISTERED, 1L, "aidar",
                AuditTargetType.USER, 1L, "aidar", null,
                now, "user.registered:0:5");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());

        AuditLog captured = captor.getValue();
        assertThat(captured.getAction()).isEqualTo(AuditAction.USER_REGISTERED);
        assertThat(captured.getActorId()).isEqualTo(1L);
        assertThat(captured.getActorName()).isEqualTo("aidar");
        assertThat(captured.getTargetType()).isEqualTo(AuditTargetType.USER);
        assertThat(captured.getTargetId()).isEqualTo(1L);
        assertThat(captured.getTargetLabel()).isEqualTo("aidar");
        assertThat(captured.getDetails()).isNull();
        assertThat(captured.getOccurredAt()).isEqualTo(now);
        assertThat(captured.getDedupKey()).isEqualTo("user.registered:0:5");
    }

    @Test
    void skipsDuplicateDelivery() {
        when(repository.existsByDedupKey("user.registered:0:5")).thenReturn(true);

        service.record(AuditAction.USER_REGISTERED, 1L, "aidar",
                AuditTargetType.USER, 1L, "aidar", null,
                LocalDateTime.now(), "user.registered:0:5");

        verify(repository, never()).save(any());
    }

    @Test
    void fallsBackToNowWhenOccurredAtNull() {
        when(repository.existsByDedupKey("user.registered:0:6")).thenReturn(false);

        LocalDateTime beforeCall = LocalDateTime.now();
        service.record(AuditAction.USER_REGISTERED, 1L, "aidar",
                AuditTargetType.USER, 1L, "aidar", null,
                null, "user.registered:0:6");
        LocalDateTime afterCall = LocalDateTime.now();

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());

        AuditLog captured = captor.getValue();
        assertThat(captured.getOccurredAt()).isNotNull();
        assertThat(captured.getOccurredAt())
                .isAfterOrEqualTo(beforeCall.minusMinutes(1))
                .isBeforeOrEqualTo(afterCall.plusMinutes(1));
    }

    @Test
    void searchDelegatesToRepositoryWithPageable() {
        Pageable pageable = mock(Pageable.class);
        Page<AuditLog> mockPage = new PageImpl<>(List.of());
        when(repository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(mockPage);

        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();
        Page<AuditLog> result = service.search(AuditAction.EVENT_LIKED, 2L,
                AuditTargetType.EVENT, from, to, pageable);

        assertThat(result).isEqualTo(mockPage);
        verify(repository).findAll(any(Specification.class), eq(pageable));
    }
}
