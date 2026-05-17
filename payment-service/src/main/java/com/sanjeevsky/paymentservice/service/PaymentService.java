package com.sanjeevsky.paymentservice.service;

import com.sanjeevsky.paymentservice.model.Payment;
import com.sanjeevsky.paymentservice.model.PaymentRequest;
import com.sanjeevsky.paymentservice.model.PaymentStatus;

import java.util.UUID;

public interface PaymentService {

    Payment initiatePayment(PaymentRequest request);

    Payment confirmPayment(UUID paymentId);

    Payment failPayment(UUID paymentId);

    Payment getByPaymentId(UUID paymentId);

    PaymentStatus getStatusByOrderId(UUID orderId);

    Payment refundPayment(UUID paymentId);
}
