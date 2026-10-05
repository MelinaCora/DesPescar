package com.despescar.reservationservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.dto.reservation.request.SeatMessageRequest;
import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.service.SeatService;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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

    @Test
    void elegirYSoltarUnAsientoNoEscribeEnLaSalidaEstandar() {
        SeatService seatService = mock(SeatService.class);
        Seat asiento = new Seat();
        asiento.setSeatUuid(UUID.randomUUID());
        asiento.setStatusSeat("RESERVADO_TEMPORAL");
        when(seatService.blockedSeat(any(), any())).thenReturn(asiento);
        when(seatService.unblockSeat(any(), any())).thenReturn(asiento);
        BookingWebSocketController conAsiento = new BookingWebSocketController(seatService, mock(SimpMessagingTemplate.class));
        SeatMessageRequest pedido = new SeatMessageRequest();
        pedido.setSeatUuid(asiento.getSeatUuid());
        PrintStream original = System.out;
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        System.setOut(new PrintStream(salida, true, StandardCharsets.UTF_8));
        try {
            conAsiento.processSeatSelection(UUID.randomUUID(), pedido, () -> "7");
            conAsiento.processSeatDeselection(UUID.randomUUID(), pedido, () -> "7");
        } finally {
            System.setOut(original);
        }

        assertFalse(salida.toString(StandardCharsets.UTF_8).contains(asiento.getSeatUuid().toString()),
                "no se escribe en la salida estándar: " + salida.toString(StandardCharsets.UTF_8));
    }
}
