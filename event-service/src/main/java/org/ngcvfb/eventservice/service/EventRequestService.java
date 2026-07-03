package org.ngcvfb.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.dto.EventDTO;
import org.ngcvfb.eventhubkz.common.events.EventRequestCreatedEvent;
import org.ngcvfb.eventhubkz.common.events.EventRequestReviewedEvent;
import org.ngcvfb.eventhubkz.common.exception.ResourceNotFoundException;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.model.RequestStatus;
import org.ngcvfb.eventservice.repository.EventRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventRequestService {

    private final EventRequestRepository eventRequestRepository;
    private final EventService eventService;
    private final EventKafkaProducer kafkaProducer;

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private Pageable withDefaultSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
    }

    public List<EventRequest> getAllRequests() {
        return eventRequestRepository.findAll(NEWEST_FIRST);
    }

    public List<EventRequest> getRequestsByRequester(Long requesterId) {
        return eventRequestRepository.findByRequesterIdOrderByCreatedAtDesc(requesterId);
    }

    public Page<EventRequest> getRequestsByStatus(RequestStatus status, Pageable pageable) {
        return eventRequestRepository.findByStatus(status, withDefaultSort(pageable));
    }

    public List<EventRequest> getRequestsByRequesterAndStatus(Long requesterId, RequestStatus status) {
        return eventRequestRepository.findByRequesterIdAndStatusOrderByCreatedAtDesc(requesterId, status);
    }

    public Page<EventRequest> getPendingRequests(Pageable pageable) {
        return eventRequestRepository.findByStatus(RequestStatus.PENDING, withDefaultSort(pageable));
    }

    @Transactional
    public EventRequest createRequest(EventRequest request) {
        boolean external = !"NATIVE".equalsIgnoreCase(request.getRegistrationType());
        if (external && (request.getExternalLink() == null || request.getExternalLink().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Для внешней регистрации нужна ссылка на сайт мероприятия");
        }
        // Вопросы имеют смысл только для записи на платформе.
        if (external) {
            request.setQuestions(null);
        }
        request.setStatus(RequestStatus.PENDING);
        EventRequest saved = eventRequestRepository.save(request);
        log.info("Created event request: {} by user {}", saved.getId(), saved.getRequesterId());

        kafkaProducer.sendEventRequestCreated(EventRequestCreatedEvent.create(
                saved.getId(), saved.getRequesterId(), saved.getRequesterEmail(),
                saved.getTitle()));

        return saved;
    }

    @Transactional
    public EventRequest approveRequest(Long requestId, Long adminId) {
        EventRequest request = findRequestOrThrow(requestId);

        if (request.getStatus() == RequestStatus.APPROVED) {
            log.info("Event request {} is already approved, skipping", requestId);
            return request;
        }
        if (request.getStatus() == RequestStatus.REJECTED) {
            throw new IllegalStateException(
                    "Заявка уже отклонена и не может быть одобрена");
        }

        request.setStatus(RequestStatus.APPROVED);
        request.setReviewerId(adminId);
        request.setReviewedAt(LocalDateTime.now());

        EventRequest approved = eventRequestRepository.save(request);

        EventDTO dto = new EventDTO();
        dto.setTitle(approved.getTitle());
        dto.setShortDescription(approved.getShortDescription());
        dto.setFullDescription(approved.getFullDescription());
        dto.setTags(approved.getTags() == null ? null : new HashSet<>(approved.getTags()));
        dto.setLocation(approved.getLocation());
        dto.setOnline(approved.isOnline());
        dto.setEventDate(approved.getEventDate());
        dto.setRegistrationDeadline(approved.getRegistrationDeadline());
        dto.setMainImageUrl(approved.getMainImageUrl());
        dto.setExternalLink(approved.getExternalLink());
        dto.setRegistrationType(approved.getRegistrationType());
        dto.setQuestions(approved.getQuestions());
        String organizerEmail = approved.getContactEmail() != null && !approved.getContactEmail().isBlank()
                ? approved.getContactEmail()
                : approved.getRequesterEmail();
        eventService.createEvent(dto, approved.getRequesterId(), organizerEmail);

        kafkaProducer.sendEventRequestReviewed(EventRequestReviewedEvent.create(
                approved.getId(),
                approved.getRequesterId(),
                approved.getRequesterEmail(),
                approved.getTitle(),
                true,
                approved.getAdminComment()
        ));

        log.info("Approved event request: {} by admin {}, event created from it", approved.getId(), adminId);

        return approved;
    }

    @Transactional
    public EventRequest rejectRequest(Long requestId, Long adminId, String rejectionReason) {
        EventRequest request = findRequestOrThrow(requestId);

        if (request.getStatus() == RequestStatus.REJECTED) {
            log.info("Event request {} is already rejected, skipping", requestId);
            return request;
        }
        if (request.getStatus() == RequestStatus.APPROVED) {
            throw new IllegalStateException(
                    "Заявка уже одобрена, событие опубликовано — отклонить нельзя");
        }

        request.setStatus(RequestStatus.REJECTED);
        request.setReviewerId(adminId);
        request.setReviewedAt(LocalDateTime.now());
        request.setAdminComment(rejectionReason);

        EventRequest rejected = eventRequestRepository.save(request);

        if (rejected.getRequesterId() == null) {
            log.warn("Rejected request {} has no requesterId, organizer will not receive a notification",
                    rejected.getId());
        }
        kafkaProducer.sendEventRequestReviewed(EventRequestReviewedEvent.create(
                rejected.getId(),
                rejected.getRequesterId(),
                rejected.getRequesterEmail(),
                rejected.getTitle(),
                false,
                rejected.getAdminComment()
        ));

        log.info("Rejected event request: {} by admin {}, notify={}",
                rejected.getId(), adminId, rejected.getRequesterId());

        return rejected;
    }

    public Optional<EventRequest> getRequestById(Long id) {
        return eventRequestRepository.findById(id);
    }

    @Transactional
    public EventRequest updateRequestStatus(Long requestId, RequestStatus status, String adminComment, Long adminId) {
        // Persist the admin comment first so approve/reject pick it up.
        if (adminComment != null && !adminComment.isEmpty()) {
            EventRequest request = findRequestOrThrow(requestId);
            request.setAdminComment(adminComment);
            eventRequestRepository.save(request);
        }

        // Delegate to the real review flow so APPROVED actually creates the
        // event and both outcomes notify the requester via Kafka.
        return switch (status) {
            case APPROVED -> approveRequest(requestId, adminId);
            case REJECTED -> rejectRequest(requestId, adminId, adminComment);
            default -> {
                EventRequest request = findRequestOrThrow(requestId);
                request.setStatus(status);
                request.setReviewerId(adminId);
                request.setReviewedAt(LocalDateTime.now());
                EventRequest updated = eventRequestRepository.save(request);
                log.info("Updated event request: {} to status {} by admin {}", updated.getId(), status, adminId);
                yield updated;
            }
        };
    }

    private EventRequest findRequestOrThrow(Long id) {
        return eventRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EventRequest", "id", id));
    }
}
