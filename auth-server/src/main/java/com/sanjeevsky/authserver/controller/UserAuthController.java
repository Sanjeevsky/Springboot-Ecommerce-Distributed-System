package com.sanjeevsky.authserver.controller;

import com.sanjeevsky.authserver.exceptions.UserAlreadyExistsException;
import com.sanjeevsky.authserver.modal.UpdatePasswordRequest;
import com.sanjeevsky.authserver.modal.User;
import com.sanjeevsky.authserver.modal.UserDTO;
import com.sanjeevsky.authserver.service.UserService;
import com.sanjeevsky.platform.response.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/auth-service")
public class UserAuthController {
    private UserService service;
    @Autowired
    public UserAuthController(UserService service) {
        this.service = service;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<User>> saveUser(@Valid @RequestBody User user) throws UserAlreadyExistsException {
        return new ResponseEntity<>(ApiResponse.ok("User registered successfully", service.registerUser(user)), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody UserDTO user) {
        return ResponseEntity.ok(ApiResponse.ok(service.authenticateUser(user)));
    }

    @PutMapping("/updatePassword")
    public ResponseEntity<ApiResponse<String>> updatePassword(@Valid @RequestBody UpdatePasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.updatePassword(request)));
    }
}
