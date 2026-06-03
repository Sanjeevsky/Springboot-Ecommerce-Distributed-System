package com.sanjeevsky.shoppingcartservice.services.impl;

import com.sanjeevsky.shoppingcartservice.clients.CatalogFeignClient;
import com.sanjeevsky.platform.model.product.ProductResponse;
import com.sanjeevsky.shoppingcartservice.exceptions.CartNotFoundException;
import com.sanjeevsky.shoppingcartservice.exceptions.InvalidCartRequestException;
import com.sanjeevsky.shoppingcartservice.model.Cart;
import com.sanjeevsky.shoppingcartservice.model.CartItem;
import com.sanjeevsky.shoppingcartservice.repository.CartRepository;
import com.sanjeevsky.shoppingcartservice.services.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CatalogFeignClient catalogFeignClient;

    @Override
    @Transactional
    public Cart getOrCreateCart(String userId) {
        log.info("getOrCreateCart called for userId={}", userId);
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            log.info("No cart found for userId={}, creating new cart", userId);
            Cart newCart = Cart.builder()
                    .userId(userId)
                    .totalAmount(0.0)
                    .build();
            return cartRepository.save(newCart);
        });
    }

    @Override
    @Transactional
    public Cart addItem(String userId, UUID productId, UUID variantId, int qty) {
        log.info("addItem called for userId={}, productId={}, qty={}", userId, productId, qty);
        validateAddQuantity(qty);
        Cart cart = getOrCreateCart(userId);

        ProductResponse product = catalogFeignClient.getProduct(productId);

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst();

        if (existingItem.isPresent()) {
            existingItem.get().setQty(existingItem.get().getQty() + qty);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .productId(productId)
                    .variantId(variantId)
                    .productName(product.getName())
                    .unitPrice(product.getSalePrice())
                    .qty(qty)
                    .addedAt(LocalDateTime.now())
                    .build();
            cart.getItems().add(newItem);
        }

        recomputeTotal(cart);
        return cartRepository.save(cart);
    }

    @Override
    @Transactional
    public Cart updateItem(String userId, UUID productId, int qty) {
        log.info("updateItem called for userId={}, productId={}, qty={}", userId, productId, qty);
        validateUpdateQuantity(qty);
        if (qty == 0) {
            return removeItem(userId, productId);
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

        cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .ifPresent(item -> item.setQty(qty));

        recomputeTotal(cart);
        return cartRepository.save(cart);
    }

    @Override
    @Transactional
    public Cart removeItem(String userId, UUID productId) {
        log.info("removeItem called for userId={}, productId={}", userId, productId);
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

        cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        recomputeTotal(cart);
        return cartRepository.save(cart);
    }

    @Override
    @Transactional
    public Cart clearCart(String userId) {
        log.info("clearCart called for userId={}", userId);
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

        cart.getItems().clear();
        cart.setTotalAmount(0.0);
        return cartRepository.save(cart);
    }

    @Override
    @Transactional
    public Cart getCheckoutSnapshot(String userId) {
        log.info("getCheckoutSnapshot called for userId={}", userId);
        return getOrCreateCart(userId);
    }

    private void recomputeTotal(Cart cart) {
        double total = cart.getItems().stream()
                .mapToDouble(i -> i.getUnitPrice() * i.getQty())
                .sum();
        cart.setTotalAmount(total);
    }

    private void validateAddQuantity(int qty) {
        if (qty <= 0) {
            throw new InvalidCartRequestException("Cart item quantity must be greater than zero");
        }
    }

    private void validateUpdateQuantity(int qty) {
        if (qty < 0) {
            throw new InvalidCartRequestException("Cart item quantity must not be negative");
        }
    }
}
