package org.ngcvfb.eventservice.repository;

import org.ngcvfb.eventservice.model.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findAllByOrderByCreatedAtDesc();
    List<SupportMessage> findByResolvedOrderByCreatedAtDesc(boolean resolved);
}
