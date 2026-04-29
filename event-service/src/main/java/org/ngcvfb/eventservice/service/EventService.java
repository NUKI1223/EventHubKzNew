package org.ngcvfb.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.dto.EventDTO;
import org.ngcvfb.eventhubkz.common.events.EventCreatedEvent;
import org.ngcvfb.eventhubkz.common.events.EventUpdatedEvent;
import org.ngcvfb.eventhubkz.common.events.EventDeletedEvent;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.Event;
import org.ngcvfb.eventservice.repository.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final EventKafkaProducer kafkaProducer;

    public List<EventDTO> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public EventDTO getEventById(Long id) {
        return eventRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
    }

    public Page<EventDTO> getUpcomingEvents(Pageable pageable) {
        return eventRepository.findUpcomingEvents(LocalDateTime.now(), pageable)
                .map(this::toDTO);
    }

    public Page<EventDTO> searchEvents(String keyword, Pageable pageable) {
        return eventRepository.searchByKeyword(keyword, pageable)
                .map(this::toDTO);
    }

    public Page<EventDTO> getMostPopular(Pageable pageable) {
        return eventRepository.findMostPopular(pageable)
                .map(this::toDTO);
    }

    public List<EventDTO> getEventsByOrganizer(Long organizerId) {
        return eventRepository.findByOrganizerId(organizerId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<EventDTO> getEventsByTag(String tag) {
        return eventRepository.findByTag(tag).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventDTO createEvent(EventDTO dto, Long organizerId, String organizerEmail) {
        Event event = Event.builder()
                .title(dto.getTitle())
                .shortDescription(dto.getShortDescription())
                .fullDescription(dto.getFullDescription())
                .tags(dto.getTags() != null ? dto.getTags() : new HashSet<>())
                .location(dto.getLocation())
                .online(dto.isOnline())
                .eventDate(dto.getEventDate())
                .registrationDeadline(dto.getRegistrationDeadline())
                .mainImageUrl(dto.getMainImageUrl())
                .externalLink(dto.getExternalLink())
                .organizerId(organizerId)
                .organizerEmail(organizerEmail)
                .likeCount(0)
                .build();

        Event saved = eventRepository.save(event);
        log.info("Created event: {} by organizer {}", saved.getId(), organizerId);

        // Send Kafka event
        kafkaProducer.sendEventCreated(EventCreatedEvent.create(
                saved.getId(),
                saved.getTitle(),
                saved.getShortDescription(),
                saved.getFullDescription(),
                saved.getTags(),
                saved.getLocation(),
                saved.isOnline(),
                saved.getEventDate(),
                saved.getMainImageUrl(),
                saved.getOrganizerEmail(),
                saved.getOrganizerId()
        ));

        return toDTO(saved);
    }

    @Transactional
    public EventDTO updateEvent(Long id, EventDTO dto) {
        Optional<Event> optionalEvent = eventRepository.findById(id);
        if (optionalEvent.isEmpty()) {
            return null;
        }

        Event event = optionalEvent.get();
        event.setTitle(dto.getTitle());
        event.setShortDescription(dto.getShortDescription());
        event.setFullDescription(dto.getFullDescription());
        event.setTags(dto.getTags() != null ? dto.getTags() : event.getTags());
        event.setLocation(dto.getLocation());
        event.setOnline(dto.isOnline());
        event.setEventDate(dto.getEventDate());
        event.setRegistrationDeadline(dto.getRegistrationDeadline());
        event.setMainImageUrl(dto.getMainImageUrl());
        event.setExternalLink(dto.getExternalLink());

        Event updated = eventRepository.save(event);
        log.info("Updated event: {}", updated.getId());

        // Send Kafka event
        kafkaProducer.sendEventUpdated(EventUpdatedEvent.create(
                updated.getId(),
                updated.getTitle(),
                updated.getShortDescription(),
                updated.getFullDescription(),
                updated.getTags(),
                updated.getLocation(),
                updated.isOnline(),
                updated.getEventDate(),
                updated.getMainImageUrl()
        ));

        return toDTO(updated);
    }

    @Transactional
    public void deleteEvent(Long id) {
        eventRepository.deleteById(id);
        log.info("Deleted event: {}", id);

        // Send Kafka event
        kafkaProducer.sendEventDeleted(EventDeletedEvent.create(id));
    }

    @Transactional
    public void incrementLikeCount(Long eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            event.setLikeCount(event.getLikeCount() + 1);
            eventRepository.save(event);
        });
    }

    public void reindexAll() {
        List<Event> all = eventRepository.findAll();
        log.info("Reindexing {} events", all.size());
        for (Event event : all) {
            kafkaProducer.sendEventCreated(EventCreatedEvent.create(
                    event.getId(),
                    event.getTitle(),
                    event.getShortDescription(),
                    event.getFullDescription(),
                    event.getTags(),
                    event.getLocation(),
                    event.isOnline(),
                    event.getEventDate(),
                    event.getMainImageUrl(),
                    event.getOrganizerEmail(),
                    event.getOrganizerId()
            ));
        }
        log.info("Reindex complete");
    }

    @Transactional
    public void decrementLikeCount(Long eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            if (event.getLikeCount() > 0) {
                event.setLikeCount(event.getLikeCount() - 1);
                eventRepository.save(event);
            }
        });
    }

    private EventDTO toDTO(Event event) {
        EventDTO dto = new EventDTO();
        dto.setId(event.getId());
        dto.setTitle(event.getTitle());
        dto.setShortDescription(event.getShortDescription());
        dto.setFullDescription(event.getFullDescription());
        dto.setTags(event.getTags());
        dto.setLocation(event.getLocation());
        dto.setOnline(event.isOnline());
        dto.setEventDate(event.getEventDate());
        dto.setRegistrationDeadline(event.getRegistrationDeadline());
        dto.setMainImageUrl(event.getMainImageUrl());
        dto.setExternalLink(event.getExternalLink());
        dto.setOrganizerEmail(event.getOrganizerEmail());
        dto.setOrganizerId(event.getOrganizerId());
        dto.setLikesCount(event.getLikeCount());
        return dto;
    }
}
