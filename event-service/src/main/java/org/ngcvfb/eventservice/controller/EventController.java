package org.ngcvfb.eventservice.controller;

import lombok.RequiredArgsConstructor;
import org.ngcvfb.eventhubkz.common.dto.EventDTO;
import org.ngcvfb.eventservice.config.Pagination;
import org.ngcvfb.eventservice.service.EventService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<EventDTO>> getAllEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDTO> getEventById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @GetMapping("/batch")
    public ResponseEntity<List<EventDTO>> getEventsByIds(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(eventService.getEventsByIds(ids));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<Page<EventDTO>> getUpcomingEvents(
            @PageableDefault(size = Pagination.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ResponseEntity.ok(eventService.getUpcomingEvents(pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<EventDTO>> searchEvents(
            @RequestParam String keyword,
            @PageableDefault(size = Pagination.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ResponseEntity.ok(eventService.searchEvents(keyword, pageable));
    }

    @GetMapping("/popular")
    public ResponseEntity<Page<EventDTO>> getMostPopular(
            @PageableDefault(size = Pagination.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ResponseEntity.ok(eventService.getMostPopular(pageable));
    }

    @GetMapping("/organizer/{organizerId}")
    public ResponseEntity<List<EventDTO>> getEventsByOrganizer(@PathVariable Long organizerId) {
        return ResponseEntity.ok(eventService.getEventsByOrganizer(organizerId));
    }

    @GetMapping("/tag/{tag}")
    public ResponseEntity<List<EventDTO>> getEventsByTag(@PathVariable String tag) {
        return ResponseEntity.ok(eventService.getEventsByTag(tag));
    }

    @PostMapping
    public ResponseEntity<EventDTO> createEvent(
            @Valid @RequestBody EventDTO eventDTO,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Email") String userEmail) {
        EventDTO created = eventService.createEvent(eventDTO, userId, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventDTO> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody EventDTO eventDTO) {
        return ResponseEntity.ok(eventService.updateEvent(id, eventDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reindex")
    public ResponseEntity<Void> reindex() {
        eventService.reindexAll();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<Void> incrementView(@PathVariable Long id) {
        eventService.incrementViewCount(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Void> incrementLike(@PathVariable Long id) {
        eventService.incrementLikeCount(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> decrementLike(@PathVariable Long id) {
        eventService.decrementLikeCount(id);
        return ResponseEntity.ok().build();
    }
}
