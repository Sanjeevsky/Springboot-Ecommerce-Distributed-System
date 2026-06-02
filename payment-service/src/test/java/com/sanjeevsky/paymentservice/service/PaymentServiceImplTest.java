package com.sanjeevsky.paymentservice.service;

import com.sanjeevsky.paymentservice.events.PaymentEventPublisher;
import com.sanjeevsky.paymentservice.exceptions.InvalidPaymentTransitionException;
import com.sanjeevsky.paymentservice.exceptions.PaymentNotFoundException;
import com.sanjeevsky.paymentservice.model.Payment;
import com.sanjeevsky.paymentservice.model.PaymentRequest;
import com.sanjeevsky.paymentservice.model.PaymentStatus;
import com.sanjeevsky.paymentservice.repository.PaymentRepository;
import com.sanjeevsky.paymentservice.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentEventPublisher eventPublisher;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String USER_ID = "user@example.com";

    private Payment pendingPayment() {
        return Payment.builder()
                .id(PAYMENT_ID)
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .amount(250.0)
                .currency("INR")
                .status(PaymentStatus.PENDING)
                .build();
    }

    // ─── initiatePayment ───────────────────────────────────────────────────────

    @Test
    void initiatePayment_savesPaymentWithPendingStatus() {
        PaymentRequest req = new PaymentRequest(ORDER_ID, USER_ID, 250.0);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.initiatePayment(req);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getAmount()).isEqualTo(250.0);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void initiatePayment_withIdempotencyKey_savesTrimmedKey() {
        PaymentRequest req = new PaymentRequest(ORDER_ID, USER_ID, 250.0);
        req.setIdempotencyKey(" payment-1 ");
        when(paymentRepository.findByUserIdAndIdempotencyKey(USER_ID, "payment-1")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.initiatePayment(req);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("payment-1");
        assertThat(result.getIdempotencyKey()).isEqualTo("payment-1");
        verify(eventPublisher).publishPaymentInitiated(any());
    }

    @Test
    void initiatePayment_withSameIdempotencyKey_returnsExistingPaymentWithoutPublishing() {
        Payment existing = pendingPayment();
        existing.setIdempotencyKey("payment-1");
        PaymentRequest req = new PaymentRequest(ORDER_ID, USER_ID, 250.0);
        req.setIdempotencyKey("payment-1");
        when(paymentRepository.findByUserIdAndIdempotencyKey(USER_ID, "payment-1")).thenReturn(Optional.of(existing));

        Payment result = paymentService.initiatePayment(req);

        assertThat(result).isSameAs(existing);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // ─── confirmPayment ────────────────────────────────────────────────────────

    @Test
    void confirmPayment_changesStatusToSuccess() {
        Payment payment = pendingPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.confirmPayment(PAYMENT_ID);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentRepository).save(payment);
    }

    @Test
    void confirmPayment_alreadySuccess_returnsExistingPaymentWithoutPublishing() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        Payment result = paymentService.confirmPayment(PAYMENT_ID);

        assertThat(result).isSameAs(payment);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void confirmPayment_failed_throwsInvalidPaymentTransitionException() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.confirmPayment(PAYMENT_ID))
                .isInstanceOf(InvalidPaymentTransitionException.class)
                .hasMessageContaining("from FAILED to SUCCESS");
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void confirmPayment_refunded_throwsInvalidPaymentTransitionException() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.REFUNDED);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.confirmPayment(PAYMENT_ID))
                .isInstanceOf(InvalidPaymentTransitionException.class)
                .hasMessageContaining("from REFUNDED to SUCCESS");
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void confirmPayment_notFound_throwsPaymentNotFoundException() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.confirmPayment(PAYMENT_ID))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(PAYMENT_ID.toString());
    }

    // ─── failPayment ───────────────────────────────────────────────────────────

    @Test
    void failPayment_changesStatusToFailed() {
        Payment payment = pendingPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.failPayment(PAYMENT_ID);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void failPayment_alreadyFailed_returnsExistingPaymentWithoutSaving() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        Payment result = paymentService.failPayment(PAYMENT_ID);

        assertThat(result).isSameAs(payment);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void failPayment_success_throwsInvalidPaymentTransitionException() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.failPayment(PAYMENT_ID))
                .isInstanceOf(InvalidPaymentTransitionException.class)
                .hasMessageContaining("from SUCCESS to FAILED");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void failPayment_refunded_throwsInvalidPaymentTransitionException() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.REFUNDED);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.failPayment(PAYMENT_ID))
                .isInstanceOf(InvalidPaymentTransitionException.class)
                .hasMessageContaining("from REFUNDED to FAILED");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void failPayment_notFound_throwsPaymentNotFoundException() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.failPayment(PAYMENT_ID))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    // ─── getByPaymentId ────────────────────────────────────────────────────────

    @Test
    void getByPaymentId_exists_returnsPayment() {
        Payment payment = pendingPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        Payment result = paymentService.getByPaymentId(PAYMENT_ID);

        assertThat(result).isSameAs(payment);
    }

    @Test
    void getByPaymentId_notFound_throwsPaymentNotFoundException() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getByPaymentId(PAYMENT_ID))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    // ─── refundPayment ────────────────────────────────────────────────────────

    @Test
    void refundPayment_changesStatusToRefunded() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.refundPayment(PAYMENT_ID);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void refundPayment_alreadyRefunded_returnsExistingPaymentWithoutPublishing() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.REFUNDED);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        Payment result = paymentService.refundPayment(PAYMENT_ID);

        assertThat(result).isSameAs(payment);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void refundPayment_pending_changesStatusToRefunded() {
        Payment payment = pendingPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.refundPayment(PAYMENT_ID);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository).save(payment);
        verify(eventPublisher).publishPaymentRefunded(any());
    }

    @Test
    void refundPayment_failed_throwsInvalidPaymentTransitionException() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refundPayment(PAYMENT_ID))
                .isInstanceOf(InvalidPaymentTransitionException.class)
                .hasMessageContaining("from FAILED to REFUNDED");
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void refundPayment_notFound_throwsPaymentNotFoundException() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.refundPayment(PAYMENT_ID))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(PAYMENT_ID.toString());
    }

    // ─── getStatusByOrderId ────────────────────────────────────────────────────

    @Test
    void getStatusByOrderId_returnsStatus() {
        Payment payment = pendingPayment();
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        PaymentStatus status = paymentService.getStatusByOrderId(ORDER_ID);

        assertThat(status).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void getStatusByOrderId_notFound_throwsPaymentNotFoundException() {
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getStatusByOrderId(ORDER_ID))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(ORDER_ID.toString());
    }
}
