package com.sanjeevsky.wishlistservice.service;

import com.sanjeevsky.wishlistservice.dto.AddToWishlistRequest;
import com.sanjeevsky.wishlistservice.model.WishlistItem;

import java.util.List;
import java.util.UUID;

public interface WishlistService {

    WishlistItem addToWishlist(String userId, AddToWishlistRequest request);

    List<WishlistItem> getWishlist(String userId);

    void removeFromWishlist(String userId, UUID productId);

    WishlistItem moveToCart(String userId, UUID productId);
}
