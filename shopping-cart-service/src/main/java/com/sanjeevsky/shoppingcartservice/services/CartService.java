package com.sanjeevsky.shoppingcartservice.services;

import com.sanjeevsky.shoppingcartservice.model.Cart;

import java.util.UUID;

public interface CartService {

    Cart getOrCreateCart(String userId);

    Cart addItem(String userId, UUID productId, UUID variantId, int qty);

    Cart updateItem(String userId, UUID productId, int qty);

    Cart removeItem(String userId, UUID productId);

    Cart clearCart(String userId);

    Cart getCheckoutSnapshot(String userId);
}
