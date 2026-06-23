package org.ngcvfb.searchservice.repository;

import org.ngcvfb.searchservice.model.EventDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventSearchRepository extends ElasticsearchRepository<EventDocument, String> {

    Page<EventDocument> findByTagsContaining(String tag, Pageable pageable);

    Page<EventDocument> findByOrganizerId(Long organizerId, Pageable pageable);

    Page<EventDocument> findByOnline(boolean online, Pageable pageable);

    Page<EventDocument> findByEventDateAfter(LocalDateTime date, Pageable pageable);

    @Query("{\"bool\": {\"should\": [" +
            "{\"match\": {\"title\": \"?0\"}}," +
            "{\"match\": {\"shortDescription\": \"?0\"}}," +
            "{\"match\": {\"fullDescription\": \"?0\"}}," +
            "{\"match\": {\"tags\": \"?0\"}}" +
            "]}}")
    Page<EventDocument> searchByKeyword(String keyword, Pageable pageable);

    List<EventDocument> findByLocationContaining(String location);
}
