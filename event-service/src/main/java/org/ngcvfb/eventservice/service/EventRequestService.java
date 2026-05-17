package org.ngcvfb.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.exception.ResourceNotFoundException;
import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.model.RequestStatus;
import org.ngcvfb.eventservice.repository.EventRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventRequestService {

    private final EventRequestRepository eventRequestRepository;
    private final EventService eventService;

    public List<EventRequest> getAllRequests() {
        return eventRequestRepository.findAll();
    }

    public List<EventRequest> getRequestsByRequester(Long requesterId) {
        return eventRequestRepository.findByRequesterId(requesterId);
    }

    public Page<EventRequest> getRequestsByStatus(RequestStatus status, Pageable pageable) {
        return eventRequestRepository.findByStatus(status, pageable);
    }

    public List<EventRequest> getRequestsByRequesterAndStatus(Long requesterId, RequestStatus status) {
        return eventRequestRepository.findByRequesterIdAndStatus(requesterId, status);
    }

    public Page<EventRequest> getPendingRequests(Pageable pageable) {
        return eventRequestRepository.findByStatus(RequestStatus.PENDING, pageable);
    }

    @Transactional
    public EventRequest createRequest(EventRequest request) {
        request.setStatus(RequestStatus.PENDING);
        EventRequest saved = eventRequestRepository.save(request);
        log.info("Created event request: {} by user {}", saved.getId(), saved.getRequesterId());
        return saved;
    }

    @Transactional
    public EventRequest approveRequest(Long requestId, Long adminId) {
        EventRequest request = findRequestOrThrow(requestId);
        request.setStatus(RequestStatus.APPROVED);
        request.setReviewerId(adminId);
        request.setReviewedAt(LocalDateTime.now());

        EventRequest approved = eventRequestRepository.save(request);
        log.info("Approved event request: {} by admin {}", approved.getId(), adminId);

        return approved;
    }

    @Transactional
    public EventRequest rejectRequest(Long requestId, Long adminId, String rejectionReason) {
        EventRequest request = findRequestOrThrow(requestId);
        request.setStatus(RequestStatus.REJECTED);
        request.setReviewerId(adminId);
        request.setReviewedAt(LocalDateTime.now());
        request.setAdminComment(rejectionReason);

        EventRequest rejected = eventRequestRepository.save(request);
        log.info("Rejected event request: {} by admin {}", rejected.getId(), adminId);

        return rejected;
    }

    public Optional<EventRequest> getRequestById(Long id) {
        return eventRequestRepository.findById(id);
    }

    @Transactional
    public EventRequest updateRequestStatus(Long requestId, RequestStatus status, String adminComment, Long adminId) {
        EventRequest request = findRequestOrThrow(requestId);
        request.setStatus(status);
        request.setReviewerId(adminId);
        request.setReviewedAt(LocalDateTime.now());
        if (adminComment != null && !adminComment.isEmpty()) {
            request.setAdminComment(adminComment);
        }

        EventRequest updated = eventRequestRepository.save(request);
        log.info("Updated event request: {} to status {} by admin {}", updated.getId(), status, adminId);

        return updated;
    }

    private EventRequest findRequestOrThrow(Long id) {
        return eventRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EventRequest", "id", id));
    }
}
