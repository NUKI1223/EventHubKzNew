package org.ngcvfb.eventservice.controller;

import lombok.RequiredArgsConstructor;
import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.model.RequestStatus;
import org.ngcvfb.eventservice.service.EventRequestService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/event-requests")
@RequiredArgsConstructor
public class EventRequestController {

    private final EventRequestService eventRequestService;

    private void assertAdmin(String userRole) {
        if (!"ADMIN".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<EventRequest>> getAllRequests(
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String userRole) {
        assertAdmin(userRole);
        return ResponseEntity.ok(eventRequestService.getAllRequests());
    }

    @GetMapping("/my")
    public ResponseEntity<List<EventRequest>> getMyRequests(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(eventRequestService.getRequestsByRequester(userId));
    }

    @GetMapping("/my/status/{status}")
    public ResponseEntity<List<EventRequest>> getMyRequestsByStatus(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable RequestStatus status) {
        return ResponseEntity.ok(eventRequestService.getRequestsByRequesterAndStatus(userId, status));
    }

    @GetMapping("/pending")
    public ResponseEntity<Page<EventRequest>> getPendingRequests(
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String userRole,
            @PageableDefault(size = 10) Pageable pageable) {
        assertAdmin(userRole);
        return ResponseEntity.ok(eventRequestService.getPendingRequests(pageable));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<EventRequest>> getRequestsByStatus(
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String userRole,
            @PathVariable RequestStatus status,
            @PageableDefault(size = 10) Pageable pageable) {
        assertAdmin(userRole);
        return ResponseEntity.ok(eventRequestService.getRequestsByStatus(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventRequest> getRequestById(
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String userRole,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        return eventRequestService.getRequestById(id)
                .map(req -> {
                    if (!"ADMIN".equals(userRole) && !userId.equals(req.getRequesterId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                    }
                    return ResponseEntity.ok(req);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<EventRequest> createRequest(
            @Valid @RequestBody EventRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Email") String userEmail) {
        request.setRequesterId(userId);
        request.setRequesterEmail(userEmail);
        EventRequest created = eventRequestService.createRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<EventRequest> approveRequest(
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String userRole,
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long adminId) {
        assertAdmin(userRole);
        return ResponseEntity.ok(eventRequestService.approveRequest(id, adminId));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<EventRequest> rejectRequest(
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String userRole,
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long adminId,
            @RequestBody(required = false) String reason) {
        assertAdmin(userRole);
        return ResponseEntity.ok(eventRequestService.rejectRequest(id, adminId, reason));
    }

    @PutMapping("/{id}/update")
    public ResponseEntity<EventRequest> updateRequest(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> payload,
            @RequestHeader("X-User-Id") Long adminId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String userRole) {
        assertAdmin(userRole);
        String status = (String) payload.get("status");
        String adminComment = (String) payload.get("adminComment");

        RequestStatus requestStatus = RequestStatus.valueOf(status.toUpperCase());
        return ResponseEntity.ok(eventRequestService.updateRequestStatus(id, requestStatus, adminComment, adminId));
    }
}
