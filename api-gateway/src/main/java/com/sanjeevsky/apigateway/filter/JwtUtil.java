package com.sanjeevsky.apigateway.filter;

import io.jsonwebtoken.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    public Claims getAllClaimsFromToken(String token) throws Exception {
        Jws<Claims> claimsJws;
        try {
            claimsJws = Jwts.parser().setSigningKey(secret).parseClaimsJws(token);
        } catch (SignatureException ex) {
            throw new Exception("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            throw new Exception("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            throw new Exception("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            throw new Exception("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            throw new Exception("JWT claims string is empty.");
        }
        return claimsJws.getBody();
    }

    private boolean isTokenExpired(String token) {
        try {
            return this.getAllClaimsFromToken(token).getExpiration().before(new Date());
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }

    public boolean isInvalid(String token) {
        return this.isTokenExpired(token);
    }

}