package org.ngcvfb.eventservice.repository;

import org.ngcvfb.eventservice.model.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByOrganizerId(Long organizerId);

    List<Event> findByEventDateBetween(LocalDateTime start, LocalDateTime end);

    Page<Event> findByEventDateAfter(LocalDateTime date, Pageable pageable);

    Page<Event> findByOnline(boolean online, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.eventDate > :now ORDER BY e.eventDate ASC")
    Page<Event> findUpcomingEvents(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE :tag MEMBER OF e.tags")
    List<Event> findByTag(@Param("tag") String tag);

    @Query("SELECT e FROM Event e WHERE :userId MEMBER OF e.staffIds AND e.eventDate > :now ORDER BY e.eventDate ASC")
    List<Event> findUpcomingStaffedBy(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("SELECT e FROM Event e WHERE LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(e.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Event> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT e FROM Event e ORDER BY e.likeCount DESC")
    Page<Event> findMostPopular(Pageable pageable);

    List<Event> findByTitleIgnoreCaseAndEventDateBetween(String title, LocalDateTime from, LocalDateTime to);
}
