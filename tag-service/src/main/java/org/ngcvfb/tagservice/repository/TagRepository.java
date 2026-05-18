package org.ngcvfb.tagservice.repository;

import org.ngcvfb.tagservice.model.Tag;
import org.ngcvfb.tagservice.model.TagType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findByType(TagType type);
    Optional<Tag> findByNameAndType(String name, TagType type);
    Set<Tag> findByNameInAndType(Set<String> names, TagType type);
    boolean existsByNameAndType(String name, TagType type);
}
