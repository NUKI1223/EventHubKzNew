package org.ngcvfb.fileservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.fileservice.dto.FileDeleteResponse;
import org.ngcvfb.fileservice.dto.FileUploadResponse;
import org.ngcvfb.fileservice.service.S3Service;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final S3Service s3Service;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "uploads") String folder) {
        try {
            FileUploadResponse response = s3Service.uploadFile(file, folder);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid file upload attempt: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("File upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ошибка загрузки файла"));
        }
    }

    @PostMapping(value = "/events/{eventId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadEventImage(
            @PathVariable Long eventId,
            @RequestParam("file") MultipartFile file) {
        try {
            FileUploadResponse response = s3Service.uploadEventImage(file, eventId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid event image upload: eventId={}, error={}", eventId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Event image upload failed: eventId={}", eventId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ошибка загрузки изображения"));
        }
    }

    @PostMapping(value = "/users/{userId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadUserAvatar(
            @PathVariable Long userId,
            @RequestParam("file") MultipartFile file) {
        try {
            FileUploadResponse response = s3Service.uploadUserAvatar(file, userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid avatar upload: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Avatar upload failed: userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ошибка загрузки аватара"));
        }
    }

    @DeleteMapping
    public ResponseEntity<FileDeleteResponse> deleteFile(@RequestParam("key") String fileKey) {
        FileDeleteResponse response = s3Service.deleteFile(fileKey);
        if (response.isDeleted()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @DeleteMapping("/batch")
    public ResponseEntity<List<FileDeleteResponse>> deleteFiles(@RequestBody List<String> fileKeys) {
        List<FileDeleteResponse> responses = s3Service.deleteFiles(fileKeys);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/exists")
    public ResponseEntity<Map<String, Boolean>> checkFileExists(@RequestParam("key") String fileKey) {
        boolean exists = s3Service.fileExists(fileKey);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "file-service"));
    }
}
