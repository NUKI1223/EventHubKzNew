package org.ngcvfb.registrationservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.EventRegisteredEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegistrationKafkaProducer {

    private static final String TOPIC_EVENT_REGISTERED = "event.registered";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendEventRegistered(EventRegisteredEvent event) {
        log.info("Sending event.registered: eventId={}, userId={}",
                event.getRegisteredEventId(), event.getUserId());
        kafkaTemplate.send(TOPIC_EVENT_REGISTERED, String.valueOf(event.getRegisteredEventId()), event);
    }
}
