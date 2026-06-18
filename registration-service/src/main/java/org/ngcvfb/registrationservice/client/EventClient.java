package org.ngcvfb.registrationservice.client;

import org.ngcvfb.eventhubkz.common.dto.EventDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Источник правды по самому мероприятию (дата, дедлайн регистрации, тип регистрации,
 * организатор) — event-service. Нужен, чтобы валидировать запись на стороне сервера:
 * запись закрывается после дедлайна и недоступна для событий с внешней регистрацией.
 */
@FeignClient(name = "event-service")
public interface EventClient {

    @GetMapping("/api/events/{id}")
    EventDTO getEventById(@PathVariable("id") Long id);
}
