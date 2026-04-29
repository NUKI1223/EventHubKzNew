package org.ngcvfb.tagservice.controller;

import lombok.RequiredArgsConstructor;
import org.ngcvfb.tagservice.model.Tag;
import org.ngcvfb.tagservice.service.TagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {
    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<Tag>> getAllTags() {
        return ResponseEntity.ok(tagService.getAllTags());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tag> getTagById(@PathVariable Long id) {
        return ResponseEntity.ok(tagService.getTagById(id));
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<Tag> getTagByName(@PathVariable String name) {
        return ResponseEntity.ok(tagService.getTagByName(name));
    }

    @PostMapping("/by-names")
    public ResponseEntity<Set<Tag>> getTagsByNames(@RequestBody Set<String> names) {
        return ResponseEntity.ok(tagService.getTagsByNames(names));
    }

    @PostMapping
    public ResponseEntity<Tag> createTag(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(tagService.createTag(request.get("name")));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Tag> updateTag(@PathVariable Long id, @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(tagService.updateTag(id, request.get("name")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ResponseEntity.noContent().build();
    }
}
