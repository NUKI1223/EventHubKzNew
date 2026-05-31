package org.ngcvfb.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.SupportMessageResolvedEvent;
import org.ngcvfb.eventhubkz.common.exception.ResourceNotFoundException;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.SupportMessage;
import org.ngcvfb.eventservice.repository.SupportMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportMessageService {

    private final SupportMessageRepository repository;
    private final EventKafkaProducer kafkaProducer;

    public List<SupportMessage> getAll(Boolean resolved) {
        return resolved == null
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByResolvedOrderByCreatedAtDesc(resolved);
    }

    @Transactional
    public SupportMessage create(SupportMessage message) {
        SupportMessage saved = repository.save(message);
        log.info("Support message created: {} from {}", saved.getId(), saved.getEmail());
        return saved;
    }

    @Transactional
    public SupportMessage resolve(Long id, String adminReply) {
        SupportMessage message = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SupportMessage", "id", id));
        message.setResolved(true);
        message.setAdminReply(adminReply);
        message.setResolvedAt(LocalDateTime.now());
        SupportMessage saved = repository.save(message);

        if (saved.getUserId() != null) {
            kafkaProducer.sendSupportResolved(SupportMessageResolvedEvent.create(
                    saved.getId(),
                    saved.getUserId(),
                    saved.getEmail(),
                    saved.getMessage(),
                    saved.getAdminReply()
            ));
        } else {
            log.warn("Support message {} has no userId, skipping notification", saved.getId());
        }
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("SupportMessage", "id", id);
        }
        repository.deleteById(id);
        log.info("Support message deleted: {}", id);
    }
}
