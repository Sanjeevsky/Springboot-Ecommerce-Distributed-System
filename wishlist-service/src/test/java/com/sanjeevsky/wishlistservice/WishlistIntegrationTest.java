package com.sanjeevsky.wishlistservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
                "spring.datasource.url=jdbc:h2:mem:wishlist-integration-db;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
class WishlistIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private static final UUID PRODUCT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private String addBody(UUID productId) {
        return "{\"productId\":\"" + productId + "\",\"productName\":\"Widget\",\"salePrice\":99.0}";
    }

    // ─── Add ──────────────────────────────────────────────────────────────────

    @Test
    void addToWishlist_returns201WithItem() throws Exception {
        mockMvc.perform(post("/wishlist-service/wishlist")
                        .header("X-User", "user1@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_A)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productId").value(PRODUCT_A.toString()))
                .andExpect(jsonPath("$.data.productName").value("Widget"))
                .andExpect(jsonPath("$.data.salePrice").value(99.0));
    }

    @Test
    void addToWishlist_duplicate_returns409() throws Exception {
        String user = "dupuser@example.com";
        mockMvc.perform(post("/wishlist-service/wishlist")
                        .header("X-User", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_A)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/wishlist-service/wishlist")
                        .header("X-User", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_A)))
                .andExpect(status().isConflict());
    }

    // ─── Get ──────────────────────────────────────────────────────────────────

    @Test
    void getWishlist_returnsAllItemsForUser() throws Exception {
        String user = "getuser@example.com";
        mockMvc.perform(post("/wishlist-service/wishlist")
                        .header("X-User", user).contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_A))).andExpect(status().isCreated());
        mockMvc.perform(post("/wishlist-service/wishlist")
                        .header("X-User", user).contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_B))).andExpect(status().isCreated());

        mockMvc.perform(get("/wishlist-service/wishlist").header("X-User", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void getWishlist_empty_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/wishlist-service/wishlist")
                        .header("X-User", "emptyuser@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ─── Remove ───────────────────────────────────────────────────────────────

    @Test
    void removeFromWishlist_returns200() throws Exception {
        String user = "remuser@example.com";
        mockMvc.perform(post("/wishlist-service/wishlist")
                        .header("X-User", user).contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_A))).andExpect(status().isCreated());

        mockMvc.perform(delete("/wishlist-service/wishlist/" + PRODUCT_A)
                        .header("X-User", user))
                .andExpect(status().isOk());

        mockMvc.perform(get("/wishlist-service/wishlist").header("X-User", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void removeFromWishlist_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/wishlist-service/wishlist/" + UUID.randomUUID())
                        .header("X-User", "anyuser@example.com"))
                .andExpect(status().isNotFound());
    }

    // ─── Move to cart ─────────────────────────────────────────────────────────

    @Test
    void moveToCart_returnsItemAndRemovesFromWishlist() throws Exception {
        String user = "moveuser@example.com";
        mockMvc.perform(post("/wishlist-service/wishlist")
                        .header("X-User", user).contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_B))).andExpect(status().isCreated());

        mockMvc.perform(post("/wishlist-service/wishlist/" + PRODUCT_B + "/move-to-cart")
                        .header("X-User", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(PRODUCT_B.toString()));

        mockMvc.perform(get("/wishlist-service/wishlist").header("X-User", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void moveToCart_notInWishlist_returns404() throws Exception {
        mockMvc.perform(post("/wishlist-service/wishlist/" + UUID.randomUUID() + "/move-to-cart")
                        .header("X-User", "noneuser@example.com"))
                .andExpect(status().isNotFound());
    }
}
