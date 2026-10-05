package com.despescar.reservationservice.service;

import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.BookingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Lo que comparten el vuelo, las estadías y los titulares: encontrar el carrito activo, crearlo,
 * verificar dueño y que todavía se pueda modificar. "Ahora" sale del Clock (hora de Argentina, D23).
 */
@Component
public class CarritoSoporte {

    public static final Duration DURACION = Duration.ofMinutes(15);
    public static final List<ReservationStatus> ABIERTOS = List.of(ReservationStatus.INICIADA, ReservationStatus.PENDIENTE_PAGO);

    private final BookingRepository bookingRepository;
    private final Clock clock;

    public CarritoSoporte(BookingRepository bookingRepository, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.clock = clock;
    }

    public LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }

    public boolean vencido(Reservation reserva) {
        return reserva.getLimiteTiempo() == null || !ahora().isBefore(reserva.getLimiteTiempo());
    }

    /** El carrito abierto y sin vencer del usuario. Uno vencido lo cierra el scheduler (D13). */
    public Optional<Reservation> carritoActivo(Long usuarioId) {
        return bookingRepository.findFirstByCreadorIdAndEstadoInOrderByIdDesc(usuarioId, ABIERTOS)
                .filter(r -> !vencido(r));
    }

    /** Crea y guarda un carrito vacío: dura 15 minutos desde ahora. */
    public Reservation crearCarrito(Long usuarioId) {
        return bookingRepository.save(Reservation.builder()
                .creadorId(usuarioId)
                .cantidadPasajeros(0)
                .tipoPago(PaymentType.SINGLE_PAYMENT)
                .estado(ReservationStatus.INICIADA)
                .limiteTiempo(ahora().plus(DURACION))
                .build());
    }

    public Reservation reservaDelUsuario(Long id, Long usuarioId) {
        Reservation reserva = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));
        if (!reserva.getCreadorId().equals(usuarioId)) {
            throw new BookingException("ACCESO_DENEGADO", "No tenés acceso a esta reserva.", HttpStatus.FORBIDDEN);
        }
        return reserva;
    }

    /** Solo se modifica un carrito abierto y dentro de su tiempo. */
    public void verificarModificable(Reservation reserva) {
        if (!ABIERTOS.contains(reserva.getEstado())) {
            throw new BookingException("ESTADO_INVALIDO",
                    "El carrito ya no se puede modificar (estado actual: " + reserva.getEstado() + ").", HttpStatus.CONFLICT);
        }
        if (vencido(reserva)) {
            throw new BookingException("CARRITO_EXPIRADO", "El tiempo límite de 15 minutos terminó.", HttpStatus.GONE);
        }
    }
}
