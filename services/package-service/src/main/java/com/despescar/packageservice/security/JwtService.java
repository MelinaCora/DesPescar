package com.despescar.identityservice.security;

import java.security.Key;
import java.util.Date;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Claims;

@Service
public class JwtService {

    private static final String SECRET_KEY =
            "despescar-super-secret-key-for-jwt-token-2026";

    private final Key key =
            Keys.hmacShaKeyFor(
                    SECRET_KEY.getBytes()
            );

    public String generateToken(
            String email,
            String role
    ) {

        return Jwts.builder()

                .subject(email) //guarda quien inicio sesion

                .claim("role", role) //guarda informacion de rol

                .issuedAt(
                        new Date()
                )

                .expiration(
                        new Date(
                                System.currentTimeMillis() //define cuando expira el token (24 hs)
                                        + 1000 * 60 * 60 * 24
                        )
                )

                .signWith(key) //firma difitalmente el token

                .compact();
    }
    public String extractUsername(String token) {

        return extractAllClaims(token)
                .getSubject();
    }

    public boolean isTokenValid(
            String token,
            String email
    ) {

        String username =
                extractUsername(token);

        return username.equals(email)
                && !isTokenExpired(token);
    }

    private boolean isTokenExpired(
            String token
    ) {

        return extractAllClaims(token)
                .getExpiration()
                .before(new Date());
    }

    private Claims extractAllClaims(
            String token
    ) {

        return Jwts.parser()

                .verifyWith(
                        (javax.crypto.SecretKey) key
                )

                .build()

                .parseSignedClaims(token)

                .getPayload();
    }
}