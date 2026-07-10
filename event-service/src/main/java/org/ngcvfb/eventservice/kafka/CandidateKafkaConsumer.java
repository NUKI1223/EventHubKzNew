package org.ngcvfb.eventservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.EventCandidateFoundEvent;
import org.ngcvfb.eventservice.service.CandidateService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CandidateKafkaConsumer {

    private final CandidateService candidateService;

    @KafkaListener(topics = "event.candidate.found", groupId = "event-service-group")
    public void handle(EventCandidateFoundEvent event) {
        log.info("Received event.candidate.found from {}", event.getSourceChannel());
        try {
            candidateService.ingest(event);
        } catch (Exception e) {
            log.error("Failed to ingest candidate from {}", event.getSourceChannel(), e);
        }
    }
}
