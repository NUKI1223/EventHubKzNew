package org.ngcvfb.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.notificationservice.model.Notification;
import org.ngcvfb.notificationservice.model.NotificationType;
import org.ngcvfb.notificationservice.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public Page<Notification> getNotificationsByUser(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Page<Notification> getUnreadNotificationsByUser(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false, pageable);
    }

    public List<Notification> getUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public Optional<Notification> getById(Long id) {
        return notificationRepository.findById(id);
    }

    @Transactional
    public Notification createNotification(Long userId, String userEmail, String title,
                                           String message, NotificationType type, Long relatedEventId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .userEmail(userEmail)
                .title(title)
                .message(message)
                .type(type)
                .relatedEventId(relatedEventId)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Created notification {} for user {}", saved.getId(), userId);
        return saved;
    }

    /**
     * Idempotent variant: skips creating a duplicate notification when one with the same
     * (userId, type, relatedEventId) already exists. Used for deterministic events
     * (event creation, request review, user registration) so Kafka replays on rebuild
     * do not stack duplicates on the user.
     */
    @Transactional
    public Notification createIfAbsent(Long userId, String userEmail, String title,
                                        String message, NotificationType type, Long relatedEventId) {
        boolean exists = relatedEventId == null
                ? notificationRepository.existsByUserIdAndType(userId, type)
                : notificationRepository.existsByUserIdAndTypeAndRelatedEventId(userId, type, relatedEventId);
        if (exists) {
            log.info("Skipped duplicate notification: user={}, type={}, relatedEventId={}", userId, type, relatedEventId);
            return null;
        }
        return createNotification(userId, userEmail, title, message, type, relatedEventId);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.markAsRead(notificationId);
        log.debug("Marked notification {} as read", notificationId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
        log.info("Marked all notifications as read for user {}", userId);
    }

    @Transactional
    public void deleteNotification(Long id) {
        notificationRepository.deleteById(id);
    }

    @Transactional
    public void deleteAllByUser(Long userId) {
        notificationRepository.deleteByUserId(userId);
    }
}
