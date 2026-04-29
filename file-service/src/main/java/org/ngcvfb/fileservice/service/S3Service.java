package org.ngcvfb.fileservice.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.fileservice.dto.FileDeleteResponse;
import org.ngcvfb.fileservice.dto.FileUploadResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class S3Service {

    @Autowired(required = false)
    private S3Client s3Client;

    @Value("${aws.s3.bucket-name:eventhubkz-files}")
    private String bucketName;

    @Value("${aws.s3.region:eu-north-1}")
    private String region;

    @Value("${file.upload.allowed-extensions:jpg,jpeg,png,gif,webp,pdf,doc,docx}")
    private String allowedExtensions;

    @Value("${file.upload.max-size-mb:10}")
    private int maxSizeMb;

    @Value("${file.upload.image-extensions:jpg,jpeg,png,gif,webp}")
    private String imageExtensions;

    @PostConstruct
    public void init() {
        if (s3Client == null) {
            log.warn("S3Client is not configured. File upload functionality will be disabled.");
        } else {
            log.info("S3Client initialized successfully");
        }
    }

    private void checkS3Available() {
        if (s3Client == null) {
            throw new IllegalStateException("S3 не настроен. Добавьте AWS credentials в конфигурацию.");
        }
    }

    public FileUploadResponse uploadFile(MultipartFile file, String folder) throws IOException {
        checkS3Available();
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        String fileKey = generateFileKey(folder, extension);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        String fileUrl = generateFileUrl(fileKey);

        log.info("File uploaded successfully: key={}, size={}", fileKey, file.getSize());

        return FileUploadResponse.builder()
                .fileKey(fileKey)
                .fileName(originalFilename)
                .fileUrl(fileUrl)
                .contentType(file.getContentType())
                .size(file.getSize())
                .build();
    }

    public FileUploadResponse uploadEventImage(MultipartFile file, Long eventId) throws IOException {
        checkS3Available();
        validateImageFile(file);

        String extension = getExtension(file.getOriginalFilename());
        String fileKey = String.format("events/%d/%s.%s", eventId, UUID.randomUUID(), extension);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        String fileUrl = generateFileUrl(fileKey);

        log.info("Event image uploaded: eventId={}, key={}", eventId, fileKey);

        return FileUploadResponse.builder()
                .fileKey(fileKey)
                .fileName(file.getOriginalFilename())
                .fileUrl(fileUrl)
                .contentType(file.getContentType())
                .size(file.getSize())
                .build();
    }

    public FileUploadResponse uploadUserAvatar(MultipartFile file, Long userId) throws IOException {
        checkS3Available();
        validateImageFile(file);

        String extension = getExtension(file.getOriginalFilename());
        String fileKey = String.format("users/%d/avatar.%s", userId, extension);

        // Delete old avatar if exists
        try {
            deleteFile(fileKey);
        } catch (Exception ignored) {
        }

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        String fileUrl = generateFileUrl(fileKey);

        log.info("User avatar uploaded: userId={}, key={}", userId, fileKey);

        return FileUploadResponse.builder()
                .fileKey(fileKey)
                .fileName(file.getOriginalFilename())
                .fileUrl(fileUrl)
                .contentType(file.getContentType())
                .size(file.getSize())
                .build();
    }

    public FileDeleteResponse deleteFile(String fileKey) {
        if (s3Client == null) {
            return FileDeleteResponse.builder()
                    .fileKey(fileKey)
                    .deleted(false)
                    .message("S3 не настроен")
                    .build();
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            s3Client.deleteObject(deleteRequest);

            log.info("File deleted: key={}", fileKey);

            return FileDeleteResponse.builder()
                    .fileKey(fileKey)
                    .deleted(true)
                    .message("Файл успешно удалён")
                    .build();
        } catch (S3Exception e) {
            log.error("Failed to delete file: key={}, error={}", fileKey, e.getMessage());
            return FileDeleteResponse.builder()
                    .fileKey(fileKey)
                    .deleted(false)
                    .message("Ошибка при удалении файла: " + e.getMessage())
                    .build();
        }
    }

    public List<FileDeleteResponse> deleteFiles(List<String> fileKeys) {
        return fileKeys.stream()
                .map(this::deleteFile)
                .collect(Collectors.toList());
    }

    public boolean fileExists(String fileKey) {
        if (s3Client == null) {
            return false;
        }

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    public boolean isConfigured() {
        return s3Client != null;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл не может быть пустым");
        }

        if (file.getSize() > maxSizeMb * 1024 * 1024L) {
            throw new IllegalArgumentException("Размер файла превышает " + maxSizeMb + " MB");
        }

        String extension = getExtension(file.getOriginalFilename());
        Set<String> allowed = Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        if (!allowed.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("Недопустимый формат файла. Разрешены: " + allowedExtensions);
        }
    }

    private void validateImageFile(MultipartFile file) {
        validateFile(file);

        String extension = getExtension(file.getOriginalFilename());
        Set<String> imageExts = Arrays.stream(imageExtensions.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        if (!imageExts.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("Файл должен быть изображением. Разрешены: " + imageExtensions);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private String generateFileKey(String folder, String extension) {
        String uuid = UUID.randomUUID().toString();
        if (folder != null && !folder.isBlank()) {
            return String.format("%s/%s.%s", folder.replaceAll("^/|/$", ""), uuid, extension);
        }
        return String.format("uploads/%s.%s", uuid, extension);
    }

    private String generateFileUrl(String fileKey) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, fileKey);
    }
}
