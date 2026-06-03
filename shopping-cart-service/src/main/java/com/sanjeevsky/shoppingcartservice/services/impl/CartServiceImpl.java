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
import java.util.Objects;
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
        String normalizedUserId = validateUserId(userId);
        log.info("getOrCreateCart called for userId={}", normalizedUserId);
        return cartRepository.findByUserId(normalizedUserId).orElseGet(() -> {
            log.info("No cart found for userId={}, creating new cart", normalizedUserId);
            Cart newCart = Cart.builder()
                    .userId(normalizedUserId)
                    .totalAmount(0.0)
                    .build();
            return cartRepository.save(newCart);
        });
    }

    @Override
    @Transactional
    public Cart addItem(String userId, UUID productId, UUID variantId, int qty) {
        String normalizedUserId = validateUserId(userId);
        validateProductId(productId);
        validateAddQuantity(qty);
        log.info("addItem called for userId={}, productId={}, qty={}", normalizedUserId, productId, qty);
        Cart cart = getOrCreateCart(normalizedUserId);

        ProductResponse product = catalogFeignClient.getProduct(productId);
        validateCatalogProduct(product, productId);

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(i -> Objects.equals(i.getProductId(), productId))
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
        String normalizedUserId = validateUserId(userId);
        validateProductId(productId);
        validateUpdateQuantity(qty);
        log.info("updateItem called for userId={}, productId={}, qty={}", normalizedUserId, productId, qty);
        if (qty == 0) {
            return removeItem(normalizedUserId, productId);
        }

        Cart cart = cartRepository.findByUserId(normalizedUserId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + normalizedUserId));

        cart.getItems().stream()
                .filter(i -> Objects.equals(i.getProductId(), productId))
                .findFirst()
                .ifPresent(item -> item.setQty(qty));

        recomputeTotal(cart);
        return cartRepository.save(cart);
    }

    @Override
    @Transactional
    public Cart removeItem(String userId, UUID productId) {
        String normalizedUserId = validateUserId(userId);
        validateProductId(productId);
        log.info("removeItem called for userId={}, productId={}", normalizedUserId, productId);
        Cart cart = cartRepository.findByUserId(normalizedUserId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + normalizedUserId));

        cart.getItems().removeIf(i -> Objects.equals(i.getProductId(), productId));
        recomputeTotal(cart);
        return cartRepository.save(cart);
    }

    @Override
    @Transactional
    public Cart clearCart(String userId) {
        String normalizedUserId = validateUserId(userId);
        log.info("clearCart called for userId={}", normalizedUserId);
        Cart cart = cartRepository.findByUserId(normalizedUserId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + normalizedUserId));

        cart.getItems().clear();
        cart.setTotalAmount(0.0);
        return cartRepository.save(cart);
    }

    @Override
    @Transactional
    public Cart getCheckoutSnapshot(String userId) {
        String normalizedUserId = validateUserId(userId);
        log.info("getCheckoutSnapshot called for userId={}", normalizedUserId);
        return getOrCreateCart(normalizedUserId);
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

    private String validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new InvalidCartRequestException("Cart userId is required");
        }
        return userId.trim();
    }

    private void validateProductId(UUID productId) {
        if (productId == null) {
            throw new InvalidCartRequestException("Cart productId is required");
        }
    }

    private void validateCatalogProduct(ProductResponse product, UUID productId) {
        if (product == null) {
            throw new InvalidCartRequestException("Product not found: " + productId);
        }
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new InvalidCartRequestException("Catalog product name is required");
        }
        if (!Double.isFinite(product.getSalePrice()) || Double.compare(product.getSalePrice(), 0.0) <= 0) {
            throw new InvalidCartRequestException("Catalog product sale price must be greater than zero");
        }
    }
}
