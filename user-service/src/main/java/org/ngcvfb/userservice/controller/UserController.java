package org.ngcvfb.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.ngcvfb.eventhubkz.common.dto.UserDTO;
import org.ngcvfb.userservice.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> tags) {
        if ((q == null || q.isBlank()) && (tags == null || tags.isEmpty())) {
            return ResponseEntity.ok(userService.getAllUsers());
        }
        Set<String> tagSet = tags == null ? null : new HashSet<>(tags);
        return ResponseEntity.ok(userService.searchUsers(q, tagSet));
    }

    @GetMapping("/batch")
    public ResponseEntity<List<UserDTO>> getUsersByIds(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(userService.getUsersByIds(ids));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<UserDTO> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<UserDTO> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserByUsername(username));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(userService.getSelf(userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable Long id,
            @RequestBody UserDTO userDTO,
            @RequestHeader("X-User-Id") Long requesterId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        requireSelfOrAdmin(id, requesterId, role);
        return ResponseEntity.ok(userService.updateUser(id, userDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long requesterId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        requireSelfOrAdmin(id, requesterId, role);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    private void requireSelfOrAdmin(Long targetUserId, Long requesterId, String role) {
        if (!"ADMIN".equalsIgnoreCase(role) && !Objects.equals(targetUserId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Доступно только самому пользователю или администратору");
        }
    }

    @GetMapping("/exists/email/{email}")
    public ResponseEntity<Boolean> existsByEmail(@PathVariable String email) {
        return ResponseEntity.ok(userService.existsByEmail(email));
    }

    @GetMapping("/exists/username/{username}")
    public ResponseEntity<Boolean> existsByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.existsByUsername(username));
    }
}
