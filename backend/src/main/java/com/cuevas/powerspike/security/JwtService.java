package com.cuevas.powerspike.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Genera y valida tokens JWT.
 * El token lleva como subject el id del usuario y claims con mail/username/role.
 * Duración configurable vía jwt.expiration-hours (por defecto 720h = 30 días).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationHours;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-hours:720}") long expirationHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
    }

    public String generateToken(Long userId, String mail, String username, String role) {
        long now = System.currentTimeMillis();
        long expiry = now + expirationHours * 3600_000;

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("mail", mail)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * Valida la firma y expiración del token y devuelve sus claims.
     * Lanza JwtException si es inválido/expirado.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractMail(Claims claims) {
        return claims.get("mail", String.class);
    }

    public String extractUsername(Claims claims) {
        return claims.get("username", String.class);
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }
}
