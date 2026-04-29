package org.ngcvfb.eventservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "event_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 500)
    private String shortDescription;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String fullDescription;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_request_tags", joinColumns = @JoinColumn(name = "request_id"))
    @Column(name = "tag_name")
    private Set<String> tags = new HashSet<>();

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private boolean online;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    private LocalDateTime registrationDeadline;

    private String mainImageUrl;

    private String externalLink;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "requester_email")
    private String requesterEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    @Column(length = 1000)
    private String adminComment;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        status = RequestStatus.PENDING;
    }
}
