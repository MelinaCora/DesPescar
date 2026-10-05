package com.despescar.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;

final class JwtSupport {

    static final String SECRET = "test-secret-key-for-common-security-1234567890";

    private JwtSupport() {
    }

    static String token(String email, Long userId, String role, long ttlMs) {
        var builder = Jwts.builder()
                .subject(email)
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)));
        if (userId != null) {
            builder.claim("userId", userId);
        }
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.compact();
    }
}
