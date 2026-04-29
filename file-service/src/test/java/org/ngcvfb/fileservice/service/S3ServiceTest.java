package org.ngcvfb.fileservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.fileservice.dto.FileDeleteResponse;
import org.ngcvfb.fileservice.dto.FileUploadResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private S3Service service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(service, "region", "eu-north-1");
        ReflectionTestUtils.setField(service, "publicUrl", "");
        ReflectionTestUtils.setField(service, "allowedExtensions", "jpg,jpeg,png,gif,webp,pdf,doc,docx");
        ReflectionTestUtils.setField(service, "maxSizeMb", 10);
        ReflectionTestUtils.setField(service, "imageExtensions", "jpg,jpeg,png,gif,webp");
    }

    private MultipartFile image(String name, byte[] content) {
        return new MockMultipartFile("file", name, "image/png", content);
    }

    @Test
    void uploadFile_withValidImage_returnsResponseAndCallsS3() throws IOException {
        MultipartFile file = image("photo.png", "data".getBytes());

        FileUploadResponse response = service.uploadFile(file, "uploads");

        assertNotNull(response);
        assertTrue(response.getFileKey().startsWith("uploads/"));
        assertTrue(response.getFileKey().endsWith(".png"));
        assertEquals("photo.png", response.getFileName());
        assertEquals("image/png", response.getContentType());
        assertEquals(4L, response.getSize());
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadFile_emptyFile_throwsIllegalArgument() {
        MultipartFile empty = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.uploadFile(empty, "uploads"));
        assertTrue(ex.getMessage().contains("пустым"));
        verifyNoInteractions(s3Client);
    }

    @Test
    void uploadFile_oversizedFile_throwsIllegalArgument() {
        byte[] big = new byte[11 * 1024 * 1024];
        MultipartFile file = image("big.png", big);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.uploadFile(file, "uploads"));
        assertTrue(ex.getMessage().contains("превышает"));
        verifyNoInteractions(s3Client);
    }

    @Test
    void uploadFile_disallowedExtension_throwsIllegalArgument() {
        MultipartFile exe = new MockMultipartFile("file", "trojan.exe",
                "application/octet-stream", "x".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.uploadFile(exe, "uploads"));
        assertTrue(ex.getMessage().contains("формат"));
        verifyNoInteractions(s3Client);
    }

    @Test
    void uploadUserAvatar_pdf_throwsBecauseNotImage() {
        MultipartFile pdf = new MockMultipartFile("file", "doc.pdf",
                "application/pdf", "x".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.uploadUserAvatar(pdf, 42L));
        assertTrue(ex.getMessage().contains("изображением"));
        verifyNoInteractions(s3Client);
    }

    @Test
    void uploadUserAvatar_validImage_usesDeterministicKey() throws IOException {
        MultipartFile file = image("me.png", "x".getBytes());

        FileUploadResponse response = service.uploadUserAvatar(file, 42L);

        assertEquals("users/42/avatar.png", response.getFileKey());
        // delete-then-put → 1 delete (for old) + 1 put
        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadEventImage_validImage_keyContainsEventIdAndUuid() throws IOException {
        MultipartFile file = image("cover.png", "x".getBytes());

        FileUploadResponse response = service.uploadEventImage(file, 7L);

        assertTrue(response.getFileKey().matches("^events/7/[0-9a-f-]+\\.png$"));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void generateFileUrl_withoutPublicUrl_usesAwsFormat() throws IOException {
        MultipartFile file = image("a.png", "x".getBytes());

        FileUploadResponse response = service.uploadFile(file, "uploads");

        assertTrue(response.getFileUrl().startsWith("https://test-bucket.s3.eu-north-1.amazonaws.com/"));
    }

    @Test
    void generateFileUrl_withPublicUrl_usesEndpointFormat() throws IOException {
        ReflectionTestUtils.setField(service, "publicUrl", "http://localhost:9100/");
        MultipartFile file = image("a.png", "x".getBytes());

        FileUploadResponse response = service.uploadFile(file, "uploads");

        assertTrue(response.getFileUrl().startsWith("http://localhost:9100/test-bucket/"));
    }

    @Test
    void deleteFile_success_returnsDeletedTrue() {
        FileDeleteResponse response = service.deleteFile("uploads/xyz.png");

        assertTrue(response.isDeleted());
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals("uploads/xyz.png", captor.getValue().key());
    }

    @Test
    void deleteFile_s3Throws_returnsDeletedFalse() {
        doThrow(S3Exception.builder().message("denied").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        FileDeleteResponse response = service.deleteFile("any/key");

        assertFalse(response.isDeleted());
        assertTrue(response.getMessage().contains("Ошибка"));
    }

    @Test
    void fileExists_present_returnsTrue() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertTrue(service.fileExists("a/b.png"));
    }

    @Test
    void fileExists_missing_returnsFalse() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("no").build());

        assertFalse(service.fileExists("missing.png"));
    }

    @Test
    void s3NotConfigured_uploadFails() {
        ReflectionTestUtils.setField(service, "s3Client", null);
        MultipartFile file = image("a.png", "x".getBytes());

        assertThrows(IllegalStateException.class, () -> service.uploadFile(file, "uploads"));
    }
}
