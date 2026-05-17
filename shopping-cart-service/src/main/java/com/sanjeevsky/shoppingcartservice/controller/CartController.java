package com.sanjeevsky.shoppingcartservice.controller;

import com.sanjeevsky.shoppingcartservice.model.Cart;
import com.sanjeevsky.shoppingcartservice.model.dto.AddItemRequest;
import com.sanjeevsky.shoppingcartservice.services.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/cart-service")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/cart")
    public ResponseEntity<?> getCart(@RequestHeader(value = "X-User", required = false) String userId) {
        log.info("GET /cart-service/cart for userId={}", userId);
        if (userId == null || userId.isBlank()) {
            return new ResponseEntity<>("Missing or invalid X-User header", HttpStatus.BAD_REQUEST);
        }
        Cart cart = cartService.getOrCreateCart(userId);
        return new ResponseEntity<>(cart, HttpStatus.OK);
    }

    @PostMapping("/cart/add")
    public ResponseEntity<?> addItem(
            @RequestHeader(value = "X-User", required = false) String userId,
            @RequestBody AddItemRequest request) {
        log.info("POST /cart-service/cart/add for userId={}, productId={}", userId, request.getProductId());
        if (userId == null || userId.isBlank()) {
            return new ResponseEntity<>("Missing or invalid X-User header", HttpStatus.BAD_REQUEST);
        }
        Cart cart = cartService.addItem(userId, request.getProductId(), request.getVariantId(), request.getQty());
        return new ResponseEntity<>(cart, HttpStatus.OK);
    }

    @PutMapping("/cart/item/{productId}")
    public ResponseEntity<?> updateItem(
            @RequestHeader(value = "X-User", required = false) String userId,
            @PathVariable("productId") UUID productId,
            @RequestParam("qty") int qty) {
        log.info("PUT /cart-service/cart/item/{} for userId={}, qty={}", productId, userId, qty);
        if (userId == null || userId.isBlank()) {
            return new ResponseEntity<>("Missing or invalid X-User header", HttpStatus.BAD_REQUEST);
        }
        Cart cart = cartService.updateItem(userId, productId, qty);
        return new ResponseEntity<>(cart, HttpStatus.OK);
    }

    @DeleteMapping("/cart/item/{productId}")
    public ResponseEntity<?> removeItem(
            @RequestHeader(value = "X-User", required = false) String userId,
            @PathVariable("productId") UUID productId) {
        log.info("DELETE /cart-service/cart/item/{} for userId={}", productId, userId);
        if (userId == null || userId.isBlank()) {
            return new ResponseEntity<>("Missing or invalid X-User header", HttpStatus.BAD_REQUEST);
        }
        Cart cart = cartService.removeItem(userId, productId);
        return new ResponseEntity<>(cart, HttpStatus.OK);
    }

    @DeleteMapping("/cart/clear")
    public ResponseEntity<?> clearCart(@RequestHeader(value = "X-User", required = false) String userId) {
        log.info("DELETE /cart-service/cart/clear for userId={}", userId);
        if (userId == null || userId.isBlank()) {
            return new ResponseEntity<>("Missing or invalid X-User header", HttpStatus.BAD_REQUEST);
        }
        Cart cart = cartService.clearCart(userId);
        return new ResponseEntity<>(cart, HttpStatus.OK);
    }

    @GetMapping("/cart/checkout")
    public ResponseEntity<?> getCheckoutSnapshot(@RequestHeader(value = "X-User", required = false) String userId) {
        log.info("GET /cart-service/cart/checkout for userId={}", userId);
        if (userId == null || userId.isBlank()) {
            return new ResponseEntity<>("Missing or invalid X-User header", HttpStatus.BAD_REQUEST);
        }
        Cart cart = cartService.getCheckoutSnapshot(userId);
        return new ResponseEntity<>(cart, HttpStatus.OK);
    }
}
