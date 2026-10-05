package com.despescar.koiiaservice.exception;

/** La sesión tiene dueño y quien consulta no es esa persona (o no se identificó). */
public class KoiSessionForbiddenException extends RuntimeException {
    public KoiSessionForbiddenException() {
        super("La sesión KOI no pertenece al usuario autenticado.");
    }
}
