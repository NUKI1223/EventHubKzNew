package org.ngcvfb.likeservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.EventLikedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LikeKafkaProducer {

    private static final String TOPIC_EVENT_LIKED = "event.liked";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendEventLiked(EventLikedEvent event) {
        log.info("Sending event.liked: eventId={}, userId={}", event.getLikedEventId(), event.getUserId());
        kafkaTemplate.send(TOPIC_EVENT_LIKED, String.valueOf(event.getLikedEventId()), event);
    }
}
