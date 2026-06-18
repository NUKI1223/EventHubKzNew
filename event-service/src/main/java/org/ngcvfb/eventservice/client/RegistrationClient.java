package org.ngcvfb.eventservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@FeignClient(name = "registration-service")
public interface RegistrationClient {

    /** Записи на событие — каждая запись содержит userId зарегистрировавшегося. */
    @GetMapping("/api/registrations/event/{eventId}")
    List<Map<String, Object>> getRegistrationsByEvent(@PathVariable("eventId") Long eventId);
}
