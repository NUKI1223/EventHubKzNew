package org.ngcvfb.eventhubkz.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDTO {
    private Long id;
    private String title;
    private String shortDescription;
    private String fullDescription;
    private String location;
    private boolean online;
    private LocalDateTime eventDate;
    private LocalDateTime registrationDeadline;
    private String mainImageUrl;
    private String externalLink;
    private String registrationType;
    private Set<String> tags;
    private Long organizerId;
    private String organizerUsername;
    private String organizerEmail;
    private int likesCount;
    private int viewsCount;
}
