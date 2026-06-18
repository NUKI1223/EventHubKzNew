package org.ngcvfb.likeservice.controller;

import lombok.RequiredArgsConstructor;
import org.ngcvfb.likeservice.model.EventLike;
import org.ngcvfb.likeservice.service.EventLikeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
public class LikeController {

    private final EventLikeService eventLikeService;

    @GetMapping("/event/{eventId}/count")
    public ResponseEntity<Long> getLikeCount(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventLikeService.getLikeCount(eventId));
    }

    @GetMapping("/counts")
    public ResponseEntity<Map<Long, Long>> getCounts(@RequestParam List<Long> eventIds) {
        return ResponseEntity.ok(eventLikeService.getCounts(eventIds));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<EventLike>> getLikesByEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventLikeService.getLikesByEvent(eventId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<EventLike>> getLikesByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(eventLikeService.getLikesByUser(userId));
    }

    @GetMapping("/user/{userId}/events")
    public ResponseEntity<List<Long>> getLikedEventIds(@PathVariable Long userId) {
        return ResponseEntity.ok(eventLikeService.getLikedEventIdsByUser(userId));
    }

    @GetMapping("/check")
    public ResponseEntity<Boolean> isLiked(
            @RequestParam Long userId,
            @RequestParam Long eventId) {
        return ResponseEntity.ok(eventLikeService.isLikedByUser(userId, eventId));
    }

    @PostMapping("/event/{eventId}")
    public ResponseEntity<EventLike> likeEvent(
            @PathVariable Long eventId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader(value = "X-Username", required = false) String username,
            @RequestBody(required = false) Map<String, Object> eventInfo) {

        Long organizerId = null;
        String organizerEmail = null;
        String eventTitle = null;

        if (eventInfo != null) {
            organizerId = eventInfo.get("organizerId") != null ?
                    Long.valueOf(eventInfo.get("organizerId").toString()) : null;
            organizerEmail = (String) eventInfo.get("organizerEmail");
            eventTitle = (String) eventInfo.get("eventTitle");
        }

        EventLike like = eventLikeService.likeEvent(
                userId, eventId, userEmail, username, organizerId, organizerEmail, eventTitle);

        return ResponseEntity.status(HttpStatus.CREATED).body(like);
    }

    @DeleteMapping("/event/{eventId}")
    public ResponseEntity<Void> unlikeEvent(
            @PathVariable Long eventId,
            @RequestHeader("X-User-Id") Long userId) {
        eventLikeService.unlikeEvent(userId, eventId);
        return ResponseEntity.noContent().build();
    }
}
