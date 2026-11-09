package com.example.hotelbooking.hotel.security;

import com.example.hotelbooking.hotel.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final int MIN_SECRET_LENGTH_BYTES = 32;

    private final SecretKey verificationKey;

    public JwtService(JwtProperties properties) {
        byte[] secret = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "security.jwt.secret must be at least " + MIN_SECRET_LENGTH_BYTES + " bytes long (HS256)");
        }
        this.verificationKey = Keys.hmacShaKeyFor(secret);
    }

    public JwtPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(verificationKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String username = claims.getSubject();
        if (username == null || username.isBlank()) {
            throw new JwtException("Access token carries no subject");
        }

        TokenRole role = TokenRole.from(claims.get("role", String.class))
                .orElseThrow(() -> new JwtException("Access token carries no known role"));

        Object uid = claims.get("uid");
        Long userId = uid instanceof Number number ? Long.valueOf(number.longValue()) : null;

        return new JwtPrincipal(userId, username, role);
    }
}
