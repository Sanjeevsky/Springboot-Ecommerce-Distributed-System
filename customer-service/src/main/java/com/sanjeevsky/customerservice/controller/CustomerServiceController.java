package com.sanjeevsky.customerservice.controller;

import com.sanjeevsky.customerservice.model.Address;
import com.sanjeevsky.customerservice.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.sanjeevsky.customerservice.utils.CommonConstants.UNAUTHORIZED_ACCESS;

@RestController
@RequestMapping("/customer-service")
public class CustomerServiceController {
    @Autowired
    private AddressService addressService;
    @PostMapping("/address")
    public ResponseEntity<?> addAddress(@RequestBody Address request ,@RequestHeader(name = "user") String user){
        if (user.isEmpty()){
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        return new ResponseEntity<>(addressService.addAddress(request,user), HttpStatus.OK);
    }

    @GetMapping("/address/{id}")
    public ResponseEntity<?> getAddress(@PathVariable(name = "uuid") UUID uuid,@RequestHeader(name = "user") String user){
        if (user.isEmpty()){
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        return new ResponseEntity<>(addressService.getAddress(uuid,user),HttpStatus.OK);
    }

    @GetMapping("/addresses")
    public ResponseEntity<?> getAddresses(@RequestHeader(name = "user") String user){
        if (user.isEmpty()){
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        return new ResponseEntity<>(addressService.getAddresses(user),HttpStatus.OK);
    }
}
