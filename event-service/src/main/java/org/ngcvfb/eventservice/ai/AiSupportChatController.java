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
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class AiSupportChatController {

    private final AiSupportChatService service;

    @SuppressWarnings("unchecked")
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, Object> payload) {
        Object rawHistory = payload.get("messages");
        List<Map<String, String>> history = rawHistory instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(m -> (Map<String, String>) m).toList()
                : List.of();
        String reply = service.reply(history);
        return ResponseEntity.ok(Map.of("reply", reply));
    }
}
