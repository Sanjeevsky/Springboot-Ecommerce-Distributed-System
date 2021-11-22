package com.sanjeevsky.authserver.jwtgenerator;

import com.sanjeevsky.authserver.modal.LoginDTO;
import com.sanjeevsky.authserver.modal.User;
import com.sanjeevsky.authserver.modal.UserDTO;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Date;
@Component




public class JwtTokenGenerator {
    public static final int VALIDITY = 600 * 1000;
    @Value("${jwt.secret}")
    private String secret;

    public LoginDTO generateToken(UserDTO user) {
        final String jwt = Jwts.builder()
                .setIssuer("EcommerceApplicationAdmin")
                .setSubject(user.getPassword())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + VALIDITY))
                .signWith(SignatureAlgorithm.ES512, secret)
                .compact();

        return new LoginDTO(user.getEmail(), jwt);
    }
}
