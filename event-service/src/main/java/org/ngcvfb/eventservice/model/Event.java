package org.ngcvfb.eventservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {
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
    @CollectionTable(name = "event_tags", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "tag_name")
    private Set<String> tags = new HashSet<>();

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private boolean online;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    private LocalDateTime registrationDeadline;

    @Column(nullable = true)
    private String mainImageUrl;

    private String externalLink;

    // Способ регистрации. Колонка добавляется ddl-auto к существующим строкам как
    // NULL — в EventService NULL трактуется как производное значение (EXTERNAL при
    // наличии внешней ссылки, иначе NATIVE).
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_type", length = 20)
    private RegistrationType registrationType;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @Column(name = "organizer_email")
    private String organizerEmail;

    @Column(name = "like_count")
    private int likeCount = 0;

    // Integer (не примитив): колонка добавлена через ddl-auto к существующим
    // строкам, у которых view_count = NULL — примитив int падает при гидрации.
    @Column(name = "view_count", columnDefinition = "integer default 0")
    private Integer viewCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
