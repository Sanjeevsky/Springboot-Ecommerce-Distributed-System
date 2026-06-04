package com.sanjeevsky.paymentservice.controller;

import com.sanjeevsky.paymentservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.paymentservice.model.Payment;
import com.sanjeevsky.paymentservice.model.PaymentRequest;
import com.sanjeevsky.paymentservice.model.PaymentStatus;
import com.sanjeevsky.paymentservice.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final String USER = "buyer@example.com";
    private static final UUID PAYMENT_ID = UUID.fromString("6f2c5bb2-291a-4ee3-9ff8-7001ffb40bbf");
    private static final UUID ORDER_ID = UUID.fromString("7c9d83ae-64ca-4c89-9c09-5077a7607b20");

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentController(paymentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void initiatePayment_withIdempotencyKey_trimsHeaderAndReturns201() throws Exception {
        when(paymentService.initiatePayment(any())).thenReturn(payment(PaymentStatus.PENDING));

        mockMvc.perform(post("/payment-service/initiate")
                        .header("Idempotency-Key", " payment-1 ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER + "\",\"amount\":119999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        ArgumentCaptor<PaymentRequest> captor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentService).initiatePayment(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("payment-1");
        assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER);
    }

    @Test
    void initiatePayment_invalidRequest_returns400BeforeServiceCall() throws Exception {
        mockMvc.perform(post("/payment-service/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("orderId is required")));

        verifyNoInteractions(paymentService);
    }

    @Test
    void confirmPayment_returnsConfirmedPayment() throws Exception {
        when(paymentService.confirmPayment(PAYMENT_ID)).thenReturn(payment(PaymentStatus.SUCCESS));

        mockMvc.perform(put("/payment-service/confirm/{paymentId}", PAYMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Payment confirmed"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        verify(paymentService).confirmPayment(PAYMENT_ID);
    }

    @Test
    void failPayment_returnsFailedPayment() throws Exception {
        when(paymentService.failPayment(PAYMENT_ID)).thenReturn(payment(PaymentStatus.FAILED));

        mockMvc.perform(put("/payment-service/fail/{paymentId}", PAYMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Payment marked failed"))
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        verify(paymentService).failPayment(PAYMENT_ID);
    }

    @Test
    void refundPayment_returnsRefundedPayment() throws Exception {
        when(paymentService.refundPayment(PAYMENT_ID)).thenReturn(payment(PaymentStatus.REFUNDED));

        mockMvc.perform(put("/payment-service/refund/{paymentId}", PAYMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Payment refunded"))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        verify(paymentService).refundPayment(PAYMENT_ID);
    }

    @Test
    void getByPaymentId_returnsPayment() throws Exception {
        when(paymentService.getByPaymentId(PAYMENT_ID)).thenReturn(payment(PaymentStatus.PENDING));

        mockMvc.perform(get("/payment-service/{paymentId}", PAYMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PAYMENT_ID.toString()));

        verify(paymentService).getByPaymentId(PAYMENT_ID);
    }

    @Test
    void getStatusByOrderId_returnsPaymentStatus() throws Exception {
        when(paymentService.getStatusByOrderId(ORDER_ID)).thenReturn(PaymentStatus.SUCCESS);

        mockMvc.perform(get("/payment-service/status/{orderId}", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("SUCCESS"));

        verify(paymentService).getStatusByOrderId(ORDER_ID);
    }

    private Payment payment(PaymentStatus status) {
        return Payment.builder()
                .id(PAYMENT_ID)
                .orderId(ORDER_ID)
                .userId(USER)
                .amount(119999)
                .currency("INR")
                .status(status)
                .idempotencyKey("payment-1")
                .build();
    }
}
