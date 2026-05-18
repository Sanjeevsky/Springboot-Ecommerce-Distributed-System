package com.sanjeevsky.inventoryservice;

import com.sanjeevsky.inventoryservice.events.InventoryEventPublisher;
import com.sanjeevsky.inventoryservice.events.OrderEventConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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
                "spring.kafka.bootstrap-servers=localhost:9999",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
                "spring.datasource.url=jdbc:h2:mem:inventory-integration-db;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
class InventoryIntegrationTest {

    @MockBean
    InventoryEventPublisher inventoryEventPublisher;

    @MockBean
    OrderEventConsumer orderEventConsumer;

    @Autowired
    MockMvc mockMvc;

    private static final UUID PRODUCT_A  = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT_B  = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VARIANT_V1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private String stockBody(UUID productId, UUID variantId, int qty) {
        String variant = variantId == null ? "null" : "\"" + variantId + "\"";
        return "{\"productId\":\"" + productId + "\",\"variantId\":" + variant + ",\"quantity\":" + qty + "}";
    }

    // ─── Add stock ────────────────────────────────────────────────────────────

    @Test
    void addStock_newEntry_createsInventory() throws Exception {
        mockMvc.perform(post("/inventory-service/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockBody(PRODUCT_A, null, 50)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(PRODUCT_A.toString()))
                .andExpect(jsonPath("$.data.totalQty").value(50))
                .andExpect(jsonPath("$.data.reservedQty").value(0));
    }

    @Test
    void addStock_existingEntry_incrementsTotalQty() throws Exception {
        mockMvc.perform(post("/inventory-service/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockBody(PRODUCT_B, null, 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQty").value(30));

        mockMvc.perform(post("/inventory-service/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockBody(PRODUCT_B, null, 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQty").value(50));
    }

    @Test
    void addStock_zeroQuantity_returns400() throws Exception {
        mockMvc.perform(post("/inventory-service/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockBody(PRODUCT_A, null, 0)))
                .andExpect(status().isBadRequest());
    }

    // ─── Get stock by product ─────────────────────────────────────────────────

    @Test
    void getStockByProduct_returnsInventoryList() throws Exception {
        UUID prod = UUID.fromString("33333333-3333-3333-3333-333333333333");
        mockMvc.perform(post("/inventory-service/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockBody(prod, VARIANT_V1, 15)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/inventory-service/stock/" + prod))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].productId").value(prod.toString()));
    }

    @Test
    void getStockByProduct_notFound_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/inventory-service/stock/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ─── Get variant stock ────────────────────────────────────────────────────

    @Test
    void getVariantStock_returnsCorrectEntry() throws Exception {
        UUID prod = UUID.fromString("44444444-4444-4444-4444-444444444444");
        mockMvc.perform(post("/inventory-service/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockBody(prod, VARIANT_V1, 25)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/inventory-service/stock/" + prod + "/variant/" + VARIANT_V1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(prod.toString()))
                .andExpect(jsonPath("$.data.variantId").value(VARIANT_V1.toString()))
                .andExpect(jsonPath("$.data.totalQty").value(25));
    }

    @Test
    void getVariantStock_notFound_returns404() throws Exception {
        mockMvc.perform(get("/inventory-service/stock/" + UUID.randomUUID()
                + "/variant/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ─── Get available qty ────────────────────────────────────────────────────

    @Test
    void getAvailableQty_returnsAvailableStock() throws Exception {
        UUID prod = UUID.fromString("55555555-5555-5555-5555-555555555555");
        mockMvc.perform(post("/inventory-service/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockBody(prod, null, 40)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/inventory-service/stock/" + prod + "/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(40));
    }
}
