package org.ngcvfb.likeservice.repository;

import org.ngcvfb.likeservice.model.EventLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventLikeRepository extends JpaRepository<EventLike, Long> {

    Optional<EventLike> findByUserIdAndEventId(Long userId, Long eventId);

    List<EventLike> findByUserId(Long userId);

    List<EventLike> findByEventId(Long eventId);

    boolean existsByUserIdAndEventId(Long userId, Long eventId);

    void deleteByUserIdAndEventId(Long userId, Long eventId);

    void deleteByEventId(Long eventId);

    @Query("SELECT COUNT(l) FROM EventLike l WHERE l.eventId = :eventId")
    long countByEventId(@Param("eventId") Long eventId);

    @Query("SELECT l.eventId FROM EventLike l WHERE l.userId = :userId")
    List<Long> findEventIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT l.eventId, COUNT(l) FROM EventLike l WHERE l.eventId IN :ids GROUP BY l.eventId")
    List<Object[]> countsByEventIds(@Param("ids") List<Long> ids);
}
