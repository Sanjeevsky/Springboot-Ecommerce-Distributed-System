package com.sanjeevsky.authserver.config;

import com.sanjeevsky.authserver.modal.Role;
import com.sanjeevsky.authserver.modal.User;
import com.sanjeevsky.authserver.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Seeds (or promotes) the platform admin account on startup. init-db.sql only
 * runs on a fresh MySQL volume and cannot bcrypt-encode passwords, so the
 * admin user is managed here instead. Idempotent: an existing admin is left
 * untouched, an existing customer with the configured email is promoted.
 */
@Component
@ConditionalOnProperty(name = "admin.seed.enabled", havingValue = "true")
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserRepository repository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${admin.seed.email}")
    private String adminEmail;

    @Value("${admin.seed.password}")
    private String adminPassword;

    public AdminUserSeeder(UserRepository repository, BCryptPasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        Optional<User> existing = repository.findById(adminEmail);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getRole() != Role.ADMIN) {
                user.setRole(Role.ADMIN);
                repository.save(user);
                log.info("Promoted existing user {} to ADMIN", adminEmail);
            }
            return;
        }
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        repository.save(admin);
        log.info("Seeded admin user {}", adminEmail);
    }
}
