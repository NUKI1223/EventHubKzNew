package org.ngcvfb.userservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.UserDeletedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserKafkaProducer {

    private static final String TOPIC_USER_DELETED = "user.deleted";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendUserDeleted(UserDeletedEvent event) {
        log.info("Sending user.deleted: userId={}, deletedBy={}",
                event.getUserId(), event.getDeletedBy());
        kafkaTemplate.send(TOPIC_USER_DELETED, String.valueOf(event.getUserId()), event);
    }
}
