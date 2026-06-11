package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.catalogservice.service.AuditService;
import com.sanjeevsky.platform.security.AdminAuthorizationInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @Mock
    private AuditService auditService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuditController(auditService))
                .addInterceptors(new AdminAuthorizationInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listAudit_withoutRole_returns403() throws Exception {
        mockMvc.perform(get("/catalog-service/audit"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(auditService);
    }

    @Test
    void listAudit_customerRole_returns403() throws Exception {
        mockMvc.perform(get("/catalog-service/audit").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(auditService);
    }

    @Test
    void listAudit_adminRole_reachesController() throws Exception {
        when(auditService.list(any(), eq(0), eq(20))).thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/catalog-service/audit").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        verify(auditService).list(null, 0, 20);
    }
}
