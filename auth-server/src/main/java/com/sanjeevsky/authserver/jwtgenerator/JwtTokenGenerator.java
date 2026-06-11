package com.sanjeevsky.authserver.jwtgenerator;

import com.sanjeevsky.authserver.modal.LoginDTO;
import com.sanjeevsky.authserver.modal.Role;
import com.sanjeevsky.authserver.modal.UserDTO;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Date;
@Component
@Configuration
public class JwtTokenGenerator {
    public static final long VALIDITY = 24L * 60 * 60 * 1000; // 24 hours
    @Value("${jwt.secret}")
    private String secret;

    public LoginDTO generateToken(UserDTO user, Role role) {
        Role effectiveRole = role == null ? Role.CUSTOMER : role;
        final String jwt = Jwts.builder()
                .setIssuer("EcommerceApplicationAdmin")
                .setSubject(user.getEmail())
                .claim("role", effectiveRole.name())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + VALIDITY))
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();

        return new LoginDTO(user.getEmail(), jwt, effectiveRole.name());
    }
}
