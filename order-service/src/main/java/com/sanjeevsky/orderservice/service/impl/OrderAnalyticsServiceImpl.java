package com.sanjeevsky.orderservice.service.impl;

import com.sanjeevsky.orderservice.exceptions.InvalidRequestException;
import com.sanjeevsky.orderservice.model.Order;
import com.sanjeevsky.orderservice.model.OrderItem;
import com.sanjeevsky.orderservice.model.analytics.AnalyticsSummary;
import com.sanjeevsky.orderservice.model.analytics.DailyAnalyticsPoint;
import com.sanjeevsky.orderservice.model.analytics.TopProductAnalytics;
import com.sanjeevsky.orderservice.repository.OrderRepository;
import com.sanjeevsky.orderservice.service.OrderAnalyticsService;
import com.sanjeevsky.platform.model.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderAnalyticsServiceImpl implements OrderAnalyticsService {

    private static final int DEFAULT_SUMMARY_DAYS = 30;
    private static final int MAX_DAILY_DAYS = 365;
    private static final int MAX_TOP_PRODUCTS = 100;
    private static final Set<OrderStatus> REVENUE_STATUSES =
            EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;

    @Override
    public AnalyticsSummary getSummary(LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        List<Order> orders = findOrders(range);
        EnumMap<OrderStatus, Long> breakdown = emptyBreakdown();
        double revenue = 0;
        long revenueOrderCount = 0;

        for (Order order : orders) {
            if (order.getStatus() != null) {
                breakdown.compute(order.getStatus(), (status, count) -> count == null ? 1L : count + 1);
            }
            if (isRevenueOrder(order)) {
                revenue += order.getOrderTotal();
                revenueOrderCount++;
            }
        }

        double roundedRevenue = money(revenue);
        return AnalyticsSummary.builder()
                .from(range.from)
                .to(range.to)
                .revenue(roundedRevenue)
                .orderCount(orders.size())
                .averageOrderValue(revenueOrderCount == 0 ? 0 : money(roundedRevenue / revenueOrderCount))
                .statusBreakdown(breakdown)
                .build();
    }

    @Override
    public List<DailyAnalyticsPoint> getDaily(int days) {
        if (days < 1 || days > MAX_DAILY_DAYS) {
            throw new InvalidRequestException("Analytics days must be between 1 and " + MAX_DAILY_DAYS);
        }

        LocalDate to = LocalDate.now();
        DateRange range = new DateRange(to.minusDays(days - 1L), to);
        Map<LocalDate, DailyAccumulator> daily = new LinkedHashMap<>();
        for (LocalDate date = range.from; !date.isAfter(range.to); date = date.plusDays(1)) {
            daily.put(date, new DailyAccumulator());
        }

        for (Order order : findOrders(range)) {
            if (order.getCreatedAt() == null) {
                continue;
            }
            DailyAccumulator point = daily.get(order.getCreatedAt().toLocalDate());
            if (point == null) {
                continue;
            }
            point.orderCount++;
            if (isRevenueOrder(order)) {
                point.revenue += order.getOrderTotal();
            }
        }

        List<DailyAnalyticsPoint> result = new ArrayList<>(daily.size());
        daily.forEach((date, value) -> result.add(DailyAnalyticsPoint.builder()
                .date(date)
                .revenue(money(value.revenue))
                .orderCount(value.orderCount)
                .build()));
        return result;
    }

    @Override
    public List<TopProductAnalytics> getTopProducts(int limit) {
        if (limit < 1 || limit > MAX_TOP_PRODUCTS) {
            throw new InvalidRequestException("Analytics product limit must be between 1 and " + MAX_TOP_PRODUCTS);
        }

        Map<UUID, ProductAccumulator> totals = new LinkedHashMap<>();
        for (Order order : orderRepository.findAllByStatusIn(REVENUE_STATUSES)) {
            if (order.getOrderItems() == null) {
                continue;
            }
            double grossItemTotal = order.getOrderItems().stream()
                    .mapToDouble(item -> item.getUnitPrice() * item.getQty())
                    .sum();
            double revenueFactor = grossItemTotal > 0 ? order.getOrderTotal() / grossItemTotal : 0;
            for (OrderItem item : order.getOrderItems()) {
                if (item.getProductId() == null) {
                    continue;
                }
                ProductAccumulator total = totals.computeIfAbsent(
                        item.getProductId(),
                        id -> new ProductAccumulator(id, item.getProductName()));
                total.quantity += item.getQty();
                total.revenue += item.getUnitPrice() * item.getQty() * revenueFactor;
                if ((total.productName == null || total.productName.isBlank())
                        && item.getProductName() != null) {
                    total.productName = item.getProductName();
                }
            }
        }

        return totals.values().stream()
                .sorted(Comparator.comparingLong(ProductAccumulator::getQuantity).reversed()
                        .thenComparing(Comparator.comparingDouble(ProductAccumulator::getRevenue).reversed())
                        .thenComparing(value -> value.productId.toString()))
                .limit(limit)
                .map(total -> TopProductAnalytics.builder()
                        .productId(total.productId)
                        .productName(total.productName == null || total.productName.isBlank()
                                ? "Unnamed product"
                                : total.productName)
                        .quantity(total.quantity)
                        .revenue(money(total.revenue))
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    private List<Order> findOrders(DateRange range) {
        return orderRepository.findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                range.from.atStartOfDay(),
                range.to.plusDays(1).atStartOfDay());
    }

    private DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to == null ? LocalDate.now() : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(DEFAULT_SUMMARY_DAYS - 1L) : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new InvalidRequestException("Analytics from date must be on or before to date");
        }
        return new DateRange(resolvedFrom, resolvedTo);
    }

    private boolean isRevenueOrder(Order order) {
        return order.getStatus() != null && REVENUE_STATUSES.contains(order.getStatus());
    }

    private EnumMap<OrderStatus, Long> emptyBreakdown() {
        EnumMap<OrderStatus, Long> breakdown = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            breakdown.put(status, 0L);
        }
        return breakdown;
    }

    private double money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static class DateRange {
        private final LocalDate from;
        private final LocalDate to;

        private DateRange(LocalDate from, LocalDate to) {
            this.from = from;
            this.to = to;
        }
    }

    private static class DailyAccumulator {
        private double revenue;
        private long orderCount;
    }

    private static class ProductAccumulator {
        private final UUID productId;
        private String productName;
        private long quantity;
        private double revenue;

        private ProductAccumulator(UUID productId, String productName) {
            this.productId = productId;
            this.productName = productName;
        }

        private long getQuantity() {
            return quantity;
        }

        private double getRevenue() {
            return revenue;
        }
    }
}
