package com.sanjeevsky.orderservice.model.analytics;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class TopProductAnalytics {
    UUID productId;
    String productName;
    long quantity;
    double revenue;
}
