package com.despescar.reservationservice.controller;

import com.despescar.reservationservice.dto.reservation.request.SeatMessageRequest;
import com.despescar.reservationservice.dto.reservation.response.SeatResponse;
import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.service.SeatService;
import com.despescar.reservationservice.exception.BookingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BookingWebSocketController {

    private final SeatService seatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/select-seat/{flightId}")
    public void processSeatSelection(@DestinationVariable UUID flightId, SeatMessageRequest request, Principal principal) {
        Long userId = Long.valueOf(principal.getName());

        // Ahora el servicio bloquea usando el ID del asiento directamente
        Seat seatUpdated = seatService.blockedSeat(
                request.getSeatUuid(),
                userId
        );

        // La respuesta DEBE devolver el ID para que el frontend lo procese sin problemas
        SeatResponse response = SeatResponse.builder()
                .seatNumber(seatUpdated.getSeatUuid().toString()) // Recomiendo devolver el UUID en este campo o crear un campo 'seatId'
                .seatStatus(seatUpdated.getStatusSeat())
                .blockedByUserId(userId)
                .build();

        String destination = "/topic/flight/" + flightId;
        messagingTemplate.convertAndSend(destination, response);
    }

    /**
     * Al usuario solo le llega el mensaje de un error del negocio (BookingException, textos fijos);
     * cualquier otro puede traer detalle interno (SQL, clases) y se reemplaza por uno genérico.
     */
    @MessageExceptionHandler
    @SendToUser("/queue/errores")
    public String manejarExcepcion(RuntimeException ex) {
        if (ex instanceof BookingException) {
            log.info("Asiento rechazado por WebSocket: {}", ex.getMessage());
            return ex.getMessage();
        }
        log.error("Error inesperado en el WebSocket de asientos", ex);
        return "Ocurrió un error inesperado.";
    }

    @MessageMapping("/deselect-seat/{flightId}")
    public void processSeatDeselection(@DestinationVariable UUID flightId, SeatMessageRequest request, Principal principal) {

        Long userId = Long.valueOf(principal.getName());

        // Desbloquear usando el ID único
        Seat seatUpdated = seatService.unblockSeat(
                request.getSeatUuid(),
                userId
        );

        SeatResponse response = SeatResponse.builder()
                .seatNumber(seatUpdated.getSeatUuid().toString()) // Mismo caso: devuelve el UUID
                .seatStatus("DISPONIBLE")
                .blockedByUserId(null)
                .build();

        messagingTemplate.convertAndSend("/topic/flight/" + flightId, response);
    }
}