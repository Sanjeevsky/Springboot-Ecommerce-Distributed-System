package com.sanjeevsky.wishlistservice.controller;

import com.sanjeevsky.wishlistservice.dto.AddToWishlistRequest;
import com.sanjeevsky.wishlistservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.wishlistservice.model.WishlistItem;
import com.sanjeevsky.wishlistservice.service.WishlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WishlistControllerTest {

    private static final String USER_ID = "buyer@example.com";
    private static final UUID PRODUCT_ID = UUID.fromString("1e05c570-bd04-49b2-8ca3-8172cddfb1c2");
    private static final UUID ITEM_ID = UUID.fromString("b3b624e6-2660-4096-8b6f-199e85221b72");

    @Mock
    private WishlistService wishlistService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WishlistController(wishlistService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void addToWishlist_withXUser_returns201AndForwardsRequest() throws Exception {
        when(wishlistService.addToWishlist(eq(USER_ID), any()))
                .thenReturn(item());

        mockMvc.perform(post("/wishlist-service/wishlist")
                        .header("X-User", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + PRODUCT_ID
                                + "\",\"productName\":\"Running Shoes\",\"salePrice\":1299.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Item added to wishlist"))
                .andExpect(jsonPath("$.data.productId").value(PRODUCT_ID.toString()));

        ArgumentCaptor<AddToWishlistRequest> captor = ArgumentCaptor.forClass(AddToWishlistRequest.class);
        verify(wishlistService).addToWishlist(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(captor.getValue().getProductName()).isEqualTo("Running Shoes");
        assertThat(captor.getValue().getSalePrice()).isEqualTo(1299.5);
    }

    @Test
    void addToWishlist_invalidRequest_returns400BeforeServiceCall() throws Exception {
        mockMvc.perform(post("/wishlist-service/wishlist")
                        .header("X-User", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("productId must not be null")));

        verifyNoInteractions(wishlistService);
    }

    @Test
    void getWishlist_forwardsXUserAndReturnsItems() throws Exception {
        when(wishlistService.getWishlist(USER_ID)).thenReturn(List.of(item()));

        mockMvc.perform(get("/wishlist-service/wishlist")
                        .header("X-User", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(ITEM_ID.toString()));

        verify(wishlistService).getWishlist(USER_ID);
    }

    @Test
    void removeFromWishlist_forwardsXUserAndProductId() throws Exception {
        mockMvc.perform(delete("/wishlist-service/wishlist/{productId}", PRODUCT_ID)
                        .header("X-User", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Item removed from wishlist"));

        verify(wishlistService).removeFromWishlist(USER_ID, PRODUCT_ID);
    }

    @Test
    void moveToCart_forwardsXUserAndProductId() throws Exception {
        when(wishlistService.moveToCart(USER_ID, PRODUCT_ID)).thenReturn(item());

        mockMvc.perform(post("/wishlist-service/wishlist/{productId}/move-to-cart", PRODUCT_ID)
                        .header("X-User", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Item moved to cart"))
                .andExpect(jsonPath("$.data.productId").value(PRODUCT_ID.toString()));

        verify(wishlistService).moveToCart(USER_ID, PRODUCT_ID);
    }

    private WishlistItem item() {
        return WishlistItem.builder()
                .id(ITEM_ID)
                .userId(USER_ID)
                .productId(PRODUCT_ID)
                .productName("Running Shoes")
                .salePrice(1299.5)
                .build();
    }
}
