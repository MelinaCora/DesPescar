package com.despescar.reservationservice.service;

import com.despescar.reservationservice.dto.ReservaResponseDTO;
import com.despescar.reservationservice.entity.ReservaEntity;
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

    @Scheduled(fixedRate = 1000)
    @Transactional
    public void verificarCarritosExpirados() {
        List<ReservaEntity> reservasPendientes = bookingRepository.findByEstado("PENDIENTE");
        LocalDateTime ahora = LocalDateTime.now();

        for (ReservaEntity reserva :  reservasPendientes) {

            if(ahora.isAfter(reserva.getLimiteTiempo())) {
                reserva.setEstado("EXPIRADA");
                bookingRepository.save(reserva);

                log.warn("Cron Job: El carrito ID {} ha expirado tras 15 minutos. Liberando inventario.", reserva.getId());

                ReservaResponseDTO responseExpirada = mapearAResponseDTO(reserva);
                messagingTemplate.convertAndSend("/topic/reserva/" + reserva.getId(), responseExpirada);
            }
        }
    }

    private ReservaResponseDTO mapearAResponseDTO(ReservaEntity reserva) {
        List<ReservaResponseDTO.AsientoDetalleDTO> asientosDto = reserva.getDetalles().stream()
                .map(d -> ReservaResponseDTO.AsientoDetalleDTO.builder()
                        .numeroAsiento(d.getNumeroAsiento())
                        .usuarioId(d.getUsuarioId())
                                .pagadorId(d.getPagadorId())
                                .precio(d.getPrecio())
                                .estadoPago(d.getEstadoPago())
                                .nombrePasajero(d.getNombrePasajero())
                                .dniPasaporte(d.getDniPasaporte())
                                .build())
                        .collect(Collectors.toList());

        return ReservaResponseDTO.builder()
                .idCarrito(reserva.getId())
                .vueloCodigo(reserva.getVueloCodigo())
                .estadoGeneral(reserva.getEstado())
                .segundosRestantes(0L)
                .asientos(asientosDto)
                .build();
    }

}
