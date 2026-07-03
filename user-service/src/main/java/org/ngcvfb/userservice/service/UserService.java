package org.ngcvfb.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.dto.UserDTO;
import org.ngcvfb.eventhubkz.common.events.UserDeletedEvent;
import org.ngcvfb.eventhubkz.common.exception.ResourceNotFoundException;
import org.ngcvfb.userservice.kafka.UserKafkaProducer;
import org.ngcvfb.userservice.model.Role;
import org.ngcvfb.userservice.model.User;
import org.ngcvfb.userservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserKafkaProducer kafkaProducer;

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToListDTO)
                .collect(Collectors.toList());
    }

    public List<UserDTO> searchUsers(String q, Set<String> tags) {
        List<User> source = (tags == null || tags.isEmpty())
                ? userRepository.findAll()
                : userRepository.findByAnyTag(tags);
        if (q != null && !q.isBlank()) {
            String needle = q.toLowerCase();
            source = source.stream()
                    .filter(u -> u.getUsername() != null && u.getUsername().toLowerCase().contains(needle))
                    .collect(Collectors.toList());
        }
        return source.stream().map(this::mapToListDTO).collect(Collectors.toList());
    }

    public List<UserDTO> getUsersByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return userRepository.findAllById(ids).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return mapToDTO(user);
    }

    // Сам пользователь (/me) — с email/role.
    public UserDTO getSelf(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return mapToSelfDTO(user);
    }

    // Контакты для внутренних сервисов (не через gateway).
    public List<UserDTO> getContactsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return userRepository.findAllById(ids).stream()
                .map(this::mapToContactDTO)
                .collect(Collectors.toList());
    }

    public UserDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return mapToDTO(user);
    }

    public UserDTO getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        return mapToDTO(user);
    }

    @Transactional
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (userDTO.getUsername() != null) {
            user.setUsername(userDTO.getUsername());
        }
        if (userDTO.getDescription() != null) {
            user.setDescription(userDTO.getDescription());
        }
        if (userDTO.getAvatarUrl() != null) {
            user.setAvatarUrl(userDTO.getAvatarUrl());
        }
        if (userDTO.getContacts() != null) {
            user.setContacts(userDTO.getContacts());
        }
        if (userDTO.getTags() != null) {
            user.setTags(new HashSet<>(userDTO.getTags()));
        }

        user = userRepository.save(user);
        log.info("User updated: {}", user.getEmail());
        return mapToDTO(user);
    }

    @Transactional
    public void deleteUser(Long id, Long requesterId, String requesterRole, String reason) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        boolean self = id.equals(requesterId);
        if (!self && user.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Нельзя удалить другого администратора");
        }
        String username = user.getUsername();
        String email = user.getEmail();
        userRepository.deleteById(id);
        kafkaProducer.sendUserDeleted(
                UserDeletedEvent.create(id, username, email, requesterId, reason));
        log.info("User deleted: {} by {} (reason: {})", id, requesterId, reason);
    }

    // Админский список: email и роль включены — не использовать в публичных ответах.
    public List<UserDTO> getAdminUserList(String q) {
        List<User> users = (q == null || q.isBlank())
                ? userRepository.findAll()
                : userRepository.findAll().stream()
                    .filter(u -> u.getUsername().toLowerCase().contains(q.toLowerCase())
                              || u.getEmail().toLowerCase().contains(q.toLowerCase()))
                    .toList();
        return users.stream().map(this::mapToAdminDTO).toList();
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }


    private UserDTO mapToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .description(user.getDescription())
                .avatarUrl(user.getAvatarUrl())
                .contacts(user.getContacts())
                .tags(user.getTags())
                .build();
    }

    // Список/поиск: минимум полей.
    private UserDTO mapToListDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .description(user.getDescription())
                .avatarUrl(user.getAvatarUrl())
                .tags(user.getTags())
                .build();
    }

    // Полное представление — только для самого пользователя (/me).
    private UserDTO mapToSelfDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .description(user.getDescription())
                .avatarUrl(user.getAvatarUrl())
                .contacts(user.getContacts())
                .tags(user.getTags())
                .enabled(user.isEnabled())
                .build();
    }

    // Контакты для внутренних сервисов (рассылки, список участников): id+username+email.
    private UserDTO mapToContactDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }

    // Только для GET /admin/list — единственное место, где публичный список получает email/role.
    private UserDTO mapToAdminDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
