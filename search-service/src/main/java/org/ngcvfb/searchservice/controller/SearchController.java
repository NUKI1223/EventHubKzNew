package org.ngcvfb.searchservice.controller;

import lombok.RequiredArgsConstructor;
import org.ngcvfb.searchservice.model.EventDocument;
import org.ngcvfb.searchservice.service.EventSearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final EventSearchService eventSearchService;

    @GetMapping
    public ResponseEntity<Page<EventDocument>> search(
            @RequestParam(name = "q") String q,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(eventSearchService.search(q, pageable));
    }

    @GetMapping("/events")
    public ResponseEntity<Page<EventDocument>> searchEvents(
            @RequestParam(name = "query") String query,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(eventSearchService.searchByTitleOrDescription(query, pageable));
    }

    @GetMapping("/events/tag/{tag}")
    public ResponseEntity<Page<EventDocument>> searchByTag(
            @PathVariable(name = "tag") String tag,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(eventSearchService.findByTag(tag, pageable));
    }

    @GetMapping("/events/organizer/{organizerId}")
    public ResponseEntity<Page<EventDocument>> searchByOrganizer(
            @PathVariable(name = "organizerId") Long organizerId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(eventSearchService.findByOrganizer(organizerId, pageable));
    }

    @GetMapping("/events/upcoming")
    public ResponseEntity<Page<EventDocument>> getUpcomingEvents(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(eventSearchService.findUpcoming(pageable));
    }

    @GetMapping("/events/online")
    public ResponseEntity<Page<EventDocument>> getOnlineEvents(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(eventSearchService.findOnlineEvents(pageable));
    }

    @GetMapping("/events/offline")
    public ResponseEntity<Page<EventDocument>> getOfflineEvents(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(eventSearchService.findOfflineEvents(pageable));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<EventDocument> getEventById(@PathVariable(name = "id") String id) {
        return eventSearchService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/count")
    public ResponseEntity<Long> getCount() {
        return ResponseEntity.ok(eventSearchService.count());
    }
}
