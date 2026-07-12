package org.ngcvfb.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.EventCandidateFoundEvent;
import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.model.RequestSource;
import org.ngcvfb.eventservice.model.RequestStatus;
import org.ngcvfb.eventservice.repository.EventRepository;
import org.ngcvfb.eventservice.repository.EventRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandidateService {

    private final EventRequestRepository requestRepo;
    private final EventRepository eventRepo;

    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(); }

    @Transactional
    public void ingest(EventCandidateFoundEvent c) {
        LocalDateTime from = c.getEventDate().toLocalDate().atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        String city = norm(c.getCity());

        boolean dupEvent = eventRepo
                .findByTitleIgnoreCaseAndEventDateBetween(c.getTitle(), from, to).stream()
                .anyMatch(e -> norm(e.getLocation()).contains(city) || city.isEmpty());
        boolean dupPending = requestRepo
                .findByStatusAndTitleIgnoreCaseAndEventDateBetween(RequestStatus.PENDING, c.getTitle(), from, to)
                .stream().anyMatch(r -> norm(r.getLocation()).contains(city) || city.isEmpty());
        if (dupEvent || dupPending) {
            log.info("Skipping duplicate AI candidate: {} @ {}", c.getTitle(), c.getEventDate());
            return;
        }

        EventRequest r = EventRequest.builder()
                .title(c.getTitle()).shortDescription(c.getShortDescription())
                .fullDescription(c.getFullDescription()).location(c.getLocation())
                .online(c.isOnline()).eventDate(c.getEventDate())
                .tags(c.getTags() == null ? new HashSet<>() : new HashSet<>(c.getTags()))
                .externalLink(c.getExternalLink()).mainImageUrl(c.getMainImageUrl())
                .source(RequestSource.AI_INGEST).sourceUrl(c.getSourceUrl())
                .sourceChannel(c.getSourceChannel()).requesterId(null)
                .status(RequestStatus.PENDING).build();
        requestRepo.save(r);
        log.info("Created AI_INGEST EventRequest from {}", c.getSourceChannel());
    }
}
