package com.sanjeevsky.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private static final String SECRET = "test-secret";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
    }

    @Test
    void getAllClaimsFromToken_validToken_returnsClaims() throws Exception {
        String token = tokenWithExpiration(new Date(System.currentTimeMillis() + 60_000L));

        Claims claims = jwtUtil.getAllClaimsFromToken(token);

        assertEquals("buyer@example.com", claims.getSubject());
        assertFalse(jwtUtil.isInvalid(token));
    }

    @Test
    void isInvalid_expiredToken_returnsTrue() {
        String token = tokenWithExpiration(new Date(System.currentTimeMillis() - 60_000L));

        assertTrue(jwtUtil.isInvalid(token));
    }

    @Test
    void isInvalid_malformedToken_returnsTrue() {
        assertTrue(jwtUtil.isInvalid("not-a-jwt"));
    }

    @Test
    void getAllClaimsFromToken_tokenSignedWithDifferentSecret_throws() {
        String token = Jwts.builder()
                .setSubject("buyer@example.com")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(SignatureAlgorithm.HS256, "wrong-secret")
                .compact();

        Exception exception = assertThrows(Exception.class, () -> jwtUtil.getAllClaimsFromToken(token));
        assertEquals("Invalid JWT signature", exception.getMessage());
    }

    private String tokenWithExpiration(Date expiration) {
        return Jwts.builder()
                .setSubject("buyer@example.com")
                .setExpiration(expiration)
                .signWith(SignatureAlgorithm.HS256, SECRET)
                .compact();
    }
}
