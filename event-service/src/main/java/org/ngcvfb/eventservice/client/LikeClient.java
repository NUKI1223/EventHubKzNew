package org.ngcvfb.eventservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@FeignClient(name = "like-service")
public interface LikeClient {

    /** Лайки события — каждая запись содержит userId лайкнувшего. */
    @GetMapping("/api/likes/event/{eventId}")
    List<Map<String, Object>> getLikesByEvent(@PathVariable("eventId") Long eventId);
}
