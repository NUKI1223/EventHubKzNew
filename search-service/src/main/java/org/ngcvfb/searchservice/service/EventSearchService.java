package org.ngcvfb.searchservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.searchservice.model.EventDocument;
import org.ngcvfb.searchservice.repository.EventSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventSearchService {

    private final EventSearchRepository eventSearchRepository;

    public Page<EventDocument> search(String keyword, Pageable pageable) {
        log.debug("Searching events with keyword: {}", keyword);
        return eventSearchRepository.searchByKeyword(keyword, pageable);
    }

    public Page<EventDocument> searchByTitleOrDescription(String query, Pageable pageable) {
        return eventSearchRepository.findByTitleContainingOrShortDescriptionContaining(
                query, query, pageable);
    }

    public Page<EventDocument> findByTag(String tag, Pageable pageable) {
        return eventSearchRepository.findByTagsContaining(tag, pageable);
    }

    public Page<EventDocument> findByOrganizer(Long organizerId, Pageable pageable) {
        return eventSearchRepository.findByOrganizerId(organizerId, pageable);
    }

    public Page<EventDocument> findUpcoming(Pageable pageable) {
        return eventSearchRepository.findByEventDateAfter(LocalDateTime.now(), pageable);
    }

    public Page<EventDocument> findOnlineEvents(Pageable pageable) {
        return eventSearchRepository.findByOnline(true, pageable);
    }

    public Page<EventDocument> findOfflineEvents(Pageable pageable) {
        return eventSearchRepository.findByOnline(false, pageable);
    }

    public Optional<EventDocument> findById(String id) {
        return eventSearchRepository.findById(id);
    }

    public EventDocument save(EventDocument document) {
        log.info("Indexing event: {}", document.getId());
        return eventSearchRepository.save(document);
    }

    public void deleteById(String id) {
        log.info("Deleting event from index: {}", id);
        eventSearchRepository.deleteById(id);
    }

    public long count() {
        return eventSearchRepository.count();
    }
}
