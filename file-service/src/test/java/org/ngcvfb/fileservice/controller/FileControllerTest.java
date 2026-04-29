package org.ngcvfb.fileservice.controller;

import org.junit.jupiter.api.Test;
import org.ngcvfb.fileservice.dto.FileDeleteResponse;
import org.ngcvfb.fileservice.dto.FileUploadResponse;
import org.ngcvfb.fileservice.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private S3Service s3Service;

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "x.png", "image/png", "data".getBytes());
    }

    @Test
    void uploadFile_success_returns200WithBody() throws Exception {
        FileUploadResponse response = FileUploadResponse.builder()
                .fileKey("uploads/abc.png")
                .fileUrl("http://localhost:9100/test/uploads/abc.png")
                .build();
        when(s3Service.uploadFile(any(), anyString())).thenReturn(response);

        mockMvc.perform(multipart("/api/files/upload").file(png()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileKey").value("uploads/abc.png"))
                .andExpect(jsonPath("$.fileUrl").exists());
    }

    @Test
    void uploadFile_validationFailure_returns400() throws Exception {
        when(s3Service.uploadFile(any(), anyString()))
                .thenThrow(new IllegalArgumentException("Файл не может быть пустым"));

        mockMvc.perform(multipart("/api/files/upload").file(png()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Файл не может быть пустым"));
    }

    @Test
    void uploadFile_ioException_returns500() throws Exception {
        when(s3Service.uploadFile(any(), anyString())).thenThrow(new IOException("disk full"));

        mockMvc.perform(multipart("/api/files/upload").file(png()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Ошибка загрузки файла"));
    }

    @Test
    void uploadAvatar_success_returns200() throws Exception {
        FileUploadResponse response = FileUploadResponse.builder()
                .fileKey("users/5/avatar.png")
                .fileUrl("http://x/users/5/avatar.png")
                .build();
        when(s3Service.uploadUserAvatar(any(), anyLong())).thenReturn(response);

        mockMvc.perform(multipart("/api/files/users/5/avatar").file(png()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileKey").value("users/5/avatar.png"));
    }

    @Test
    void uploadAvatar_notImage_returns400() throws Exception {
        when(s3Service.uploadUserAvatar(any(), anyLong()))
                .thenThrow(new IllegalArgumentException("Файл должен быть изображением"));

        mockMvc.perform(multipart("/api/files/users/5/avatar").file(png()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Файл должен быть изображением"));
    }

    @Test
    void uploadEventImage_success_returns200() throws Exception {
        FileUploadResponse response = FileUploadResponse.builder()
                .fileKey("events/7/abc.png")
                .build();
        when(s3Service.uploadEventImage(any(), anyLong())).thenReturn(response);

        mockMvc.perform(multipart("/api/files/events/7/image").file(png()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileKey").value("events/7/abc.png"));
    }

    @Test
    void deleteFile_success_returns200() throws Exception {
        FileDeleteResponse response = FileDeleteResponse.builder()
                .fileKey("uploads/abc.png")
                .deleted(true)
                .message("Файл успешно удалён")
                .build();
        when(s3Service.deleteFile("uploads/abc.png")).thenReturn(response);

        mockMvc.perform(delete("/api/files").param("key", "uploads/abc.png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));
    }

    @Test
    void deleteFile_failure_returns500() throws Exception {
        FileDeleteResponse response = FileDeleteResponse.builder()
                .fileKey("uploads/abc.png")
                .deleted(false)
                .message("denied")
                .build();
        when(s3Service.deleteFile(anyString())).thenReturn(response);

        mockMvc.perform(delete("/api/files").param("key", "uploads/abc.png"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.deleted").value(false));
    }

    @Test
    void exists_returnsBoolean() throws Exception {
        when(s3Service.fileExists("a.png")).thenReturn(true);

        mockMvc.perform(get("/api/files/exists").param("key", "a.png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));
    }

    @Test
    void health_returns200() throws Exception {
        mockMvc.perform(get("/api/files/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("file-service"));
    }
}
