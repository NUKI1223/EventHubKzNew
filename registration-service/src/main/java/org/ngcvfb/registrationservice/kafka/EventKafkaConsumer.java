package org.ngcvfb.registrationservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.EventDeletedEvent;
import org.ngcvfb.registrationservice.service.EventRegistrationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventKafkaConsumer {

    private final EventRegistrationService registrationService;

    @KafkaListener(topics = "event.deleted", groupId = "registration-service-group")
    public void handleEventDeleted(EventDeletedEvent event) {
        log.info("Received event.deleted: {}", event.getId());
        try {
            registrationService.deleteAllRegistrationsForEvent(event.getId());
            log.info("Deleted all registrations for event: {}", event.getId());
        } catch (Exception e) {
            log.error("Failed to delete registrations for event: {}", event.getId(), e);
        }
    }
}
