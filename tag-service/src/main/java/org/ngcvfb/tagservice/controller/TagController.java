package org.ngcvfb.tagservice.controller;

import lombok.RequiredArgsConstructor;
import org.ngcvfb.tagservice.model.Tag;
import org.ngcvfb.tagservice.model.TagType;
import org.ngcvfb.tagservice.service.TagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {
    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<Tag>> getTags(
            @RequestParam(name = "type", defaultValue = "EVENT") TagType type) {
        return ResponseEntity.ok(tagService.getTags(type));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tag> getTagById(@PathVariable Long id) {
        return ResponseEntity.ok(tagService.getTagById(id));
    }

    @PostMapping("/by-names")
    public ResponseEntity<Set<Tag>> getTagsByNames(
            @RequestParam(name = "type", defaultValue = "EVENT") TagType type,
            @RequestBody Set<String> names) {
        return ResponseEntity.ok(tagService.getTagsByNames(names, type));
    }

    @PostMapping
    public ResponseEntity<Tag> createTag(
            @RequestParam(name = "type", defaultValue = "EVENT") TagType type,
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        assertAdmin(role);
        return ResponseEntity.ok(tagService.createTag(request.get("name"), type));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Tag> updateTag(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        assertAdmin(role);
        return ResponseEntity.ok(tagService.updateTag(id, request.get("name")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        assertAdmin(role);
        tagService.deleteTag(id);
        return ResponseEntity.noContent().build();
    }

    // Создание/изменение/удаление тегов — только администратор.
    private void assertAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Управление тегами доступно только администратору");
        }
    }
}
