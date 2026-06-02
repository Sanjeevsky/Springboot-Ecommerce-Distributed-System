package com.sanjeevsky.apigateway.filter;

import io.jsonwebtoken.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret}")
    private String secret;

    public Claims getAllClaimsFromToken(String token) throws Exception {
        Jws<Claims> claimsJws;
        try {
            claimsJws = Jwts.parser().setSigningKey(secret).parseClaimsJws(token);
        } catch (SignatureException ex) {
            throw new Exception("Invalid JWT signature", ex);
        } catch (MalformedJwtException ex) {
            throw new Exception("Invalid JWT token", ex);
        } catch (ExpiredJwtException ex) {
            throw new Exception("Expired JWT token", ex);
        } catch (UnsupportedJwtException ex) {
            throw new Exception("Unsupported JWT token", ex);
        } catch (IllegalArgumentException ex) {
            throw new Exception("JWT claims string is empty.", ex);
        }
        return claimsJws.getBody();
    }

    private boolean isTokenExpired(String token) {
        try {
            return this.getAllClaimsFromToken(token).getExpiration().before(new Date());
        } catch (Exception e) {
            log.debug("JWT token rejected: {}", e.getMessage());
            return true;
        }
    }

    public boolean isInvalid(String token) {
        return this.isTokenExpired(token);
    }

}
