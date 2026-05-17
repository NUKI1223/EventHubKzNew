package org.ngcvfb.tagservice.service;

import lombok.RequiredArgsConstructor;
import org.ngcvfb.eventhubkz.common.exception.ResourceNotFoundException;
import org.ngcvfb.tagservice.model.Tag;
import org.ngcvfb.tagservice.repository.TagRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {
    private final TagRepository tagRepository;

    public List<Tag> getAllTags() {
        return tagRepository.findAll();
    }

    public Tag getTagById(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", "id", id));
    }

    public Tag getTagByName(String name) {
        return tagRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", "name", name));
    }

    public Set<Tag> getTagsByNames(Set<String> names) {
        return tagRepository.findByNameIn(names);
    }

    public Tag createTag(String name) {
        if (tagRepository.existsByName(name)) {
            throw new IllegalArgumentException("Tag already exists: " + name);
        }
        return tagRepository.save(Tag.builder().name(name).build());
    }

    public Tag updateTag(Long id, String name) {
        Tag tag = getTagById(id);
        tag.setName(name);
        return tagRepository.save(tag);
    }

    public void deleteTag(Long id) {
        tagRepository.deleteById(id);
    }

    public Set<String> getOrCreateTags(Set<String> tagNames) {
        Set<Tag> existingTags = tagRepository.findByNameIn(tagNames);
        Set<String> existingNames = existingTags.stream().map(Tag::getName).collect(Collectors.toSet());

        tagNames.stream()
                .filter(name -> !existingNames.contains(name))
                .forEach(this::createTag);

        return tagNames;
    }
}
