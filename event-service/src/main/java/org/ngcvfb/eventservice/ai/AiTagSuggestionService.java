package org.ngcvfb.eventservice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventservice.client.TagClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTagSuggestionService {

    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final String MODEL = "gemini-2.5-flash-lite";
    private static final int MAX_TAGS = 5;

    private final TagClient tagClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient http = RestClient.builder().build();

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    public List<String> suggestTags(String title, String shortDescription, String fullDescription) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key not configured — returning empty tag suggestions");
            return List.of();
        }

        List<String> vocabulary = loadEventVocabulary();
        if (vocabulary.isEmpty()) {
            log.warn("Tag vocabulary is empty — cannot suggest tags");
            return List.of();
        }

        String userMessage = """
                Доступные теги: %s.

                Описание IT-мероприятия:
                Название: %s
                Краткое: %s
                Полное: %s

                Выбери 3-5 наиболее релевантных тегов СТРОГО из списка выше. Учитывай язык, технологии и формат.
                Верни ТОЛЬКО JSON-массив строк. Пример: ["backend","java","meetup"]
                """.formatted(
                String.join(", ", vocabulary),
                safe(title),
                safe(shortDescription),
                safe(fullDescription)
        );

        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text",
                                "Ты — помощник модератора IT-платформы EventHub.kz. Отвечай строго JSON-массивом строк, без markdown."))
                ),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userMessage))
                )),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.2,
                        "maxOutputTokens", 512,
                        "thinkingConfig", Map.of("thinkingBudget", 0)
                )
        );

        try {
            String response = http.post()
                    .uri(API_URL.formatted(MODEL))
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            List<String> picked = parseTagsFromResponse(response, vocabulary);
            log.info("Gemini suggested {} tags from vocabulary of {}: {}", picked.size(), vocabulary.size(), picked);
            return picked;
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> loadEventVocabulary() {
        try {
            List<Map<String, Object>> tags = tagClient.getTags("EVENT");
            List<String> names = new ArrayList<>();
            for (Map<String, Object> tag : tags) {
                Object name = tag.get("name");
                if (name != null) names.add(name.toString());
            }
            return names;
        } catch (Exception e) {
            log.error("Failed to load tag vocabulary from tag-service: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseTagsFromResponse(String response, List<String> vocabulary) {
        if (response == null || response.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) return List.of();

            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) return List.of();

            String text = parts.get(0).path("text").asText("").trim();
            // strip potential markdown fences just in case
            text = text.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start < 0 || end < 0 || end <= start) return List.of();
            String json = text.substring(start, end + 1);

            JsonNode arr = objectMapper.readTree(json);
            if (!arr.isArray()) return List.of();

            Set<String> vocabSet = new LinkedHashSet<>(vocabulary);
            Set<String> picked = new LinkedHashSet<>();
            for (JsonNode node : arr) {
                String tag = node.asText("").trim();
                if (tag.isEmpty()) continue;
                if (vocabSet.contains(tag)) picked.add(tag);
                if (picked.size() >= MAX_TAGS) break;
            }
            return new ArrayList<>(picked);
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {} ({})", e.getMessage(), response);
            return List.of();
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        return trimmed.length() > 1500 ? trimmed.substring(0, 1500) : trimmed;
    }
}
