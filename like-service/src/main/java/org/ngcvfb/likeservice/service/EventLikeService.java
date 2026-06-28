package org.ngcvfb.likeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.EventLikedEvent;
import org.ngcvfb.likeservice.kafka.LikeKafkaProducer;
import org.ngcvfb.likeservice.model.EventLike;
import org.ngcvfb.likeservice.repository.EventLikeRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventLikeService {

    private final EventLikeRepository eventLikeRepository;
    private final LikeKafkaProducer kafkaProducer;

    public boolean isLikedByUser(Long userId, Long eventId) {
        return eventLikeRepository.existsByUserIdAndEventId(userId, eventId);
    }

    @Cacheable(value = "likeCount", key = "#eventId")
    public long getLikeCount(Long eventId) {
        return eventLikeRepository.countByEventId(eventId);
    }

    /** Счётчики лайков для набора событий одним запросом (для списка): eventId -> count. */
    public java.util.Map<Long, Long> getCounts(List<Long> eventIds) {
        java.util.Map<Long, Long> result = new java.util.HashMap<>();
        if (eventIds == null || eventIds.isEmpty()) return result;
        for (Object[] row : eventLikeRepository.countsByEventIds(eventIds)) {
            result.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return result;
    }

    public List<Long> getLikedEventIdsByUser(Long userId) {
        return eventLikeRepository.findEventIdsByUserId(userId);
    }

    public List<EventLike> getLikesByEvent(Long eventId) {
        return eventLikeRepository.findByEventId(eventId);
    }

    public List<EventLike> getLikesByUser(Long userId) {
        return eventLikeRepository.findByUserId(userId);
    }

    @CacheEvict(value = "likeCount", key = "#eventId")
    @Transactional
    public EventLike likeEvent(Long userId, Long eventId, String userEmail, String username,
                               Long organizerId, String organizerEmail, String eventTitle) {
        if (eventLikeRepository.existsByUserIdAndEventId(userId, eventId)) {
            log.info("User {} already liked event {}", userId, eventId);
            return eventLikeRepository.findByUserIdAndEventId(userId, eventId).orElse(null);
        }

        EventLike like = EventLike.builder()
                .userId(userId)
                .eventId(eventId)
                .build();

        EventLike saved = eventLikeRepository.save(like);
        log.info("User {} liked event {}", userId, eventId);

        // Send Kafka event for notification
        kafkaProducer.sendEventLiked(EventLikedEvent.create(
                eventId, userId, userEmail, username, organizerId, organizerEmail, eventTitle
        ));

        return saved;
    }

    @CacheEvict(value = "likeCount", key = "#eventId")
    @Transactional
    public void unlikeEvent(Long userId, Long eventId) {
        eventLikeRepository.deleteByUserIdAndEventId(userId, eventId);
        log.info("User {} unliked event {}", userId, eventId);
    }

    @CacheEvict(value = "likeCount", key = "#eventId")
    @Transactional
    public void deleteAllLikesForEvent(Long eventId) {
        eventLikeRepository.deleteByEventId(eventId);
        log.info("Deleted all likes for event {}", eventId);
    }
}
