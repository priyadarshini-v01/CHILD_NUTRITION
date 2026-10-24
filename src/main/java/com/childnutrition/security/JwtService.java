package com.childnutrition.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;

    public JwtService(@Value("${jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be set to a base64-encoded key of at least 256 bits");
        }
        byte[] decoded;
        try { decoded = Decoders.BASE64.decode(secret); }
        catch (RuntimeException ex) { throw new IllegalStateException("JWT_SECRET must be base64 encoded", ex); }
        if (decoded.length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 256 bits");
        this.key = Keys.hmacShaKeyFor(decoded);
    }

    public String issue(String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder().subject(email).claim("role", role)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(86_400)))
                .signWith(key).compact();
    }

    public String subject(String token) { return claims(token).getSubject(); }

    public boolean isValid(String token, String email) {
        try { return email.equals(subject(token)) && claims(token).getExpiration().after(new Date()); }
        catch (RuntimeException ex) { return false; }
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
