package com.despescar.koiiaservice.exception;

import java.util.UUID;

public class KoiSessionNotFoundException extends RuntimeException {
    public KoiSessionNotFoundException(UUID sessionId) {
        super("No existe una sesión KOI con id " + sessionId);
    }
}
