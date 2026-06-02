package com.sanjeevsky.wishlistservice.service.impl;

import com.sanjeevsky.wishlistservice.clients.CartFeignClient;
import com.sanjeevsky.wishlistservice.dto.AddToWishlistRequest;
import com.sanjeevsky.wishlistservice.exceptions.WishlistItemAlreadyExistsException;
import com.sanjeevsky.wishlistservice.exceptions.WishlistItemNotFoundException;
import com.sanjeevsky.wishlistservice.model.WishlistItem;
import com.sanjeevsky.wishlistservice.repository.WishlistRepository;
import com.sanjeevsky.wishlistservice.service.WishlistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final CartFeignClient cartFeignClient;

    public WishlistServiceImpl(WishlistRepository wishlistRepository, CartFeignClient cartFeignClient) {
        this.wishlistRepository = wishlistRepository;
        this.cartFeignClient = cartFeignClient;
    }

    @Override
    public WishlistItem addToWishlist(String userId, AddToWishlistRequest request) {
        log.info("Adding productId: {} to wishlist for userId: {}", request.getProductId(), userId);

        wishlistRepository.findByUserIdAndProductId(userId, request.getProductId()).ifPresent(existing -> {
            throw new WishlistItemAlreadyExistsException(
                    "Product " + request.getProductId() + " is already in wishlist for user " + userId);
        });

        WishlistItem item = WishlistItem.builder()
                .userId(userId)
                .productId(request.getProductId())
                .productName(request.getProductName())
                .salePrice(request.getSalePrice())
                .build();

        WishlistItem saved = wishlistRepository.save(item);
        log.info("WishlistItem saved with id: {}", saved.getId());
        return saved;
    }

    @Override
    public List<WishlistItem> getWishlist(String userId) {
        log.info("Fetching wishlist for userId: {}", userId);
        return wishlistRepository.findByUserId(userId);
    }

    @Override
    public void removeFromWishlist(String userId, UUID productId) {
        log.info("Removing productId: {} from wishlist for userId: {}", productId, userId);
        wishlistRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new WishlistItemNotFoundException(
                        "Product " + productId + " not found in wishlist for user " + userId));
        wishlistRepository.deleteByUserIdAndProductId(userId, productId);
        log.info("ProductId: {} removed from wishlist for userId: {}", productId, userId);
    }

    @Override
    public WishlistItem moveToCart(String userId, UUID productId) {
        log.info("Moving productId: {} to cart for userId: {}", productId, userId);
        WishlistItem item = wishlistRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new WishlistItemNotFoundException(
                        "Product " + productId + " not found in wishlist for user " + userId));

        try {
            Map<String, Object> cartItem = new HashMap<>();
            cartItem.put("productId", item.getProductId().toString());
            cartItem.put("productName", item.getProductName());
            cartItem.put("qty", 1);
            cartItem.put("unitPrice", item.getSalePrice());
            cartFeignClient.addItem(userId, cartItem);
            log.info("ProductId: {} added to cart for userId: {}", productId, userId);
        } catch (Exception e) {
            log.warn("Failed to add item to cart during move-to-cart, item remains in wishlist: {}", e.getMessage());
            throw e;
        }

        wishlistRepository.deleteByUserIdAndProductId(userId, productId);
        log.info("ProductId: {} removed from wishlist after move-to-cart for userId: {}", productId, userId);
        return item;
    }
}
