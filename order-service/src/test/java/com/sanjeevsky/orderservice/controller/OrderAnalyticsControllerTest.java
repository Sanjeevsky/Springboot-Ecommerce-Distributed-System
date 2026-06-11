package com.sanjeevsky.orderservice.controller;

import com.sanjeevsky.orderservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.orderservice.model.analytics.AnalyticsSummary;
import com.sanjeevsky.orderservice.model.analytics.DailyAnalyticsPoint;
import com.sanjeevsky.orderservice.model.analytics.TopProductAnalytics;
import com.sanjeevsky.orderservice.service.OrderAnalyticsService;
import com.sanjeevsky.platform.model.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderAnalyticsControllerTest {

    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 7);

    @Mock
    private OrderAnalyticsService analyticsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderAnalyticsController(analyticsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getSummary_passesDateRangeAndReturnsContract() throws Exception {
        when(analyticsService.getSummary(FROM, TO)).thenReturn(AnalyticsSummary.builder()
                .from(FROM)
                .to(TO)
                .revenue(1250.5)
                .orderCount(4)
                .averageOrderValue(625.25)
                .statusBreakdown(Map.of(OrderStatus.CONFIRMED, 2L))
                .build());

        mockMvc.perform(get("/order-service/analytics/summary")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.revenue").value(1250.5))
                .andExpect(jsonPath("$.data.orderCount").value(4))
                .andExpect(jsonPath("$.data.statusBreakdown.CONFIRMED").value(2));

        verify(analyticsService).getSummary(FROM, TO);
    }

    @Test
    void getSummary_invalidDateReturns400() throws Exception {
        mockMvc.perform(get("/order-service/analytics/summary")
                        .param("from", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid value for from"));

        verifyNoInteractions(analyticsService);
    }

    @Test
    void getDaily_passesDaysAndReturnsPoints() throws Exception {
        when(analyticsService.getDaily(7)).thenReturn(List.of(DailyAnalyticsPoint.builder()
                .date(TO)
                .revenue(250)
                .orderCount(2)
                .build()));

        mockMvc.perform(get("/order-service/analytics/daily").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].date").value(TO.toString()))
                .andExpect(jsonPath("$.data[0].revenue").value(250))
                .andExpect(jsonPath("$.data[0].orderCount").value(2));

        verify(analyticsService).getDaily(7);
    }

    @Test
    void getTopProducts_passesLimitAndReturnsRows() throws Exception {
        UUID productId = UUID.randomUUID();
        when(analyticsService.getTopProducts(5)).thenReturn(List.of(TopProductAnalytics.builder()
                .productId(productId)
                .productName("Phone")
                .quantity(3)
                .revenue(1500)
                .build()));

        mockMvc.perform(get("/order-service/analytics/top-products").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.data[0].productName").value("Phone"))
                .andExpect(jsonPath("$.data[0].quantity").value(3));

        verify(analyticsService).getTopProducts(5);
    }
}
