package org.ngcvfb.eventservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.eventhubkz.common.events.EventCandidateFoundEvent;
import org.ngcvfb.eventservice.model.Event;
import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.model.RequestSource;
import org.ngcvfb.eventservice.model.RequestStatus;
import org.ngcvfb.eventservice.repository.EventRepository;
import org.ngcvfb.eventservice.repository.EventRequestRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock EventRequestRepository requestRepo;
    @Mock EventRepository eventRepo;
    @InjectMocks CandidateService service;

    private static final LocalDateTime WHEN = LocalDateTime.now().plusDays(7);

    private EventCandidateFoundEvent candidate() {
        return EventCandidateFoundEvent.create("Go Meetup", "short desc here",
                "full description long enough", WHEN,
                "Алматы", "SmartPoint", false, Set.of("backend"), null,
                "https://t.me/kz/10", "kz");
    }

    @Test
    void newCandidateCreatesAiIngestRequest() {
        when(eventRepo.findByTitleIgnoreCaseAndEventDateBetween(anyString(), any(), any()))
                .thenReturn(List.of());
        when(requestRepo.findByStatusAndTitleIgnoreCaseAndEventDateBetween(any(), anyString(), any(), any()))
                .thenReturn(List.of());

        service.ingest(candidate());

        verify(requestRepo).save(argThat((EventRequest r) ->
                r.getSource() == RequestSource.AI_INGEST
                && r.getRequesterId() == null
                && r.getSourceChannel().equals("kz")
                && r.getLocation().equals("SmartPoint")));
    }

    @Test
    void duplicateInCatalogIsSkipped() {
        // stored event's location corroborates the candidate city "Алматы" → deduped
        Event existing = Event.builder().title("Go Meetup").location("Алматы, БЦ SmartPoint")
                .eventDate(WHEN).build();
        when(eventRepo.findByTitleIgnoreCaseAndEventDateBetween(anyString(), any(), any()))
                .thenReturn(List.of(existing));

        service.ingest(candidate());

        verify(requestRepo, never()).save(any());
    }

    @Test
    void sameTitleAndDayButLocationDoesNotCorroborateCityStillCreates() {
        // FAIL-SAFE: a same-title/same-day row whose location does NOT contain the
        // candidate city passes through to the admin queue (city is never persisted).
        Event existing = Event.builder().title("Go Meetup").location("SmartPoint")
                .eventDate(WHEN).build();
        when(eventRepo.findByTitleIgnoreCaseAndEventDateBetween(anyString(), any(), any()))
                .thenReturn(List.of(existing));
        when(requestRepo.findByStatusAndTitleIgnoreCaseAndEventDateBetween(any(), anyString(), any(), any()))
                .thenReturn(List.of());

        service.ingest(candidate());

        verify(requestRepo).save(any(EventRequest.class));
    }
}
