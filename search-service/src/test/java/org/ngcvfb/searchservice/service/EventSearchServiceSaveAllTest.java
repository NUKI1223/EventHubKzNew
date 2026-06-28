package org.ngcvfb.searchservice.service;

import org.junit.jupiter.api.Test;
import org.ngcvfb.searchservice.model.EventDocument;
import org.ngcvfb.searchservice.repository.EventSearchRepository;

import java.util.List;

import static org.mockito.Mockito.*;

class EventSearchServiceSaveAllTest {

    @Test
    void saveAllIssuesASingleBulkCall() {
        EventSearchRepository repo = mock(EventSearchRepository.class);
        EventSearchService service = new EventSearchService(repo);

        List<EventDocument> docs = List.of(
                EventDocument.builder().id("1").title("a").build(),
                EventDocument.builder().id("2").title("b").build());

        service.saveAll(docs);

        verify(repo, times(1)).saveAll(docs); // one bulk request
        verify(repo, never()).save(any());    // never per-document
    }
}
