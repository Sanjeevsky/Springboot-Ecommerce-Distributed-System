package com.sanjeevsky.shoppingcartservice.controller;

import com.sanjeevsky.shoppingcartservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.shoppingcartservice.model.Cart;
import com.sanjeevsky.shoppingcartservice.model.CartItem;
import com.sanjeevsky.shoppingcartservice.services.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    private static final String USER_ID = "buyer@example.com";
    private static final UUID CART_ID = UUID.fromString("d8d4a3fb-9cf0-4277-b819-56e756a32ea0");
    private static final UUID ITEM_ID = UUID.fromString("da55dce8-0fec-4097-9758-7daed6f0db6b");
    private static final UUID PRODUCT_ID = UUID.fromString("54f3ac74-d44d-4da7-b1f8-597535165418");
    private static final UUID VARIANT_ID = UUID.fromString("5c68f0cc-e6e2-49c3-a09d-9ce1a7a3f6a8");

    @Mock
    private CartService cartService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CartController(cartService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getCart_forwardsXUser() throws Exception {
        when(cartService.getOrCreateCart(USER_ID)).thenReturn(cart());

        mockMvc.perform(get("/cart-service/cart")
                        .header("X-User", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(CART_ID.toString()));

        verify(cartService).getOrCreateCart(USER_ID);
    }

    @Test
    void addItem_forwardsXUserAndRequestValues() throws Exception {
        when(cartService.addItem(USER_ID, PRODUCT_ID, VARIANT_ID, 2)).thenReturn(cart());

        mockMvc.perform(post("/cart-service/cart/add")
                        .header("X-User", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + PRODUCT_ID + "\",\"variantId\":\"" + VARIANT_ID + "\",\"qty\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].productId").value(PRODUCT_ID.toString()));

        verify(cartService).addItem(USER_ID, PRODUCT_ID, VARIANT_ID, 2);
    }

    @Test
    void addItem_invalidRequest_returns400BeforeServiceCall() throws Exception {
        mockMvc.perform(post("/cart-service/cart/add")
                        .header("X-User", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Product ID is required")));

        verifyNoInteractions(cartService);
    }

    @Test
    void updateItem_forwardsXUserProductAndQuantity() throws Exception {
        when(cartService.updateItem(USER_ID, PRODUCT_ID, 4)).thenReturn(cart());

        mockMvc.perform(put("/cart-service/cart/item/{productId}", PRODUCT_ID)
                        .header("X-User", USER_ID)
                        .param("qty", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(cartService).updateItem(USER_ID, PRODUCT_ID, 4);
    }

    @Test
    void removeItem_forwardsXUserAndProduct() throws Exception {
        when(cartService.removeItem(USER_ID, PRODUCT_ID)).thenReturn(cart());

        mockMvc.perform(delete("/cart-service/cart/item/{productId}", PRODUCT_ID)
                        .header("X-User", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(cartService).removeItem(USER_ID, PRODUCT_ID);
    }

    @Test
    void clearCart_forwardsXUser() throws Exception {
        when(cartService.clearCart(USER_ID)).thenReturn(emptyCart());

        mockMvc.perform(delete("/cart-service/cart/clear")
                        .header("X-User", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isEmpty());

        verify(cartService).clearCart(USER_ID);
    }

    @Test
    void getCheckoutSnapshot_returnsSnapshotResponse() throws Exception {
        when(cartService.getCheckoutSnapshot(USER_ID)).thenReturn(cart());

        mockMvc.perform(get("/cart-service/cart/checkout")
                        .header("X-User", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(CART_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].id").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].qty").value(2))
                .andExpect(jsonPath("$.data.totalAmount").value(50.0));

        verify(cartService).getCheckoutSnapshot(USER_ID);
    }

    private Cart cart() {
        return Cart.builder()
                .id(CART_ID)
                .userId(USER_ID)
                .totalAmount(50.0)
                .items(List.of(item()))
                .build();
    }

    private Cart emptyCart() {
        return Cart.builder()
                .id(CART_ID)
                .userId(USER_ID)
                .totalAmount(0.0)
                .items(List.of())
                .build();
    }

    private CartItem item() {
        return CartItem.builder()
                .id(ITEM_ID)
                .productId(PRODUCT_ID)
                .variantId(VARIANT_ID)
                .productName("Running Shoes")
                .unitPrice(25.0)
                .qty(2)
                .build();
    }
}
