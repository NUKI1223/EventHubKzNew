package org.ngcvfb.eventservice.repository;

import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.model.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRequestRepository extends JpaRepository<EventRequest, Long> {

    List<EventRequest> findAll(Sort sort);

    List<EventRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    Page<EventRequest> findByStatus(RequestStatus status, Pageable pageable);

    List<EventRequest> findByRequesterIdAndStatusOrderByCreatedAtDesc(Long requesterId, RequestStatus status);

    void deleteByRequesterId(Long requesterId);

    List<EventRequest> findByStatusAndTitleIgnoreCaseAndEventDateBetween(
            RequestStatus status, String title, LocalDateTime from, LocalDateTime to);
}
