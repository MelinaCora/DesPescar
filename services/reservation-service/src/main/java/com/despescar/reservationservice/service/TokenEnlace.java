package com.despescar.reservationservice.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Token del enlace de invitación (D-b6): 32 bytes de SecureRandom en base64url sin relleno. Es la
 * única forma de sumarse a un grupo; no deriva del id de la reserva.
 */
@Component
public class TokenEnlace {

    private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final SecureRandom random = new SecureRandom();

    public String nuevo() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Un token mal formado se trata igual que uno inexistente (404, sin oráculo). */
    public static boolean formatoValido(String token) {
        return token != null && FORMATO.matcher(token).matches();
    }
}
