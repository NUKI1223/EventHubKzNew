package org.ngcvfb.auditservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditLog;
import org.ngcvfb.auditservice.service.AuditRecordService;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @Mock AuditRecordService recordService;
    @InjectMocks AuditController controller;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void forbiddenWithoutAdminRole() throws Exception {
        mvc.perform(get("/api/admin/audit").header("X-User-Role", "USER"))
           .andExpect(status().isForbidden());
    }

    @Test
    void returnsPageForAdmin() throws Exception {
        AuditLog row = AuditLog.builder()
                .id(1L).action(AuditAction.EVENT_LIKED).actorId(1L).actorName("aidar")
                .occurredAt(LocalDateTime.now()).dedupKey("t:0:1").build();
        when(recordService.search(eq(AuditAction.EVENT_LIKED), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row)));

        mvc.perform(get("/api/admin/audit")
                        .header("X-User-Role", "ADMIN")
                        .param("action", "EVENT_LIKED"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.content[0].actorName").value("aidar"))
           .andExpect(jsonPath("$.content[0].action").value("EVENT_LIKED"));
    }
}
