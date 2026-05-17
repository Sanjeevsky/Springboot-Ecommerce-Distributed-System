package com.sanjeevsky.shoppingcartservice.controller;

import com.sanjeevsky.platform.response.ApiResponse;
import com.sanjeevsky.shoppingcartservice.model.Cart;
import com.sanjeevsky.shoppingcartservice.model.dto.AddItemRequest;
import com.sanjeevsky.shoppingcartservice.services.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/cart-service")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/cart")
    public ResponseEntity<ApiResponse<Cart>> getCart(@RequestHeader("X-User") String userId) {
        log.info("GET /cart-service/cart for userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(cartService.getOrCreateCart(userId)));
    }

    @PostMapping("/cart/add")
    public ResponseEntity<ApiResponse<Cart>> addItem(
            @RequestHeader("X-User") String userId,
            @Valid @RequestBody AddItemRequest request) {
        log.info("POST /cart-service/cart/add for userId={}, productId={}", userId, request.getProductId());
        Cart cart = cartService.addItem(userId, request.getProductId(), request.getVariantId(), request.getQty());
        return ResponseEntity.ok(ApiResponse.ok(cart));
    }

    @PutMapping("/cart/item/{productId}")
    public ResponseEntity<ApiResponse<Cart>> updateItem(
            @RequestHeader("X-User") String userId,
            @PathVariable("productId") UUID productId,
            @RequestParam("qty") int qty) {
        log.info("PUT /cart-service/cart/item/{} for userId={}, qty={}", productId, userId, qty);
        return ResponseEntity.ok(ApiResponse.ok(cartService.updateItem(userId, productId, qty)));
    }

    @DeleteMapping("/cart/item/{productId}")
    public ResponseEntity<ApiResponse<Cart>> removeItem(
            @RequestHeader("X-User") String userId,
            @PathVariable("productId") UUID productId) {
        log.info("DELETE /cart-service/cart/item/{} for userId={}", productId, userId);
        return ResponseEntity.ok(ApiResponse.ok(cartService.removeItem(userId, productId)));
    }

    @DeleteMapping("/cart/clear")
    public ResponseEntity<ApiResponse<Cart>> clearCart(@RequestHeader("X-User") String userId) {
        log.info("DELETE /cart-service/cart/clear for userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(cartService.clearCart(userId)));
    }

    @GetMapping("/cart/checkout")
    public ResponseEntity<ApiResponse<Cart>> getCheckoutSnapshot(@RequestHeader("X-User") String userId) {
        log.info("GET /cart-service/cart/checkout for userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(cartService.getCheckoutSnapshot(userId)));
    }
}
