package org.ngcvfb.auditservice.controller;

import lombok.RequiredArgsConstructor;
import org.ngcvfb.auditservice.dto.AuditLogDTO;
import org.ngcvfb.auditservice.dto.AuditPage;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.ngcvfb.auditservice.service.AuditRecordService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditRecordService recordService;

    @GetMapping
    public AuditPage search(
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) AuditTargetType targetType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Только для администратора");
        }
        var pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<AuditLogDTO> result = recordService.search(action, actorId, targetType, from, to, pageable)
                .map(AuditLogDTO::from);
        return AuditPage.from(result);
    }
}
