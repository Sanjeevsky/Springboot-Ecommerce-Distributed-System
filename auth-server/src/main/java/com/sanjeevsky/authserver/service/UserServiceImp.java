package com.sanjeevsky.authserver.service;

import com.sanjeevsky.authserver.exceptions.CredentialsMismatchException;
import com.sanjeevsky.authserver.exceptions.NoSuchUserExistsException;
import com.sanjeevsky.authserver.exceptions.UserAlreadyExistsException;
import com.sanjeevsky.authserver.jwtgenerator.JwtTokenGenerator;
import com.sanjeevsky.authserver.modal.LoginDTO;
import com.sanjeevsky.authserver.modal.UpdatePasswordRequest;
import com.sanjeevsky.authserver.modal.User;
import com.sanjeevsky.authserver.modal.UserDTO;
import com.sanjeevsky.authserver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServiceImp implements UserService {
    @Autowired
    private UserRepository repository;
    @Autowired
    private JwtTokenGenerator generator;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public User registerUser(User user) {
        if (repository.findById(user.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("User Already Exists with this Email...!!");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return repository.save(user);
    }

    @Override
    public LoginDTO authenticateUser(UserDTO user) {
        Optional<User> stored = repository.findById(user.getEmail());
        if (stored.isPresent()) {
            if (passwordEncoder.matches(user.getPassword(), stored.get().getPassword())) {
                return generator.generateToken(user);
            } else {
                throw new CredentialsMismatchException("Credentials Mismatch..Please try with valid credentials");
            }
        } else {
            throw new NoSuchUserExistsException("No User Found With Given Credentials...!!");
        }
    }

    @Override
    public String updatePassword(UpdatePasswordRequest request) {
        User user = repository.findById(request.getEmail())
                .orElseThrow(() -> new NoSuchUserExistsException("No user found with email: " + request.getEmail()));
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new CredentialsMismatchException("Old password does not match");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        repository.save(user);
        return "Password updated successfully";
    }
}
