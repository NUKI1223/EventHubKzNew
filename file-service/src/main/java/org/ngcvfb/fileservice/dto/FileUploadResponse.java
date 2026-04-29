package org.ngcvfb.fileservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    private String fileKey;
    private String fileName;
    private String fileUrl;
    private String contentType;
    private long size;
}
