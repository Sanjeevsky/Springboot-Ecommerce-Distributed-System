package com.sanjeevsky.paymentservice.service;

import com.sanjeevsky.paymentservice.model.Payment;
import com.sanjeevsky.paymentservice.model.PaymentRequest;
import com.sanjeevsky.paymentservice.model.PaymentStatus;
import com.sanjeevsky.platform.events.ChargePaymentCommand;

import java.util.UUID;

public interface PaymentService {

    Payment initiatePayment(PaymentRequest request);

    /**
     * Saga charge step: creates (idempotently) and settles a payment in a single command.
     * Publishes {@code PaymentConfirmedEvent} on success or {@code PaymentFailedEvent} on
     * failure so the order saga orchestrator can advance or compensate.
     */
    Payment charge(ChargePaymentCommand command);

    Payment confirmPayment(UUID paymentId);

    Payment failPayment(UUID paymentId);

    Payment getByPaymentId(UUID paymentId);

    PaymentStatus getStatusByOrderId(UUID orderId);

    Payment refundPayment(UUID paymentId);
}
