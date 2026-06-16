package com.sanjeevsky.authserver.service;

import com.sanjeevsky.authserver.exceptions.CredentialsMismatchException;
import com.sanjeevsky.authserver.exceptions.InvalidAuthRequestException;
import com.sanjeevsky.authserver.exceptions.NoSuchUserExistsException;
import com.sanjeevsky.authserver.exceptions.UserAlreadyExistsException;
import com.sanjeevsky.authserver.jwtgenerator.JwtTokenGenerator;
import com.sanjeevsky.authserver.modal.LoginDTO;
import com.sanjeevsky.authserver.modal.Role;
import com.sanjeevsky.authserver.modal.UpdatePasswordRequest;
import com.sanjeevsky.authserver.modal.User;
import com.sanjeevsky.authserver.modal.UserDTO;
import com.sanjeevsky.authserver.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServiceImp implements UserService {
    private final UserRepository repository;
    private final JwtTokenGenerator generator;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserServiceImp(
            UserRepository repository,
            JwtTokenGenerator generator,
            BCryptPasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.generator = generator;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User registerUser(User user) {
        validateRegistrationRequest(user);
        if (repository.findById(user.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("User Already Exists with this Email...!!");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        // Roles are never client-assignable: every self-registration is a customer.
        user.setRole(Role.CUSTOMER);
        return repository.save(user);
    }

    @Override
    public LoginDTO authenticateUser(UserDTO user) {
        validateLoginRequest(user);
        Optional<User> stored = repository.findById(user.getEmail());
        // A failed login returns a uniform 401 whether the email is unknown or the
        // password is wrong: a 404 for unknown emails would let callers enumerate which
        // accounts exist, and a generic message keeps the two cases indistinguishable.
        if (stored.isPresent() && passwordEncoder.matches(user.getPassword(), stored.get().getPassword())) {
            return generator.generateToken(user, stored.get().getRole());
        }
        throw new CredentialsMismatchException("Invalid email or password");
    }

    @Override
    public String updatePassword(UpdatePasswordRequest request) {
        validateUpdatePasswordRequest(request);
        User user = repository.findById(request.getEmail())
                .orElseThrow(() -> new NoSuchUserExistsException("No user found with email: " + request.getEmail()));
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new CredentialsMismatchException("Old password does not match");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        repository.save(user);
        return "Password updated successfully";
    }

    private void validateRegistrationRequest(User user) {
        if (user == null) {
            throw new InvalidAuthRequestException("User request is required");
        }
        user.setEmail(normalizeEmail(user.getEmail()));
        validatePassword(user.getPassword(), "Password");
    }

    private void validateLoginRequest(UserDTO user) {
        if (user == null) {
            throw new InvalidAuthRequestException("Login request is required");
        }
        user.setEmail(normalizeEmail(user.getEmail()));
        validatePassword(user.getPassword(), "Password");
    }

    private void validateUpdatePasswordRequest(UpdatePasswordRequest request) {
        if (request == null) {
            throw new InvalidAuthRequestException("Update password request is required");
        }
        request.setEmail(normalizeEmail(request.getEmail()));
        validateRequiredPassword(request.getOldPassword(), "Old password");
        validatePassword(request.getNewPassword(), "New password");
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? null : email.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new InvalidAuthRequestException("Email is required");
        }
        if (!normalized.contains("@")) {
            throw new InvalidAuthRequestException("Invalid email format");
        }
        return normalized;
    }

    private void validatePassword(String password, String fieldName) {
        validateRequiredPassword(password, fieldName);
        if (password.length() < 6) {
            throw new InvalidAuthRequestException(fieldName + " must be at least 6 characters");
        }
    }

    private void validateRequiredPassword(String password, String fieldName) {
        if (password == null || password.trim().isEmpty()) {
            throw new InvalidAuthRequestException(fieldName + " is required");
        }
    }
}
