package com.sanjeevsky.notificationservice;

import com.sanjeevsky.notificationservice.consumer.OrderEventConsumer;
import com.sanjeevsky.notificationservice.consumer.PaymentEventConsumer;
import com.sanjeevsky.notificationservice.model.Notification;
import com.sanjeevsky.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "eureka.client.enabled=false",
                "spring.boot.admin.client.enabled=false",
                "spring.zipkin.enabled=false",
                "feign.client.config.default.connectTimeout=1000",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
                "spring.datasource.url=jdbc:h2:mem:notification-integration-db;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
class NotificationIntegrationTest {

    @MockBean
    OrderEventConsumer orderEventConsumer;

    @MockBean
    PaymentEventConsumer paymentEventConsumer;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    NotificationRepository notificationRepository;

    private Notification save(String userId, String type, boolean read) {
        return notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .subject("Test subject")
                .message("Test message")
                .read(read)
                .build());
    }

    // ─── Get all ──────────────────────────────────────────────────────────────

    @Test
    void getAllNotifications_returnsAllForUser() throws Exception {
        String user = "notify1@example.com";
        save(user, "ORDER_PLACED", false);
        save(user, "PAYMENT_PROCESSED", true);
        save("other@example.com", "ORDER_PLACED", false);

        mockMvc.perform(get("/notification-service/notifications").header("X-User", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void getAllNotifications_noNotifications_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/notification-service/notifications")
                        .header("X-User", "empty@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ─── Get unread ───────────────────────────────────────────────────────────

    @Test
    void getUnreadNotifications_returnsOnlyUnread() throws Exception {
        String user = "notify2@example.com";
        save(user, "ORDER_PLACED", false);
        save(user, "ORDER_CONFIRMED", false);
        save(user, "PAYMENT_PROCESSED", true);

        mockMvc.perform(get("/notification-service/notifications/unread").header("X-User", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.read==true)]").doesNotExist());
    }

    // ─── Mark as read ─────────────────────────────────────────────────────────

    @Test
    void markAsRead_updatesReadFlag() throws Exception {
        String user = "notify3@example.com";
        Notification n = save(user, "ORDER_PLACED", false);

        mockMvc.perform(put("/notification-service/notifications/" + n.getId() + "/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.id").value(n.getId().toString()));
    }

    @Test
    void markAsRead_notFound_returns404() throws Exception {
        mockMvc.perform(put("/notification-service/notifications/" + UUID.randomUUID() + "/read"))
                .andExpect(status().isNotFound());
    }
}
