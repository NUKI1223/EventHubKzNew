package org.ngcvfb.eventservice.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class AiTagSuggestionController {

    private final AiTagSuggestionService service;

    @PostMapping("/suggest-tags")
    public ResponseEntity<Map<String, List<String>>> suggest(@RequestBody Map<String, String> payload) {
        List<String> tags = service.suggestTags(
                payload.getOrDefault("title", ""),
                payload.getOrDefault("shortDescription", ""),
                payload.getOrDefault("fullDescription", "")
        );
        return ResponseEntity.ok(Map.of("tags", tags));
    }
}
