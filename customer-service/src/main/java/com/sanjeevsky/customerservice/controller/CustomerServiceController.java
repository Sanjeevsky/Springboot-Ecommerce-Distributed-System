package com.sanjeevsky.customerservice.controller;

import com.sanjeevsky.customerservice.model.Address;
import com.sanjeevsky.customerservice.service.AddressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

import static com.sanjeevsky.customerservice.utils.CommonConstants.UNAUTHORIZED_ACCESS;

@Slf4j
@RestController
@RequestMapping("/customer-service")
public class CustomerServiceController {

    private final AddressService addressService;

    public CustomerServiceController(AddressService addressService) {
        this.addressService = addressService;
    }

    @PostMapping("/address")
    public ResponseEntity<?> addAddress(
            @RequestBody Address request,
            @RequestHeader(name = "X-User") String user) {
        if (user == null || user.isEmpty()) {
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        return new ResponseEntity<>(addressService.addAddress(request, user), HttpStatus.OK);
    }

    @GetMapping("/address/{id}")
    public ResponseEntity<?> getAddress(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "X-User") String user) {
        if (user == null || user.isEmpty()) {
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        return new ResponseEntity<>(addressService.getAddress(id, user), HttpStatus.OK);
    }

    @GetMapping("/addresses")
    public ResponseEntity<?> getAddresses(
            @RequestHeader(name = "X-User") String user) {
        if (user == null || user.isEmpty()) {
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        return new ResponseEntity<>(addressService.getAddresses(user), HttpStatus.OK);
    }

    @PutMapping("/address/{id}")
    public ResponseEntity<?> updateAddress(
            @PathVariable UUID id,
            @RequestBody Address address,
            @RequestHeader(name = "X-User") String user) {
        if (user == null || user.isEmpty()) {
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        return new ResponseEntity<>(addressService.updateAddress(id, address, user), HttpStatus.OK);
    }

    @DeleteMapping("/address/{id}")
    public ResponseEntity<?> deleteAddress(
            @PathVariable UUID id,
            @RequestHeader(name = "X-User") String user) {
        if (user == null || user.isEmpty()) {
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        addressService.deleteAddress(id, user);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
