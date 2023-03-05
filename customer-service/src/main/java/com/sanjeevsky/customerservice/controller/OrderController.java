package com.sanjeevsky.customerservice.controller;

import com.sanjeevsky.customerservice.model.Order;
import com.sanjeevsky.customerservice.service.OrderService;
import org.aspectj.weaver.ast.Or;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.sanjeevsky.customerservice.utils.CommonConstants.UNAUTHORIZED_ACCESS;

@RestController
@RequestMapping("/customer-service")
public class OrderController {

    @Autowired
    OrderService orderService;

    @GetMapping("/order/{id}")
    public ResponseEntity<?> getOrder(@RequestHeader(name = "user") UUID user,@PathVariable("id") UUID id){
        if (user.toString().isEmpty()){
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        return new ResponseEntity<>(orderService.getOrderById(user,id), HttpStatus.OK);
    }

    @PostMapping("/order")
    public ResponseEntity<?> addOrder(@RequestBody Order order){
        return null;
    }

}
