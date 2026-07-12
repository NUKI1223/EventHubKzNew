package org.ngcvfb.eventservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.ngcvfb.eventhubkz.common.dto.QuestionDef;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
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

    @NotBlank(message = "Название обязательно")
    @Size(min = 3, max = 200, message = "Название от 3 до 200 символов")
    @Column(nullable = false)
    private String title;

    @NotBlank(message = "Краткое описание обязательно")
    @Size(min = 10, max = 500, message = "Краткое описание от 10 до 500 символов")
    @Column(nullable = false, length = 500)
    private String shortDescription;

    @NotBlank(message = "Полное описание обязательно")
    @Size(min = 20, max = 20000, message = "Полное описание от 20 до 20000 символов")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String fullDescription;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_request_tags", joinColumns = @JoinColumn(name = "request_id"))
    @Column(name = "tag_name")
    private Set<String> tags = new HashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Size(max = 10, message = "Не больше 10 вопросов")
    private List<QuestionDef> questions;

    @NotBlank(message = "Локация обязательна")
    @Size(max = 200)
    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private boolean online;

    @NotNull(message = "Дата события обязательна")
    @Future(message = "Дата события должна быть в будущем")
    @Column(nullable = false)
    private LocalDateTime eventDate;

    private LocalDateTime registrationDeadline;

    @Pattern(
            regexp = "^$|^https?://.+",
            message = "URL изображения должен начинаться с http:// или https://")
    @Column(length = 1024)  // Telegram CDN poster URLs run ~350–450 chars (>255 default)
    private String mainImageUrl;

    @Pattern(
            regexp = "^$|^https?://[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}(?:[:/?#][^\\s]*)?$",
            message = "Введите корректный URL вида https://example.com")
    private String externalLink;

    // NATIVE — запись внутри платформы (с вопросами); EXTERNAL — переход по ссылке.
    @Column(name = "registration_type")
    private String registrationType;

    @Column(name = "requester_id")
    private Long requesterId;

    @Column(name = "requester_email")
    private String requesterEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private RequestSource source = RequestSource.MANUAL;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "source_channel", length = 200)
    private String sourceChannel;

    @Column(name = "contact_email")
    private String contactEmail;

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
        if (source == null) source = RequestSource.MANUAL;
    }
}
