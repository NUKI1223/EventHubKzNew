package org.ngcvfb.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.notificationservice.repository.NotificationRepository;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationUserCleanupTest {

    @Mock NotificationRepository notificationRepository;

    @InjectMocks NotificationService service;

    @Test
    void deletesAllUserNotifications() {
        service.deleteAllForUser(7L);
        verify(notificationRepository).deleteByUserId(7L);
    }
}
