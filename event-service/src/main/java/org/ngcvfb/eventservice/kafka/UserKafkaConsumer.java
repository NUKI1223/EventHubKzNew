package org.ngcvfb.eventservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.UserDeletedEvent;
import org.ngcvfb.eventservice.repository.EventRequestRepository;
import org.ngcvfb.eventservice.service.EventService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserKafkaConsumer {

    private final EventService eventService;
    private final EventRequestRepository eventRequestRepository;

    @KafkaListener(topics = "user.deleted", groupId = "event-service-group")
    @Transactional
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Received user.deleted: {}", event.getUserId());
        try {
            eventService.deleteAllForUser(event.getUserId());
            eventRequestRepository.deleteByRequesterId(event.getUserId());
        } catch (Exception e) {
            log.error("Failed to cascade user deletion: {}", event.getUserId(), e);
        }
    }
}
