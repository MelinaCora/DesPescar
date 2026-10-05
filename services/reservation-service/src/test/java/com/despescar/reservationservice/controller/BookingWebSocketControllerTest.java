package com.despescar.reservationservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.service.SeatService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class BookingWebSocketControllerTest {

    private final BookingWebSocketController controller =
            new BookingWebSocketController(mock(SeatService.class), mock(SimpMessagingTemplate.class));

    @Test
    void unErrorDelNegocioLlegaConSuMensaje() {
        assertEquals("El asiento 1A ya fue reservado por otra persona.", controller.manejarExcepcion(
                new BookingException("ASIENTO_NO_DISPONIBLE", "El asiento 1A ya fue reservado por otra persona.", HttpStatus.CONFLICT)));
    }

    @Test
    void unErrorInesperadoNoMuestraElDetalle() {
        String mensaje = controller.manejarExcepcion(new IllegalStateException(
                "could not execute statement [Duplicate entry] [update seats set status_seat=? where seat_uuid=?]"));

        assertEquals("Ocurrió un error inesperado.", mensaje);
    }
}
