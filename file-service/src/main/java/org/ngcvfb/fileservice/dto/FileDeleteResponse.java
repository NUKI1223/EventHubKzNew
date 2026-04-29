package org.ngcvfb.fileservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDeleteResponse {
    private String fileKey;
    private boolean deleted;
    private String message;
}
