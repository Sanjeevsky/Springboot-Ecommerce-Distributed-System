package com.sanjeevsky.notificationservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.notificationservice.model.Notification;
import com.sanjeevsky.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderEventConsumer consumer;

    private static final String ORDER_ID = "order-123";
    private static final String USER_ID = "user@example.com";

    // ─── OrderPlacedEvent ─────────────────────────────────────────────────────

    @Test
    void consume_orderPlacedPayload_savesOrderPlacedNotification() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\","
                + "\"totalAmount\":499.99,"
                + "\"items\":[{\"productName\":\"Shirt\",\"qty\":2},{\"productName\":\"Pants\",\"qty\":1}]}";

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getEventKey()).isEqualTo("order:" + ORDER_ID + ":ORDER_PLACED");
        assertThat(saved.getType()).isEqualTo("ORDER_PLACED");
        assertThat(saved.getMessage()).contains("499.99");
        assertThat(saved.getMessage()).contains("Shirt x 2");
        assertThat(saved.getMessage()).contains("Pants x 1");
    }

    // ─── OrderCancelledEvent ──────────────────────────────────────────────────

    @Test
    void consume_orderCancelledPayload_savesOrderCancelledNotification() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\","
                + "\"reason\":\"Out of stock\"}";

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo("ORDER_CANCELLED");
        assertThat(saved.getMessage()).contains("Out of stock");
    }

    // ─── OrderConfirmedEvent ──────────────────────────────────────────────────

    @Test
    void consume_orderConfirmedPayload_savesOrderConfirmedNotification() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\"}";

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("ORDER_CONFIRMED");
    }

    // ─── error handling ───────────────────────────────────────────────────────

    @Test
    void consume_invalidJson_throwsForRetryAndDoesNotSave() {
        assertThatThrownBy(() -> consumer.consume("not-json-at-all"))
                .isInstanceOf(IllegalStateException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void consume_missingUserId_usesUnknownFallback() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\","
                + "\"items\":[{\"productName\":\"Shoes\",\"qty\":1}],"
                + "\"totalAmount\":100.0}";

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("unknown");
    }

    @Test
    void consume_duplicateEventKey_skipsSave() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\"}";
        when(notificationRepository.existsByEventKey("order:" + ORDER_ID + ":ORDER_CONFIRMED"))
                .thenReturn(true);

        consumer.consume(payload);

        verify(notificationRepository, never()).save(any());
    }
}
