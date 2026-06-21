package org.ngcvfb.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.dto.EventDTO;
import org.ngcvfb.eventhubkz.common.dto.UserDTO;
import org.ngcvfb.eventhubkz.common.events.EventCreatedEvent;
import org.ngcvfb.eventhubkz.common.events.EventUpdatedEvent;
import org.ngcvfb.eventhubkz.common.events.EventDeletedEvent;
import org.ngcvfb.eventhubkz.common.exception.ResourceNotFoundException;
import org.ngcvfb.eventservice.client.RegistrationClient;
import org.ngcvfb.eventservice.client.UserClient;
import org.ngcvfb.eventservice.dto.AttendeeDTO;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.Event;
import org.ngcvfb.eventservice.model.RegistrationType;
import org.ngcvfb.eventservice.repository.EventRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final EventKafkaProducer kafkaProducer;
    private final RegistrationClient registrationClient;
    private final UserClient userClient;

    public List<EventDTO> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "events", key = "#id")
    public EventDTO getEventById(Long id) {
        return toDTO(findEventOrThrow(id));
    }

    public List<EventDTO> getEventsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return eventRepository.findAllById(ids).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private Event findEventOrThrow(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", id));
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

    @Cacheable(value = "eventsByTag", key = "#tag")
    public List<EventDTO> getEventsByTag(String tag) {
        return eventRepository.findByTag(tag).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "eventsByTag", allEntries = true)
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
                .registrationType(resolveRegistrationType(dto.getRegistrationType(), dto.getExternalLink()))
                .questions(dto.getQuestions())
                .organizerId(organizerId)
                .organizerEmail(organizerEmail)
                .likeCount(0)
                .build();

        Event saved = eventRepository.save(event);
        log.info("Created event: {} by organizer {}", saved.getId(), organizerId);

        kafkaProducer.sendEventCreated(toCreatedEvent(saved));

        return toDTO(saved);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "events", key = "#id"),
            @CacheEvict(value = "eventsByTag", allEntries = true)
    })
    public EventDTO updateEvent(Long id, EventDTO dto, Long requesterId, String role) {
        Event event = findEventOrThrow(id);
        requireEventOwnerOrAdmin(event, requesterId, role);
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
        if (dto.getRegistrationType() != null) {
            event.setRegistrationType(resolveRegistrationType(dto.getRegistrationType(), dto.getExternalLink()));
        }

        Event updated = eventRepository.save(event);
        log.info("Updated event: {}", updated.getId());

        kafkaProducer.sendEventUpdated(toUpdatedEvent(updated));

        return toDTO(updated);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "events", key = "#id"),
            @CacheEvict(value = "eventsByTag", allEntries = true)
    })
    public void deleteEvent(Long id, Long requesterId, String role) {
        Event event = findEventOrThrow(id);
        requireEventOwnerOrAdmin(event, requesterId, role);
        eventRepository.deleteById(id);
        log.info("Deleted event: {} by {}", id, requesterId);

        // Send Kafka event
        kafkaProducer.sendEventDeleted(EventDeletedEvent.create(id));
    }

    // Редактировать/удалять мероприятие может только его организатор или администратор.
    private void requireEventOwnerOrAdmin(Event event, Long requesterId, String role) {
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        if (!isAdmin && !Objects.equals(event.getOrganizerId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Изменять мероприятие может только организатор или администратор");
        }
    }

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public void incrementLikeCount(Long eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            event.setLikeCount(event.getLikeCount() + 1);
            eventRepository.save(event);
        });
    }

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public void incrementViewCount(Long eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            int current = event.getViewCount() == null ? 0 : event.getViewCount();
            event.setViewCount(current + 1);
            eventRepository.save(event);
        });
    }

    public void reindexAll() {
        List<Event> all = eventRepository.findAll();
        log.info("Reindexing {} events", all.size());
        for (Event event : all) {
            kafkaProducer.sendEventCreated(toCreatedEvent(event));
        }
        log.info("Reindex complete");
    }

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public void decrementLikeCount(Long eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            if (event.getLikeCount() > 0) {
                event.setLikeCount(event.getLikeCount() - 1);
                eventRepository.save(event);
            }
        });
    }

    private void requireOrganizerOrAdmin(Event event, Long requesterId, String role) {
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        if (!isAdmin && !Objects.equals(event.getOrganizerId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Управлять сотрудниками может только организатор мероприятия");
        }
    }

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public void addStaff(Long eventId, Long userId, Long requesterId, String role) {
        Event event = findEventOrThrow(eventId);
        requireOrganizerOrAdmin(event, requesterId, role);
        if (event.getStaffIds() == null) {
            event.setStaffIds(new HashSet<>());
        }
        event.getStaffIds().add(userId);
        eventRepository.save(event);
        log.info("Added staff {} to event {} by {}", userId, eventId, requesterId);
    }

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public void removeStaff(Long eventId, Long userId, Long requesterId, String role) {
        Event event = findEventOrThrow(eventId);
        requireOrganizerOrAdmin(event, requesterId, role);
        if (event.getStaffIds() != null) {
            event.getStaffIds().remove(userId);
            eventRepository.save(event);
        }
        log.info("Removed staff {} from event {} by {}", userId, eventId, requesterId);
    }

    public List<EventDTO> getStaffedBy(Long userId) {
        return eventRepository.findUpcomingStaffedBy(userId, LocalDateTime.now())
                .stream().map(this::toDTO).toList();
    }

    // user-service сериализует ids как ?ids=1,2,3 — на популярном событии длинный
    // список переполняет лимит строки запроса. Бьём на батчи, чтобы список участников
    // (и напоминания) не падали с ростом аудитории.
    private List<UserDTO> fetchUsersChunked(List<Long> ids) {
        final int batch = 200;
        List<UserDTO> users = new java.util.ArrayList<>();
        for (int i = 0; i < ids.size(); i += batch) {
            users.addAll(userClient.getUsersByIds(ids.subList(i, Math.min(i + batch, ids.size()))));
        }
        return users;
    }

    /**
     * Список участников мероприятия для организатора (или администратора): id, имя, email.
     * Агрегирует записи из registration-service и контакты из user-service.
     * Доступ строго ограничен владельцем события — иначе 403.
     */
    public List<AttendeeDTO> getAttendees(Long eventId, Long requesterId, String role) {
        Event event = findEventOrThrow(eventId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        boolean isOrganizer = Objects.equals(event.getOrganizerId(), requesterId);
        boolean isStaff = event.getStaffIds() != null && event.getStaffIds().contains(requesterId);
        if (!isAdmin && !isOrganizer && !isStaff) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Список участников доступен только организатору или сотруднику мероприятия");
        }

        List<Map<String, Object>> registrations;
        try {
            registrations = registrationClient.getEventAttendees(eventId, requesterId, role);
        } catch (Exception e) {
            log.error("Failed to fetch registrations for event {}: {}", eventId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Сервис записей временно недоступен, попробуйте позже");
        }
        Map<Long, String> statusByUser = new java.util.HashMap<>();
        Map<Long, Map<String, String>> answersByUser = new java.util.HashMap<>();
        for (Map<String, Object> r : registrations) {
            if (r.get("userId") == null) continue;
            Long uid = Long.valueOf(r.get("userId").toString());
            statusByUser.put(uid, r.get("status") == null ? "REGISTERED" : r.get("status").toString());
            Object ans = r.get("answers");
            if (ans instanceof Map<?, ?> m) {
                Map<String, String> a = new java.util.HashMap<>();
                m.forEach((k, v) -> a.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
                answersByUser.put(uid, a);
            }
        }
        List<Long> userIds = new java.util.ArrayList<>(statusByUser.keySet());
        if (userIds.isEmpty()) {
            return List.of();
        }

        return fetchUsersChunked(userIds).stream()
                .map(u -> new AttendeeDTO(u.getId(), u.getUsername(), u.getEmail(),
                        statusByUser.getOrDefault(u.getId(), "REGISTERED"),
                        answersByUser.getOrDefault(u.getId(), Map.of())))
                .sorted((a, b) -> {
                    String an = a.username() == null ? "" : a.username();
                    String bn = b.username() == null ? "" : b.username();
                    return an.compareToIgnoreCase(bn);
                })
                .collect(Collectors.toList());
    }

    /**
     * Определяет способ регистрации: явный, если передан корректный, иначе производный —
     * EXTERNAL при наличии внешней ссылки, иначе NATIVE. Гарантирует, что старые события
     * (с registration_type = NULL) и заявки без явного выбора получают разумное значение.
     */
    private RegistrationType resolveRegistrationType(String requested, String externalLink) {
        if (requested != null && !requested.isBlank()) {
            try {
                return RegistrationType.valueOf(requested.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                log.warn("Unknown registrationType '{}', falling back to derived value", requested);
            }
        }
        boolean hasExternal = externalLink != null && !externalLink.isBlank();
        return hasExternal ? RegistrationType.EXTERNAL : RegistrationType.NATIVE;
    }

    private EventCreatedEvent toCreatedEvent(Event event) {
        return EventCreatedEvent.create(
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
        );
    }

    private EventUpdatedEvent toUpdatedEvent(Event event) {
        return EventUpdatedEvent.create(
                event.getId(),
                event.getTitle(),
                event.getShortDescription(),
                event.getFullDescription(),
                event.getTags(),
                event.getLocation(),
                event.isOnline(),
                event.getEventDate(),
                event.getMainImageUrl()
        );
    }

    private EventDTO toDTO(Event event) {
        EventDTO dto = new EventDTO();
        dto.setId(event.getId());
        dto.setTitle(event.getTitle());
        dto.setShortDescription(event.getShortDescription());
        dto.setFullDescription(event.getFullDescription());
        dto.setTags(event.getTags() == null ? null : new HashSet<>(event.getTags()));
        dto.setStaffIds(event.getStaffIds() == null ? java.util.Set.of() : new HashSet<>(event.getStaffIds()));
        dto.setLocation(event.getLocation());
        dto.setOnline(event.isOnline());
        dto.setEventDate(event.getEventDate());
        dto.setRegistrationDeadline(event.getRegistrationDeadline());
        dto.setMainImageUrl(event.getMainImageUrl());
        dto.setExternalLink(event.getExternalLink());
        dto.setRegistrationType(
                resolveRegistrationType(
                        event.getRegistrationType() == null ? null : event.getRegistrationType().name(),
                        event.getExternalLink()).name());
        dto.setOrganizerEmail(event.getOrganizerEmail());
        dto.setOrganizerId(event.getOrganizerId());
        dto.setLikesCount(event.getLikeCount());
        dto.setViewsCount(event.getViewCount() == null ? 0 : event.getViewCount());
        dto.setQuestions(event.getQuestions());
        return dto;
    }
}
