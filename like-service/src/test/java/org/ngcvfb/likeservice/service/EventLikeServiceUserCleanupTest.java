package org.ngcvfb.likeservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.likeservice.kafka.LikeKafkaProducer;
import org.ngcvfb.likeservice.model.EventLike;
import org.ngcvfb.likeservice.repository.EventLikeRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventLikeServiceUserCleanupTest {

    @Mock EventLikeRepository eventLikeRepository;
    @Mock LikeKafkaProducer kafkaProducer;
    @Mock CacheManager cacheManager;
    @Mock Cache cache;

    @InjectMocks EventLikeService service;

    @Test
    void deletesLikesAndEvictsAffectedCounters() {
        EventLike l1 = EventLike.builder().userId(7L).eventId(10L).build();
        EventLike l2 = EventLike.builder().userId(7L).eventId(11L).build();
        when(eventLikeRepository.findByUserId(7L)).thenReturn(List.of(l1, l2));
        when(cacheManager.getCache("likeCount")).thenReturn(cache);

        service.deleteAllLikesForUser(7L);

        verify(eventLikeRepository).deleteByUserId(7L);
        verify(cache).evict(10L);
        verify(cache).evict(11L);
    }
}
