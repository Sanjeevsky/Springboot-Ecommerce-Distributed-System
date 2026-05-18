package com.sanjeevsky.wishlistservice.controller;

import com.sanjeevsky.platform.response.ApiResponse;
import com.sanjeevsky.wishlistservice.dto.AddToWishlistRequest;
import com.sanjeevsky.wishlistservice.model.WishlistItem;
import com.sanjeevsky.wishlistservice.service.WishlistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/wishlist-service")
public class WishlistController {

    @Autowired
    private WishlistService wishlistService;

    @PostMapping("/wishlist")
    public ResponseEntity<ApiResponse<WishlistItem>> addToWishlist(
            @RequestHeader("X-User") String userId,
            @Valid @RequestBody AddToWishlistRequest request) {
        log.info("Received request to add item to wishlist for userId: {}", userId);
        return new ResponseEntity<>(ApiResponse.ok("Item added to wishlist", wishlistService.addToWishlist(userId, request)), HttpStatus.CREATED);
    }

    @GetMapping("/wishlist")
    public ResponseEntity<ApiResponse<List<WishlistItem>>> getWishlist(
            @RequestHeader("X-User") String userId) {
        log.info("Received request to fetch wishlist for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.ok(wishlistService.getWishlist(userId)));
    }

    @DeleteMapping("/wishlist/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeFromWishlist(
            @RequestHeader("X-User") String userId,
            @PathVariable("productId") UUID productId) {
        log.info("Received request to remove productId: {} from wishlist for userId: {}", productId, userId);
        wishlistService.removeFromWishlist(userId, productId);
        return ResponseEntity.ok(ApiResponse.ok("Item removed from wishlist", null));
    }

    @PostMapping("/wishlist/{productId}/move-to-cart")
    public ResponseEntity<ApiResponse<WishlistItem>> moveToCart(
            @RequestHeader("X-User") String userId,
            @PathVariable("productId") UUID productId) {
        log.info("Received request to move productId: {} to cart for userId: {}", productId, userId);
        WishlistItem item = wishlistService.moveToCart(userId, productId);
        return ResponseEntity.ok(ApiResponse.ok("Item moved to cart", item));
    }
}
