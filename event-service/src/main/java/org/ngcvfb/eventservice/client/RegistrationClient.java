package org.ngcvfb.eventservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

@FeignClient(name = "registration-service")
public interface RegistrationClient {

    /** Записи на событие — каждая запись содержит userId зарегистрировавшегося. */
    @GetMapping("/api/registrations/event/{eventId}")
    List<Map<String, Object>> getRegistrationsByEvent(@PathVariable("eventId") Long eventId);

    /** Участники с ответами — организатор/админ. Headers форвардятся из getAttendees. */
    @GetMapping("/api/registrations/event/{eventId}/attendees")
    List<Map<String, Object>> getEventAttendees(
            @PathVariable("eventId") Long eventId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role);
}
