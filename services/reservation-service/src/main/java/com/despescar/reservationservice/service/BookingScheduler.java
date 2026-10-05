package com.despescar.reservationservice.service;

import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.mapper.ReservationMapper;
import com.despescar.reservationservice.repository.BookingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cierra los carritos vencidos como EXPIRADA (D13), con la hora de Argentina. Cada carrito en su
 * propia transacción corta, con la fila bloqueada (para no pisar una confirmación de pago en curso)
 * y cambiando el estado por la entidad (así @PreUpdate libera carrito_abierto_de); ahí se liberan los
 * asientos. Las retenciones de hotel se liberan después del commit (llamadas HTTP, mejor esfuerzo,
 * D31). Los pasajeros quedan PENDIENTE: el total no cambia y un pago tardío todavía puede
 * confirmarse (D6).
 */
@Component
@Slf4j
public class BookingScheduler {

    static final List<ReservationStatus> ABIERTOS = List.of(
            ReservationStatus.INICIADA, ReservationStatus.PENDIENTE_PAGO, ReservationStatus.ESPERANDO_PAGADORES);

    private final BookingRepository bookingRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ReservationMapper reservationMapper;
    private final InventarioCarrito inventario;
    private final Clock clock;
    private final TransactionTemplate transaccion;

    public BookingScheduler(BookingRepository bookingRepository, SimpMessagingTemplate messagingTemplate,
                            ReservationMapper reservationMapper, InventarioCarrito inventario, Clock clock,
                            TransactionTemplate transaccion) {
        this.bookingRepository = bookingRepository;
        this.messagingTemplate = messagingTemplate;
        this.reservationMapper = reservationMapper;
        this.inventario = inventario;
        this.clock = clock;
        this.transaccion = transaccion;
    }

    @Scheduled(fixedRate = 60000)
    public void verificarCarritosExpirados() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        List<Long> vencidos = transaccion.execute(status -> bookingRepository
                .findByEstadoInAndLimiteTiempoBefore(ABIERTOS, ahora).stream().map(Reservation::getId).toList());
        if (vencidos == null) {
            return;
        }
        for (Long id : vencidos) {
            try {
                expirar(id, ahora);
            } catch (RuntimeException ex) {
                log.error("No se pudo expirar la reserva {}", id, ex);
            }
        }
    }

    private void expirar(Long id, LocalDateTime ahora) {
        Expirada expirada = transaccion.execute(status -> {
            Reservation r = bookingRepository.findByIdForUpdate(id).orElse(null);
            // Se relee bloqueada: un pago pudo confirmarla entre la consulta y este punto
            if (r == null || !ABIERTOS.contains(r.getEstado()) || !r.getLimiteTiempo().isBefore(ahora)) {
                return null;
            }
            r.setEstado(ReservationStatus.EXPIRADA);
            inventario.liberarAsientos(r);
            bookingRepository.save(r);
            // El mapper recorre las estadías: quedan cargadas para liberarlas después del commit
            return new Expirada(r, reservationMapper.toResponse(r));
        });
        if (expirada == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSend("/topic/reserva/" + id, expirada.aviso());
        } catch (RuntimeException ex) {
            log.warn("No se pudo avisar el vencimiento de la reserva {}: {}", id, ex.getMessage());
        }
        inventario.liberarRetenciones(expirada.reserva());
        log.warn("La reserva {} venció sin pagarse: queda EXPIRADA y se liberaron asientos y retenciones.", id);
    }

    private record Expirada(Reservation reserva, ReservationResponse aviso) {
    }
}
