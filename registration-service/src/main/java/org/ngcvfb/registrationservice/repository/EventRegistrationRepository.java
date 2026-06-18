package org.ngcvfb.registrationservice.repository;

import org.ngcvfb.registrationservice.model.EventRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {

    Optional<EventRegistration> findByUserIdAndEventId(Long userId, Long eventId);

    Optional<EventRegistration> findByCode(String code);

    boolean existsByCode(String code);

    List<EventRegistration> findByUserId(Long userId);

    List<EventRegistration> findByEventId(Long eventId);

    boolean existsByUserIdAndEventId(Long userId, Long eventId);

    void deleteByUserIdAndEventId(Long userId, Long eventId);

    void deleteByEventId(Long eventId);

    @Query("SELECT COUNT(r) FROM EventRegistration r WHERE r.eventId = :eventId")
    long countByEventId(@Param("eventId") Long eventId);

    @Query("SELECT r.eventId FROM EventRegistration r WHERE r.userId = :userId")
    List<Long> findEventIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT r.eventId, COUNT(r) FROM EventRegistration r WHERE r.eventId IN :ids GROUP BY r.eventId")
    List<Object[]> countsByEventIds(@Param("ids") List<Long> ids);
}
