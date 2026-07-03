package org.ngcvfb.auditservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
public class AuditPage {
    @JsonProperty("content")
    private List<AuditLogDTO> content;

    @JsonProperty("number")
    private int pageNumber;

    @JsonProperty("size")
    private int pageSize;

    @JsonProperty("totalElements")
    private long totalElements;

    @JsonProperty("totalPages")
    private int totalPages;

    @JsonProperty("last")
    private boolean last;

    public static AuditPage from(Page<AuditLogDTO> page) {
        return AuditPage.builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
