package com.sanjeevsky.paymentservice;

import com.sanjeevsky.paymentservice.events.PaymentEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "eureka.client.enabled=false",
                "spring.boot.admin.client.enabled=false",
                "spring.zipkin.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
                "spring.datasource.url=jdbc:h2:mem:payment-integration-db;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.config.import=")
class PaymentIntegrationTest {

    @MockBean
    PaymentEventPublisher paymentEventPublisher;

    @Autowired
    MockMvc mockMvc;

    private static final UUID ORDER_ID  = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ORDER_ID2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String USER    = "buyer@example.com";

    private String initiateBody(UUID orderId) {
        return "{\"orderId\":\"" + orderId + "\",\"userId\":\"" + USER + "\",\"amount\":500.0}";
    }

    private String extractPaymentId(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        int start = json.indexOf("\"id\":\"") + 6;
        int end   = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    // ─── Initiate ─────────────────────────────────────────────────────────────

    @Test
    void initiatePayment_returns201WithPendingStatus() throws Exception {
        mockMvc.perform(post("/payment-service/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody(ORDER_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.amount").value(500.0))
                .andExpect(jsonPath("$.data.userId").value(USER));
    }

    @Test
    void initiatePayment_withSameIdempotencyKey_returnsExistingPayment() throws Exception {
        UUID oid = UUID.fromString("99999999-9999-9999-9999-999999999999");
        MvcResult init = mockMvc.perform(post("/payment-service/initiate")
                        .header("Idempotency-Key", "payment-retry-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody(oid)))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = extractPaymentId(init);

        mockMvc.perform(post("/payment-service/initiate")
                        .header("Idempotency-Key", "payment-retry-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody(oid)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(paymentId));
    }

    // ─── Confirm ──────────────────────────────────────────────────────────────

    @Test
    void confirmPayment_returns200WithSuccessStatus() throws Exception {
        MvcResult init = mockMvc.perform(post("/payment-service/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody(ORDER_ID2)))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = extractPaymentId(init);

        mockMvc.perform(put("/payment-service/confirm/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    // ─── Fail ─────────────────────────────────────────────────────────────────

    @Test
    void failPayment_returns200WithFailedStatus() throws Exception {
        UUID oid = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        MvcResult init = mockMvc.perform(post("/payment-service/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody(oid)))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = extractPaymentId(init);

        mockMvc.perform(put("/payment-service/fail/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));
    }

    // ─── Refund ───────────────────────────────────────────────────────────────

    @Test
    void refundPayment_returns200WithRefundedStatus() throws Exception {
        UUID oid = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        MvcResult init = mockMvc.perform(post("/payment-service/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody(oid)))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = extractPaymentId(init);

        mockMvc.perform(put("/payment-service/confirm/" + paymentId)).andExpect(status().isOk());

        mockMvc.perform(put("/payment-service/refund/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
    }

    @Test
    void refundPayment_pendingPayment_returns200WithRefundedStatus() throws Exception {
        UUID oid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        MvcResult init = mockMvc.perform(post("/payment-service/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody(oid)))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = extractPaymentId(init);

        mockMvc.perform(put("/payment-service/refund/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
    }

    @Test
    void failPayment_successPayment_returns400() throws Exception {
        UUID oid = UUID.fromString("22222222-2222-2222-2222-222222222222");
        MvcResult init = mockMvc.perform(post("/payment-service/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody(oid)))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = extractPaymentId(init);
        mockMvc.perform(put("/payment-service/confirm/" + paymentId)).andExpect(status().isOk());

        mockMvc.perform(put("/payment-service/fail/" + paymentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("from SUCCESS to FAILED")));
    }

    // ─── Get by ID ────────────────────────────────────────────────────────────

    @Test
    void getByPaymentId_returnsPayment() throws Exception {
        UUID oid = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        MvcResult init = mockMvc.perform(post("/payment-service/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody(oid)))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = extractPaymentId(init);

        mockMvc.perform(get("/payment-service/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(paymentId))
                .andExpect(jsonPath("$.data.amount").value(500.0));
    }

    // ─── Get status by orderId ─────────────────────────────────────────────────

    @Test
    void getStatusByOrderId_returnsStatus() throws Exception {
        UUID oid = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        mockMvc.perform(post("/payment-service/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody(oid)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/payment-service/status/" + oid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("PENDING"));
    }

    // ─── Not found ────────────────────────────────────────────────────────────

    @Test
    void confirmPayment_notFound_returns404() throws Exception {
        mockMvc.perform(put("/payment-service/confirm/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByPaymentId_notFound_returns404() throws Exception {
        mockMvc.perform(get("/payment-service/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
