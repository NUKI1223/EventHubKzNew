package org.ngcvfb.userservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.eventhubkz.common.events.UserDeletedEvent;
import org.ngcvfb.userservice.kafka.UserKafkaProducer;
import org.ngcvfb.userservice.model.Role;
import org.ngcvfb.userservice.model.User;
import org.ngcvfb.userservice.repository.UserRepository;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceDeleteTest {

    @Mock UserRepository userRepository;
    @Mock UserKafkaProducer kafkaProducer;

    @InjectMocks UserService userService;

    private User user(long id, Role role) {
        return User.builder().id(id).username("u" + id).email("u" + id + "@kz")
                .password("x").role(role).build();
    }

    @Test
    void adminCannotDeleteAnotherAdmin() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.ADMIN)));

        assertThatThrownBy(() -> userService.deleteUser(2L, 1L, "ADMIN", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void deletePublishesSnapshotEvent() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.USER)));

        userService.deleteUser(2L, 1L, "ADMIN", "спам");

        verify(userRepository).deleteById(2L);
        verify(kafkaProducer).sendUserDeleted(any(UserDeletedEvent.class));
    }

    @Test
    void selfDeleteStillAllowed() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.USER)));

        userService.deleteUser(2L, 2L, "USER", null);

        verify(userRepository).deleteById(2L);
        verify(kafkaProducer).sendUserDeleted(any(UserDeletedEvent.class));
    }
}
