package org.ngcvfb.tagservice.repository;

import org.ngcvfb.tagservice.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.Set;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);
    Set<Tag> findByNameIn(Set<String> names);
    boolean existsByName(String name);
}
