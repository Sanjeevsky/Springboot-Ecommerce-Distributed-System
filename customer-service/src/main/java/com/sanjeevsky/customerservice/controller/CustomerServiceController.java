package com.sanjeevsky.customerservice.controller;

import com.sanjeevsky.customerservice.model.Address;
import com.sanjeevsky.customerservice.service.AddressService;
import com.sanjeevsky.platform.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/customer-service")
public class CustomerServiceController {

    private final AddressService addressService;

    public CustomerServiceController(AddressService addressService) {
        this.addressService = addressService;
    }

    @PostMapping("/address")
    public ResponseEntity<ApiResponse<Address>> addAddress(
            @Valid @RequestBody Address request,
            @RequestHeader(name = "X-User") String user) {
        return new ResponseEntity<>(ApiResponse.ok(addressService.addAddress(request, user)), HttpStatus.CREATED);
    }

    @GetMapping("/address/{id}")
    public ResponseEntity<ApiResponse<Address>> getAddress(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "X-User") String user) {
        return ResponseEntity.ok(ApiResponse.ok(addressService.getAddress(id, user)));
    }

    @GetMapping("/addresses")
    public ResponseEntity<ApiResponse<?>> getAddresses(
            @RequestHeader(name = "X-User") String user) {
        return ResponseEntity.ok(ApiResponse.ok(addressService.getAddresses(user)));
    }

    @PutMapping("/address/{id}")
    public ResponseEntity<ApiResponse<Address>> updateAddress(
            @PathVariable UUID id,
            @RequestBody Address address,
            @RequestHeader(name = "X-User") String user) {
        return ResponseEntity.ok(ApiResponse.ok(addressService.updateAddress(id, address, user)));
    }

    @DeleteMapping("/address/{id}")
    public ResponseEntity<Void> deleteAddress(
            @PathVariable UUID id,
            @RequestHeader(name = "X-User") String user) {
        addressService.deleteAddress(id, user);
        return ResponseEntity.noContent().build();
    }
}
