package com.despescar.reservationservice.controller;

import com.despescar.reservationservice.dto.reservation.request.SeatMessageRequest;
import com.despescar.reservationservice.dto.reservation.response.SeatResponse;
import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class BookingWebSocketController {

    private final SeatService seatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/select-seat/{flightId}")
    public void processSeatSelection(@DestinationVariable UUID flightId, SeatMessageRequest request) {
        System.out.println("📥 [WebSocket] Recibida selección para vuelo: " + flightId + " asiento ID: " + request.getSeatUuid());

        // Ahora el servicio bloquea usando el ID del asiento directamente
        Seat seatUpdated = seatService.blockedSeat(
                request.getSeatUuid(),
                request.getUserId()
        );

        // La respuesta DEBE devolver el ID para que el frontend lo procese sin problemas
        SeatResponse response = SeatResponse.builder()
                .seatNumber(seatUpdated.getSeatUuid().toString()) // Recomiendo devolver el UUID en este campo o crear un campo 'seatId'
                .seatStatus(seatUpdated.getStatusSeat())
                .blockedByUserId(request.getUserId())
                .build();

        String destination = "/topic/flight/" + flightId;
        System.out.println("📡 [WebSocket] Haciendo broadcast a: " + destination);

        messagingTemplate.convertAndSend(destination, response);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errores")
    public String manejarExcepcion(RuntimeException ex) {
        System.err.println("❌ [WebSocket Error] " + ex.getMessage());
        return ex.getMessage();
    }

    @MessageMapping("/deselect-seat/{flightId}")
    public void processSeatDeselection(@DestinationVariable UUID flightId, SeatMessageRequest request) {

        System.out.println("🔥 BACKEND RECIBIÓ DESELECCIÓN: Asiento ID=" + request.getSeatUuid() + ", Usuario=" + request.getUserId());

        // Desbloquear usando el ID único
        Seat seatUpdated = seatService.unblockSeat(
                request.getSeatUuid(),
                request.getUserId()
        );

        SeatResponse response = SeatResponse.builder()
                .seatNumber(seatUpdated.getSeatUuid().toString()) // Mismo caso: devuelve el UUID
                .seatStatus("DISPONIBLE")
                .blockedByUserId(null)
                .build();

        messagingTemplate.convertAndSend("/topic/flight/" + flightId, response);
    }
}