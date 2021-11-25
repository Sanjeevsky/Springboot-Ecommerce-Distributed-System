package com.sanjeevsky.authserver.controller;

import com.sanjeevsky.authserver.exceptions.CredentialsMismatchException;
import com.sanjeevsky.authserver.exceptions.NoSuchUserExistsException;
import com.sanjeevsky.authserver.exceptions.UserAlreadyExistsException;
import com.sanjeevsky.authserver.modal.LoginDTO;
import com.sanjeevsky.authserver.modal.User;
import com.sanjeevsky.authserver.modal.UserDTO;
import com.sanjeevsky.authserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth-service")
public class UserAuthController {
    private UserService service;
    @Autowired
    public UserAuthController(UserService service) {
        this.service = service;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> saveUser(@RequestBody User user) throws UserAlreadyExistsException {
        return new ResponseEntity<User>(service.registerUser(user), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserDTO user) throws UserAlreadyExistsException, CredentialsMismatchException, NoSuchUserExistsException {
        return new ResponseEntity<LoginDTO>(service.authenticateUser(user), HttpStatus.OK);
    }

    /*
    * Will create sendPasswordRecoveryOtp();
    * */

    //will implement otp validation for forget password
    @PutMapping("/updatePassword")
    public ResponseEntity<?> updatePassword(@RequestBody User user) {
        return new ResponseEntity<String>(service.updatePassword(user), HttpStatus.ACCEPTED);
    }

}
