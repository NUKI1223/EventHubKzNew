package org.ngcvfb.eventservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "tag-service")
public interface TagClient {

    @GetMapping("/api/tags")
    List<Map<String, Object>> getTags(@RequestParam("type") String type);
}
