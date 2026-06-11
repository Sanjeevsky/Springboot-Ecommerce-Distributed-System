package com.sanjeevsky.orderservice.service;

import com.sanjeevsky.orderservice.model.analytics.AnalyticsSummary;
import com.sanjeevsky.orderservice.model.analytics.DailyAnalyticsPoint;
import com.sanjeevsky.orderservice.model.analytics.TopProductAnalytics;

import java.time.LocalDate;
import java.util.List;

public interface OrderAnalyticsService {
    AnalyticsSummary getSummary(LocalDate from, LocalDate to);
    List<DailyAnalyticsPoint> getDaily(int days);
    List<TopProductAnalytics> getTopProducts(int limit);
}
