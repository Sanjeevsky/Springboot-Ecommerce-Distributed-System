package com.sanjeevsky.shoppingcartservice;

import com.sanjeevsky.platform.model.product.ProductResponse;
import com.sanjeevsky.shoppingcartservice.clients.CatalogFeignClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "eureka.client.enabled=false",
                "spring.boot.admin.client.enabled=false",
                "spring.zipkin.enabled=false",
                "feign.circuitbreaker.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:cart-integration-db;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:schema.sql"
        }
)
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.config.import=")
class CartIntegrationTest {

    @MockBean
    CatalogFeignClient catalogFeignClient;

    @Autowired
    MockMvc mockMvc;

    private static final UUID PRODUCT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private void stubProduct(UUID id, String name, double salePrice) {
        when(catalogFeignClient.getProduct(id))
                .thenReturn(new ProductResponse(id, name, "desc", salePrice, salePrice + 5.0, 5.0, 1, false));
    }

    // ─── Full happy-path flow ──────────────────────────────────────────────────

    @Test
    void fullCartFlow_getAddUpdateCheckoutRemove() throws Exception {
        stubProduct(PRODUCT_A, "Widget", 25.0);
        String user = "flow@example.com";

        // 1. First access creates an empty cart
        mockMvc.perform(get("/cart-service/cart").header("X-User", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(user))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalAmount").value(0.0));

        // 2. Add 2 units → total = 50
        mockMvc.perform(post("/cart-service/cart/add")
                        .header("X-User", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"productId\":\"%s\",\"qty\":2}", PRODUCT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productId").value(PRODUCT_A.toString()))
                .andExpect(jsonPath("$.data.items[0].qty").value(2))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(25.0))
                .andExpect(jsonPath("$.data.totalAmount").value(50.0));

        // 3. Update qty to 5 → total = 125
        mockMvc.perform(put("/cart-service/cart/item/" + PRODUCT_A + "?qty=5")
                        .header("X-User", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].qty").value(5))
                .andExpect(jsonPath("$.data.totalAmount").value(125.0));

        // 4. Checkout snapshot reflects live state
        mockMvc.perform(get("/cart-service/cart/checkout").header("X-User", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(125.0));

        // 5. Remove item → cart is empty again
        mockMvc.perform(delete("/cart-service/cart/item/" + PRODUCT_A)
                        .header("X-User", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalAmount").value(0.0));
    }

    // ─── Multi-item + clear ────────────────────────────────────────────────────

    @Test
    void addTwoProducts_thenClear_cartIsEmpty() throws Exception {
        stubProduct(PRODUCT_A, "Widget", 25.0);
        stubProduct(PRODUCT_B, "Gadget", 10.0);
        String user = "clear@example.com";

        mockMvc.perform(post("/cart-service/cart/add")
                        .header("X-User", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"productId\":\"%s\",\"qty\":1}", PRODUCT_A)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/cart-service/cart/add")
                        .header("X-User", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"productId\":\"%s\",\"qty\":3}", PRODUCT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.totalAmount").value(55.0));  // 25 + 30

        mockMvc.perform(delete("/cart-service/cart/clear").header("X-User", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalAmount").value(0.0));
    }

    // ─── Duplicate add increments qty ─────────────────────────────────────────

    @Test
    void addSameProductTwice_qtyAccumulates() throws Exception {
        stubProduct(PRODUCT_A, "Widget", 25.0);
        String user = "incr@example.com";

        mockMvc.perform(post("/cart-service/cart/add")
                        .header("X-User", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"productId\":\"%s\",\"qty\":2}", PRODUCT_A)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/cart-service/cart/add")
                        .header("X-User", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"productId\":\"%s\",\"qty\":3}", PRODUCT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].qty").value(5))
                .andExpect(jsonPath("$.data.totalAmount").value(125.0));
    }

    // ─── Update to qty=0 removes the item ─────────────────────────────────────

    @Test
    void updateItemToZero_removesFromCart() throws Exception {
        stubProduct(PRODUCT_A, "Widget", 25.0);
        String user = "zero@example.com";

        mockMvc.perform(post("/cart-service/cart/add")
                        .header("X-User", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"productId\":\"%s\",\"qty\":3}", PRODUCT_A)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/cart-service/cart/item/" + PRODUCT_A + "?qty=0")
                        .header("X-User", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalAmount").value(0.0));
    }

    // ─── Validation: qty < 1 is rejected ──────────────────────────────────────

    @Test
    void addItem_zeroQty_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/cart-service/cart/add")
                        .header("X-User", "valid@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"productId\":\"%s\",\"qty\":0}", PRODUCT_A)))
                .andExpect(status().isBadRequest());
    }
}
