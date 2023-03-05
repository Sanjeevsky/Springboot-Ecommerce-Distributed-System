package com.sanjeevsky.customerservice.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name="catalog-service")
public interface ProductFeignClient {

    @GetMapping("/getProduct/{id}")
    public ResponseEntity<?> getProduct(@PathVariable("id")UUID uuid);
}
