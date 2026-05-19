package com.sanjeevsky.shoppingcartservice.service;

import com.sanjeevsky.platform.model.product.ProductResponse;
import com.sanjeevsky.shoppingcartservice.clients.CatalogFeignClient;
import com.sanjeevsky.shoppingcartservice.exceptions.CartNotFoundException;
import com.sanjeevsky.shoppingcartservice.model.Cart;
import com.sanjeevsky.shoppingcartservice.model.CartItem;
import com.sanjeevsky.shoppingcartservice.repository.CartRepository;
import com.sanjeevsky.shoppingcartservice.services.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.lenient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CatalogFeignClient catalogFeignClient;

    @InjectMocks
    private CartServiceImpl cartService;

    private static final String USER_ID = "user@example.com";
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID CART_ID = UUID.randomUUID();

    private Cart emptyCart() {
        return Cart.builder()
                .id(CART_ID)
                .userId(USER_ID)
                .items(new ArrayList<>())
                .totalAmount(0.0)
                .build();
    }

    private CartItem item(Cart cart, UUID productId, double price, int qty) {
        return CartItem.builder()
                .id(UUID.randomUUID())
                .cart(cart)
                .productId(productId)
                .productName("Widget")
                .unitPrice(price)
                .qty(qty)
                .build();
    }

    @BeforeEach
    void stubSave() {
        lenient().when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── getOrCreateCart ───────────────────────────────────────────────────────

    @Test
    void getOrCreateCart_existingCart_returnsIt() {
        Cart existing = emptyCart();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        Cart result = cartService.getOrCreateCart(USER_ID);

        assertThat(result).isSameAs(existing);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void getOrCreateCart_noCart_createsNewCart() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        Cart result = cartService.getOrCreateCart(USER_ID);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getTotalAmount()).isZero();
        verify(cartRepository).save(any(Cart.class));
    }

    // ─── addItem ───────────────────────────────────────────────────────────────

    @Test
    void addItem_newProduct_addsItemAndComputesTotal() {
        Cart cart = emptyCart();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(catalogFeignClient.getProduct(PRODUCT_ID))
                .thenReturn(new ProductResponse(PRODUCT_ID, "Widget", "desc", 25.0, 30.0, 5.0, 1, false));

        Cart result = cartService.addItem(USER_ID, PRODUCT_ID, null, 2);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQty()).isEqualTo(2);
        assertThat(result.getItems().get(0).getUnitPrice()).isEqualTo(25.0);
        assertThat(result.getTotalAmount()).isEqualTo(50.0);
    }

    @Test
    void addItem_existingProduct_incrementsQty() {
        Cart cart = emptyCart();
        CartItem existing = item(cart, PRODUCT_ID, 10.0, 3);
        cart.getItems().add(existing);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(catalogFeignClient.getProduct(PRODUCT_ID))
                .thenReturn(new ProductResponse(PRODUCT_ID, "Widget", "desc", 10.0, 12.0, 2.0, 1, false));

        Cart result = cartService.addItem(USER_ID, PRODUCT_ID, null, 2);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQty()).isEqualTo(5);
        assertThat(result.getTotalAmount()).isEqualTo(50.0);
    }

    // ─── updateItem ────────────────────────────────────────────────────────────

    @Test
    void updateItem_validQty_updatesQtyAndTotal() {
        Cart cart = emptyCart();
        CartItem existingItem = item(cart, PRODUCT_ID, 20.0, 1);
        cart.getItems().add(existingItem);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        Cart result = cartService.updateItem(USER_ID, PRODUCT_ID, 4);

        assertThat(result.getItems().get(0).getQty()).isEqualTo(4);
        assertThat(result.getTotalAmount()).isEqualTo(80.0);
    }

    @Test
    void updateItem_zeroQty_delegatesToRemoveItem() {
        Cart cart = emptyCart();
        CartItem existingItem = item(cart, PRODUCT_ID, 20.0, 1);
        cart.getItems().add(existingItem);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        Cart result = cartService.updateItem(USER_ID, PRODUCT_ID, 0);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalAmount()).isZero();
    }

    @Test
    void updateItem_cartNotFound_throwsCartNotFoundException() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItem(USER_ID, PRODUCT_ID, 3))
                .isInstanceOf(CartNotFoundException.class);
    }

    // ─── removeItem ────────────────────────────────────────────────────────────

    @Test
    void removeItem_existingItem_removesAndRecomputesTotal() {
        UUID otherProductId = UUID.randomUUID();
        Cart cart = emptyCart();
        CartItem toRemove = item(cart, PRODUCT_ID, 15.0, 2);      // 30
        CartItem toKeep = item(cart, otherProductId, 10.0, 1);    // 10
        cart.getItems().addAll(List.of(toRemove, toKeep));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        Cart result = cartService.removeItem(USER_ID, PRODUCT_ID);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getProductId()).isEqualTo(otherProductId);
        assertThat(result.getTotalAmount()).isEqualTo(10.0);
    }

    @Test
    void removeItem_cartNotFound_throwsCartNotFoundException() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItem(USER_ID, PRODUCT_ID))
                .isInstanceOf(CartNotFoundException.class);
    }

    // ─── clearCart ─────────────────────────────────────────────────────────────

    @Test
    void clearCart_removesAllItemsAndZerosTotal() {
        Cart cart = emptyCart();
        cart.getItems().add(item(cart, PRODUCT_ID, 10.0, 3));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        Cart result = cartService.clearCart(USER_ID);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalAmount()).isZero();
    }

    @Test
    void clearCart_cartNotFound_throwsCartNotFoundException() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.clearCart(USER_ID))
                .isInstanceOf(CartNotFoundException.class);
    }

    // ─── total recomputation ───────────────────────────────────────────────────

    @Test
    void addItem_multipleItems_totalIsCorrect() {
        UUID p2 = UUID.randomUUID();
        Cart cart = emptyCart();
        CartItem existing = item(cart, PRODUCT_ID, 5.0, 2);   // 10
        cart.getItems().add(existing);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(catalogFeignClient.getProduct(p2))
                .thenReturn(new ProductResponse(p2, "Gadget", "desc", 8.0, 10.0, 2.0, 1, false));

        Cart result = cartService.addItem(USER_ID, p2, null, 3);  // 8 * 3 = 24

        assertThat(result.getTotalAmount()).isEqualTo(34.0);  // 10 + 24
    }

    // ─── save is called exactly once per mutation ──────────────────────────────

    @Test
    void addItem_callsSaveOnce() {
        Cart cart = emptyCart();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(catalogFeignClient.getProduct(PRODUCT_ID))
                .thenReturn(new ProductResponse(PRODUCT_ID, "Widget", "desc", 10.0, 12.0, 2.0, 1, false));

        cartService.addItem(USER_ID, PRODUCT_ID, null, 1);

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getItems()).hasSize(1);
    }
}
