package com.sanjeevsky.reviewservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.reviewservice.model.OrderEligibility;
import com.sanjeevsky.reviewservice.repository.OrderEligibilityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private OrderEligibilityRepository eligibilityRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderEventConsumer consumer;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_1 = UUID.randomUUID();
    private static final UUID PRODUCT_2 = UUID.randomUUID();
    private static final String USER_ID = "buyer@example.com";

    // ─── OrderPlacedEvent ─────────────────────────────────────────────────────

    @Test
    void consume_orderPlaced_persistsEligibilityForEachProduct() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\","
                + "\"totalAmount\":300.0,"
                + "\"items\":["
                + "{\"productId\":\"" + PRODUCT_1 + "\",\"qty\":1},"
                + "{\"productId\":\"" + PRODUCT_2 + "\",\"qty\":2}"
                + "]}";

        when(eligibilityRepository.existsByUserIdAndProductId(eq(USER_ID), any(UUID.class))).thenReturn(false);
        when(eligibilityRepository.save(any(OrderEligibility.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consumeOrderEvent(payload);

        verify(eligibilityRepository, times(2)).save(any(OrderEligibility.class));
    }

    @Test
    void consume_orderPlaced_setsCorrectFields() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\","
                + "\"totalAmount\":150.0,"
                + "\"items\":[{\"productId\":\"" + PRODUCT_1 + "\",\"qty\":1}]}";

        when(eligibilityRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_1)).thenReturn(false);
        when(eligibilityRepository.save(any(OrderEligibility.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consumeOrderEvent(payload);

        ArgumentCaptor<OrderEligibility> captor = ArgumentCaptor.forClass(OrderEligibility.class);
        verify(eligibilityRepository).save(captor.capture());
        OrderEligibility saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getProductId()).isEqualTo(PRODUCT_1);
        assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
    }

    @Test
    void consume_orderPlaced_skipsIfEligibilityAlreadyExists() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\","
                + "\"totalAmount\":100.0,"
                + "\"items\":[{\"productId\":\"" + PRODUCT_1 + "\",\"qty\":1}]}";

        when(eligibilityRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_1)).thenReturn(true);

        consumer.consumeOrderEvent(payload);

        verify(eligibilityRepository, never()).save(any());
    }

    // ─── Non-OrderPlacedEvent (no items field) ────────────────────────────────

    @Test
    void consume_orderConfirmedEvent_noItems_doesNothing() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\","
                + "\"totalAmount\":300.0}";

        consumer.consumeOrderEvent(payload);

        verifyNoInteractions(eligibilityRepository);
    }

    @Test
    void consume_orderCancelledEvent_doesNothing() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\","
                + "\"reason\":\"Out of stock\"}";

        consumer.consumeOrderEvent(payload);

        verifyNoInteractions(eligibilityRepository);
    }

    // ─── error handling ───────────────────────────────────────────────────────

    @Test
    void consume_invalidJson_doesNotThrow() {
        consumer.consumeOrderEvent("not-json");

        verifyNoInteractions(eligibilityRepository);
    }

    @Test
    void consume_missingUserId_skipsProcessing() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\","
                + "\"items\":[{\"productId\":\"" + PRODUCT_1 + "\",\"qty\":1}]}";

        consumer.consumeOrderEvent(payload);

        verify(eligibilityRepository, never()).save(any());
    }
}
