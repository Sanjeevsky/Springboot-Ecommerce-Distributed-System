package com.sanjeevsky.orderservice.controller;

import com.sanjeevsky.orderservice.model.analytics.AnalyticsSummary;
import com.sanjeevsky.orderservice.model.analytics.DailyAnalyticsPoint;
import com.sanjeevsky.orderservice.model.analytics.TopProductAnalytics;
import com.sanjeevsky.orderservice.service.OrderAnalyticsService;
import com.sanjeevsky.platform.response.ApiResponse;
import com.sanjeevsky.platform.security.AdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@AdminOnly
@RestController
@RequestMapping("/order-service/analytics")
@RequiredArgsConstructor
public class OrderAnalyticsController {

    private final OrderAnalyticsService analyticsService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AnalyticsSummary>> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getSummary(from, to)));
    }

    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<List<DailyAnalyticsPoint>>> getDaily(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getDaily(days)));
    }

    @GetMapping("/top-products")
    public ResponseEntity<ApiResponse<List<TopProductAnalytics>>> getTopProducts(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getTopProducts(limit)));
    }
}
