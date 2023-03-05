package com.sanjeevsky.customerservice.service.impl;

import com.sanjeevsky.customerservice.clients.ProductFeignClient;
import com.sanjeevsky.customerservice.exceptions.CartDoesnotExistsException;
import com.sanjeevsky.customerservice.exceptions.InvalidRequestException;
import com.sanjeevsky.customerservice.model.Cart;
import com.sanjeevsky.customerservice.repository.CartRepository;
import com.sanjeevsky.customerservice.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {
    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductFeignClient productFeignClient;
    @Override
    public Cart getCart(String user) {
        Optional<Cart> cart = cartRepository.findByUser(user);
        if (cart.isEmpty()){
            Cart cart1 = Cart.builder().cartQty(0).cartTotal(0.0).user(user).build();
            return cartRepository.save(cart1);
        }
        return cart.get();
    }

    @Override
    public Cart clearCart(String user) {
        Optional<Cart> optionalCart = cartRepository.findByUser(user);
        if (optionalCart.isEmpty()){
            throw new CartDoesnotExistsException("Cart Doesn't Exist");
        }
        Cart cart = optionalCart.get();
        cart.setCartQty(0);
        cart.setCartTotal(0);
        cart.getProductItems().clear();
        return cartRepository.save(cart);
    }

    @Override
    public Cart updateCart(String user, UUID productID, int qty) {
        if (qty<0) throw new InvalidRequestException("Quantity Can't be Negative");
        try {
            ResponseEntity<?> product = productFeignClient.getProduct(productID);
            switch (product.getStatusCode()){
                case OK:
                {
                    break;
                }
                case NOT_FOUND:
                {
                    break;
                }
                case INTERNAL_SERVER_ERROR:
                {
                    break;
                }

            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (qty==0){
           return this.removeProduct(user,productID);
        }else {
            return this.updateProduct(user,productID,qty);
        }
    }

    @Override
    public Cart removeProduct(String user, UUID productID) {
        Optional<Cart> optionalCart = cartRepository.findByUser(user);
        if (optionalCart.isEmpty()){
            throw new CartDoesnotExistsException("Cart Doesn't Exits Exception");
        }
        Cart cart = optionalCart.get();
        cart.getProductItems().stream().filter(productItem -> productItem.getProductId() != productID).collect(Collectors.toList());
        return cartRepository.save(cart);
    }

    @Override
    public Cart updateProduct(String user, UUID productID, int qty) {
        Optional<Cart> optionalCart = cartRepository.findByUser(user);
        if (optionalCart.isEmpty()){
            throw new CartDoesnotExistsException("Cart Doesn't Exits Exception");
        }
        Cart cart = optionalCart.get();
        cart.getProductItems().stream().peek(productItem -> {
            if (productItem.getProductId()== productID){
                productItem.setQty(qty);
            }
        }).collect(Collectors.toList());
        return cartRepository.save(cart);
    }
}
