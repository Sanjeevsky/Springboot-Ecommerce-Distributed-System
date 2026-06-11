package com.sanjeevsky.orderservice.model.analytics;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sanjeevsky.platform.model.order.OrderStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.Map;

@Value
@Builder
public class AnalyticsSummary {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate from;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate to;
    double revenue;
    long orderCount;
    double averageOrderValue;
    Map<OrderStatus, Long> statusBreakdown;
}
