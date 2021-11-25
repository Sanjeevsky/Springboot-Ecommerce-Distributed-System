package com.sanjeevsky.customerservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customer-service")
public class CustomerServiceController {

    @GetMapping("/test")
    public String test(){
        return "hello";
    }
}
