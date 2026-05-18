package org.ngcvfb.eventservice.controller;

import lombok.RequiredArgsConstructor;
import org.ngcvfb.eventservice.model.SupportMessage;
import org.ngcvfb.eventservice.service.SupportMessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportMessageController {

    private final SupportMessageService service;

    @PostMapping
    public ResponseEntity<SupportMessage> create(
            @RequestBody SupportMessage payload,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId != null) payload.setUserId(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(payload));
    }

    @GetMapping
    public ResponseEntity<List<SupportMessage>> list(
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role,
            @RequestParam(required = false) Boolean resolved) {
        assertAdmin(role);
        return ResponseEntity.ok(service.getAll(resolved));
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<SupportMessage> resolve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        assertAdmin(role);
        String reply = body == null ? null : body.get("adminReply");
        return ResponseEntity.ok(service.resolve(id, reply));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        assertAdmin(role);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void assertAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
    }
}
