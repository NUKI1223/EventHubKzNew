package org.ngcvfb.tagservice.service;

import lombok.RequiredArgsConstructor;
import org.ngcvfb.eventhubkz.common.exception.ResourceNotFoundException;
import org.ngcvfb.tagservice.model.Tag;
import org.ngcvfb.tagservice.model.TagType;
import org.ngcvfb.tagservice.repository.TagRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {
    private final TagRepository tagRepository;

    public List<Tag> getTags(TagType type) {
        return tagRepository.findByType(type);
    }

    public Tag getTagById(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", "id", id));
    }

    public Set<Tag> getTagsByNames(Set<String> names, TagType type) {
        return tagRepository.findByNameInAndType(names, type);
    }

    public Tag createTag(String name, TagType type) {
        if (tagRepository.existsByNameAndType(name, type)) {
            throw new IllegalArgumentException("Tag already exists for " + type + ": " + name);
        }
        return tagRepository.save(Tag.builder().name(name).type(type).build());
    }

    public Tag updateTag(Long id, String name) {
        Tag tag = getTagById(id);
        tag.setName(name);
        return tagRepository.save(tag);
    }

    public void deleteTag(Long id) {
        tagRepository.deleteById(id);
    }

    public Set<String> getOrCreateTags(Set<String> tagNames, TagType type) {
        Set<Tag> existing = tagRepository.findByNameInAndType(tagNames, type);
        Set<String> existingNames = existing.stream().map(Tag::getName).collect(Collectors.toSet());

        tagNames.stream()
                .filter(name -> !existingNames.contains(name))
                .forEach(name -> createTag(name, type));

        return tagNames;
    }
}
