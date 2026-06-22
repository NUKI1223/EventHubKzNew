package org.ngcvfb.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.ngcvfb.eventhubkz.common.dto.UserDTO;
import org.ngcvfb.userservice.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Внутренний контракт для service-to-service вызовов (через Eureka, НЕ через gateway).
 * Путь /internal/** намеренно не маршрутизируется api-gateway, поэтому внешние клиенты
 * сюда не достучатся — это единственное место, где наружу (в рамках кластера) отдаётся
 * email пользователей: нужно для рассылок и списка участников в event-service.
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/contacts")
    public ResponseEntity<List<UserDTO>> getContacts(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(userService.getContactsByIds(ids));
    }
}
