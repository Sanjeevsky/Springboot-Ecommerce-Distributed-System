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
class PaymentEventConsumerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentEventConsumer consumer;

    private static final String ORDER_ID = "order-abc";
    private static final String PAYMENT_ID = "pay-001";
    private static final String USER_ID = "user@example.com";

    @Test
    void consume_paymentPayload_savesPaymentProcessedNotification() {
        String payload = "{\"paymentId\":\"" + PAYMENT_ID + "\",\"orderId\":\"" + ORDER_ID + "\","
                + "\"userId\":\"" + USER_ID + "\",\"amount\":750.0}";

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getEventKey()).isEqualTo("payment:" + PAYMENT_ID + ":PAYMENT_PROCESSED");
        assertThat(saved.getType()).isEqualTo("PAYMENT_PROCESSED");
        assertThat(saved.getMessage()).contains(ORDER_ID);
    }

    @Test
    void consume_missingUserId_usesUnknownFallback() {
        String payload = "{\"paymentId\":\"" + PAYMENT_ID + "\",\"orderId\":\"" + ORDER_ID + "\",\"amount\":200.0}";

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("unknown");
    }

    @Test
    void consume_confirmedEventType_usesSpecificNotificationType() {
        String payload = "{\"paymentId\":\"" + PAYMENT_ID + "\",\"orderId\":\"" + ORDER_ID + "\","
                + "\"eventType\":\"PAYMENT_CONFIRMED\",\"amount\":200.0}";

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("PAYMENT_CONFIRMED");
        assertThat(captor.getValue().getSubject()).isEqualTo("Payment Confirmed");
        assertThat(captor.getValue().getEventKey()).isEqualTo("payment:" + PAYMENT_ID + ":PAYMENT_CONFIRMED");
    }

    @Test
    void consume_duplicateEventKey_skipsSave() {
        String payload = "{\"paymentId\":\"" + PAYMENT_ID + "\",\"orderId\":\"" + ORDER_ID + "\","
                + "\"eventType\":\"PAYMENT_REFUNDED\",\"amount\":200.0}";
        when(notificationRepository.existsByEventKey("payment:" + PAYMENT_ID + ":PAYMENT_REFUNDED"))
                .thenReturn(true);

        consumer.consume(payload);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void consume_invalidJson_throwsForRetryAndDoesNotSave() {
        assertThatThrownBy(() -> consumer.consume("{broken"))
                .isInstanceOf(IllegalStateException.class);

        verify(notificationRepository, never()).save(any());
    }
}
