package org.ngcvfb.likeservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.EventDeletedEvent;
import org.ngcvfb.likeservice.service.EventLikeService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventKafkaConsumer {

    private final EventLikeService eventLikeService;

    @KafkaListener(topics = "event.deleted", groupId = "like-service-group")
    public void handleEventDeleted(EventDeletedEvent event) {
        log.info("Received event.deleted: {}", event.getId());
        try {
            eventLikeService.deleteAllLikesForEvent(event.getId());
            log.info("Deleted all likes for event: {}", event.getId());
        } catch (Exception e) {
            log.error("Failed to delete likes for event: {}", event.getId(), e);
        }
    }
}
