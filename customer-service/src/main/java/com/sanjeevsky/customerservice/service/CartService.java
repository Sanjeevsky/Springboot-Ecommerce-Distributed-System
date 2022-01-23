package com.sanjeevsky.customerservice.service;

import com.sanjeevsky.customerservice.model.Cart;

import java.util.UUID;

public interface CartService {
    Cart getCart(String user);
    Cart clearCart(String user);
    Cart updateCart(String user, UUID productID, int qty);
    Cart removeProduct(String user, UUID productID);
    Cart updateProduct(String user, UUID productID,int qty);
}
