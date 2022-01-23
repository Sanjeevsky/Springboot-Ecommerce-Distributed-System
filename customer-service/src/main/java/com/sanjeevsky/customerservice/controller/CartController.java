package com.sanjeevsky.customerservice.controller;

import com.sanjeevsky.customerservice.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.sanjeevsky.customerservice.utils.CommonConstants.UNAUTHORIZED_ACCESS;

@RestController
@RequestMapping("/customer-service")
public class CartController {
    @Autowired
    private CartService cartService;
    @GetMapping("/cart")
    public ResponseEntity<?> getCart(@RequestHeader(name = "user") String user){
        if (user.isEmpty()){
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        return new ResponseEntity<>(cartService.getCart(user), HttpStatus.OK);
    }

    @DeleteMapping("/clear-cart")
    public ResponseEntity<?> cleanCart(@RequestHeader(name = "user") String user) {
        if (user.isEmpty()){
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        return new ResponseEntity<>(cartService.clearCart(user), HttpStatus.ACCEPTED);
    }

    @PutMapping("/updateCart/{productId}/{qty}")
    public ResponseEntity<?> updateCart(@RequestHeader(name = "user") String user, @PathVariable("productId") UUID productID,@PathVariable("qty") int qty){
        if (user.isEmpty()){
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        return new ResponseEntity<>(cartService.updateCart(user,productID,qty),HttpStatus.ACCEPTED);
    }
}
