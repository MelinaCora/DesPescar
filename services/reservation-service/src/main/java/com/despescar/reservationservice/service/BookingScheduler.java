package com.despescar.reservationservice.service;

import com.despescar.reservationservice.client.FlightClient;
import com.despescar.reservationservice.client.HotelClient;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.ReservationPaymentState;
import com.despescar.reservationservice.enums.ReservationState;
import com.despescar.reservationservice.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingScheduler {


    private final BookingRepository bookingRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FlightClient flightClient;
    private final HotelClient hotelClient;


    @Scheduled(fixedRate = 60000)
    @Transactional
    public void verificarCarritosExpirados() {


        List<Reservation> reservasPendientes =
                bookingRepository.findByEstado(ReservationState.PENDIENTE);


        LocalDateTime ahora = LocalDateTime.now();


        for (Reservation reserva : reservasPendientes) {


            if (ahora.isAfter(reserva.getLimiteTiempo())) {


                reserva.setEstado(ReservationState.EXPIRADA);

                int cantidadAsientos = reserva.getDetalles().size();

                reserva.getDetalles().forEach(detalle -> {

                    if (ReservationPaymentState.PAGADO
                            .equals(detalle.getEstadoPago())) {

                        detalle.setEstadoPago(
                                ReservationPaymentState.REEMBOLSADO
                        );

                    } else {

                        detalle.setEstadoPago(
                                ReservationPaymentState.CANCELADO
                        );
                    }
                });


                bookingRepository.save(reserva);

                log.warn(
                        "Cron Job: El carrito ID {} expiró. Liberando inventario.",
                        reserva.getId()
                );

                // Restaurar inventario en los servicios externos
                try {
                    flightClient.adjustSeats(reserva.getVueloCodigo(), cantidadAsientos);
                    if (reserva.getHotelId() != null) {
                        hotelClient.adjustRooms(reserva.getHotelId(), 1);
                    }
                } catch (Exception e) {
                    log.error("No se pudo restaurar inventario para reserva expirada {}.", reserva.getId(), e);
                }

                ReservationResponse responseExpirada =
                        mapearAResponseDTO(reserva);


                messagingTemplate.convertAndSend(
                        "/topic/reserva/" + reserva.getId(),
                        responseExpirada
                );
            }
        }
    }


    private ReservationResponse mapearAResponseDTO(
            Reservation reserva
    ) {


        List<ReservationResponse.AsientoDetalleDTO> asientosDto =
                reserva.getDetalles()
                        .stream()
                        .map(d ->
                                ReservationResponse.AsientoDetalleDTO.builder()
                                        .numeroAsiento(d.getNumeroAsiento())
                                        .usuarioId(d.getUsuarioId())
                                        .pagadorId(d.getPagadorId())
                                        .precio(d.getPrecio())
                                        .estadoPago(d.getEstadoPago())
                                        .nombrePasajero(d.getNombrePasajero())
                                        .dniPasaporte(d.getDniPasaporte())
                                        .build()
                        )
                        .collect(Collectors.toList());


        return ReservationResponse.builder()
                .idCarrito(reserva.getId())
                .vueloCodigo(reserva.getVueloCodigo())
                .estadoGeneral(reserva.getEstado())
                .segundosRestantes(0L)
                .asientos(asientosDto)
                .build();
    }
}