package com.sanjeevsky.shoppingcartservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cart-service/")
@Slf4j
public class CartController {

    @GetMapping("/getCart")
    public ResponseEntity<?> getCart(){
        return new ResponseEntity<>("Cart",HttpStatus.OK);
    }
}
