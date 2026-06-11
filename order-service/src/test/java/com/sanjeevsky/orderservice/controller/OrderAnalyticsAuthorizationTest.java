package com.sanjeevsky.orderservice.controller;

import com.sanjeevsky.orderservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.orderservice.model.analytics.AnalyticsSummary;
import com.sanjeevsky.orderservice.service.OrderAnalyticsService;
import com.sanjeevsky.platform.security.AdminAuthorizationInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderAnalyticsAuthorizationTest {

    @Mock
    private OrderAnalyticsService analyticsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderAnalyticsController(analyticsService))
                .addInterceptors(new AdminAuthorizationInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void summary_withoutRoleReturns403() throws Exception {
        mockMvc.perform(get("/order-service/analytics/summary"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Administrator role is required"));

        verifyNoInteractions(analyticsService);
    }

    @Test
    void summary_customerRoleReturns403() throws Exception {
        mockMvc.perform(get("/order-service/analytics/summary")
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(analyticsService);
    }

    @Test
    void summary_adminRoleReachesService() throws Exception {
        when(analyticsService.getSummary(any(), any())).thenReturn(AnalyticsSummary.builder()
                .from(LocalDate.now())
                .to(LocalDate.now())
                .statusBreakdown(Map.of())
                .build());

        mockMvc.perform(get("/order-service/analytics/summary")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        verify(analyticsService).getSummary(null, null);
    }
}
