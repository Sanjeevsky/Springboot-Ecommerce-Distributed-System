package com.sanjeevsky.orderservice.service;

import com.sanjeevsky.orderservice.exceptions.InvalidRequestException;
import com.sanjeevsky.orderservice.model.Order;
import com.sanjeevsky.orderservice.model.OrderItem;
import com.sanjeevsky.orderservice.model.analytics.AnalyticsSummary;
import com.sanjeevsky.orderservice.model.analytics.DailyAnalyticsPoint;
import com.sanjeevsky.orderservice.model.analytics.TopProductAnalytics;
import com.sanjeevsky.orderservice.repository.OrderRepository;
import com.sanjeevsky.orderservice.service.impl.OrderAnalyticsServiceImpl;
import com.sanjeevsky.platform.model.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAnalyticsServiceImplTest {

    private static final UUID PHONE_ID = UUID.randomUUID();
    private static final UUID WATCH_ID = UUID.randomUUID();

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderAnalyticsServiceImpl analyticsService;

    @Test
    void getSummary_countsStatusesAndRecognizedRevenue() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 7);
        when(orderRepository.findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(
                        order(OrderStatus.CONFIRMED, 100.125, from.atTime(10, 0)),
                        order(OrderStatus.DELIVERED, 50.0, from.plusDays(1).atTime(11, 0)),
                        order(OrderStatus.PENDING, 200.0, from.plusDays(2).atTime(12, 0)),
                        order(OrderStatus.CANCELLED, 300.0, from.plusDays(3).atTime(13, 0))));

        AnalyticsSummary result = analyticsService.getSummary(from, to);

        assertThat(result.getRevenue()).isEqualTo(150.13);
        assertThat(result.getOrderCount()).isEqualTo(4);
        assertThat(result.getAverageOrderValue()).isEqualTo(75.07);
        assertThat(result.getStatusBreakdown())
                .containsEntry(OrderStatus.CONFIRMED, 1L)
                .containsEntry(OrderStatus.DELIVERED, 1L)
                .containsEntry(OrderStatus.PENDING, 1L)
                .containsEntry(OrderStatus.CANCELLED, 1L)
                .containsEntry(OrderStatus.SHIPPED, 0L);
    }

    @Test
    void getSummary_rejectsReverseRange() {
        assertThatThrownBy(() -> analyticsService.getSummary(
                LocalDate.of(2026, 6, 8),
                LocalDate.of(2026, 6, 1)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("on or before");

        verifyNoInteractions(orderRepository);
    }

    @Test
    void getDaily_fillsMissingDaysAndExcludesPendingRevenue() {
        LocalDate today = LocalDate.now();
        when(orderRepository.findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                today.minusDays(2).atStartOfDay(), today.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(
                        order(OrderStatus.CONFIRMED, 120.0, today.minusDays(2).atTime(9, 0)),
                        order(OrderStatus.PENDING, 80.0, today.atTime(10, 0))));

        List<DailyAnalyticsPoint> result = analyticsService.getDaily(3);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getDate()).isEqualTo(today.minusDays(2));
        assertThat(result.get(0).getRevenue()).isEqualTo(120.0);
        assertThat(result.get(0).getOrderCount()).isEqualTo(1);
        assertThat(result.get(1).getRevenue()).isZero();
        assertThat(result.get(1).getOrderCount()).isZero();
        assertThat(result.get(2).getRevenue()).isZero();
        assertThat(result.get(2).getOrderCount()).isEqualTo(1);
    }

    @Test
    void getDaily_rejectsUnsupportedWindow() {
        assertThatThrownBy(() -> analyticsService.getDaily(366))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("between 1 and 365");

        verifyNoInteractions(orderRepository);
    }

    @Test
    void getTopProducts_aggregatesConfirmedItemsAndSortsByQuantity() {
        when(orderRepository.findAllByStatusIn(any())).thenReturn(List.of(
                orderWithTotal(1080,
                        item(PHONE_ID, "Phone", 500.0, 2),
                        item(WATCH_ID, "Watch", 200.0, 1)),
                orderWithTotal(500, item(PHONE_ID, "Phone", 500.0, 1))));

        List<TopProductAnalytics> result = analyticsService.getTopProducts(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductId()).isEqualTo(PHONE_ID);
        assertThat(result.get(0).getProductName()).isEqualTo("Phone");
        assertThat(result.get(0).getQuantity()).isEqualTo(3);
        assertThat(result.get(0).getRevenue()).isEqualTo(1400.0);
        verify(orderRepository).findAllByStatusIn(any());
    }

    @Test
    void getTopProducts_rejectsInvalidLimit() {
        assertThatThrownBy(() -> analyticsService.getTopProducts(0))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("between 1 and 100");

        verifyNoInteractions(orderRepository);
    }

    private Order order(OrderStatus status, double total, LocalDateTime createdAt) {
        return Order.builder()
                .status(status)
                .orderTotal(total)
                .createdAt(createdAt)
                .build();
    }

    private Order orderWithTotal(double total, OrderItem... items) {
        return Order.builder()
                .status(OrderStatus.CONFIRMED)
                .orderTotal(total)
                .orderItems(List.of(items))
                .build();
    }

    private OrderItem item(UUID productId, String name, double unitPrice, int qty) {
        return OrderItem.builder()
                .productId(productId)
                .productName(name)
                .unitPrice(unitPrice)
                .qty(qty)
                .build();
    }
}
