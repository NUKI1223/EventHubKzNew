package org.ngcvfb.eventservice.repository;

import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.model.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRequestRepository extends JpaRepository<EventRequest, Long> {

    List<EventRequest> findByRequesterId(Long requesterId);

    Page<EventRequest> findByStatus(RequestStatus status, Pageable pageable);

    List<EventRequest> findByRequesterIdAndStatus(Long requesterId, RequestStatus status);
}
