package com.despescar.reservationservice.service;

import com.despescar.reservationservice.client.HotelClient;
import com.despescar.reservationservice.dto.hotel.RetencionHotelRequest;
import com.despescar.reservationservice.dto.hotel.RetencionHotelResponse;
import com.despescar.reservationservice.dto.reservation.response.SeatResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.SeatRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Lo que el carrito toma y devuelve: asientos (en este servicio) y retenciones (en hotel-service).
 *
 * <p>Orden de llamada obligatorio al pagar: primero {@link #confirmarEstadias}, fuera de los locks de
 * asientos (es una llamada de red); despues {@link #confirmarAsientos} en una transaccion corta; si los
 * asientos fallan, compensar con {@link #liberarRetenciones}. Los metodos de asientos exigen una
 * transaccion en curso y avisan por WebSocket recien al confirmarse.
 */
@Component
@Slf4j
public class InventarioCarrito {

    static final String DISPONIBLE = "DISPONIBLE";
    static final String RESERVADO_TEMPORAL = "RESERVADO_TEMPORAL";
    static final String OCUPADO = "OCUPADO";
    /** Vencimiento de una retención tomada de nuevo al confirmar un pago tardío. */
    static final Duration RETENCION_DE_RESCATE = Duration.ofMinutes(10);
    private static final Set<String> FALTA_DE_LUGAR = Set.of(
            "SIN_DISPONIBILIDAD_HOTEL", "SOLICITUD_HOTEL_INVALIDA", "HOTEL_NO_ENCONTRADO");

    private final HotelClient hotelClient;
    private final SeatRepository seatRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public InventarioCarrito(HotelClient hotelClient, SeatRepository seatRepository,
                             SimpMessagingTemplate messagingTemplate, Clock clock) {
        this.hotelClient = hotelClient;
        this.seatRepository = seatRepository;
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    /** Las retenciones vencen a la misma hora que el carrito. */
    public Instant vencimiento(Reservation carrito) {
        return carrito.getLimiteTiempo().atZone(clock.getZone()).toInstant();
    }

    /**
     * Los asientos que el creador tiene retenidos en el vuelo de ida vencen con el carrito (D9) y
     * quedan atados a él (reservaId). No toca los que ya son de otro carrito.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void alinearBloqueos(Reservation carrito) {
        if (!CarritoCalculo.tieneVuelo(carrito)) {
            return;
        }
        for (Seat seat : seatRepository.findByFlightId(carrito.getFlightIds().get(0))) {
            if (RESERVADO_TEMPORAL.equals(seat.getStatusSeat()) && Objects.equals(carrito.getCreadorId(), seat.getBlockedByUserId())
                    && (seat.getReservaId() == null || seat.getReservaId().equals(carrito.getId()))) {
                seat.setBloqueadoHasta(carrito.getLimiteTiempo());
                seat.setReservaId(carrito.getId());
                seatRepository.save(seat);
            }
        }
    }

    /**
     * Ocupa los asientos de ida de los pasajeros. Todo o nada: si alguno ya no se puede tomar,
     * no cambia ninguno y devuelve false.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean confirmarAsientos(Reservation reserva) {
        if (!CarritoCalculo.tieneVuelo(reserva) || reserva.getDetalles().isEmpty()) {
            return true;
        }
        UUID vueloIda = reserva.getFlightIds().get(0);
        List<Seat> asientos = new ArrayList<>();
        for (ReservationDetail detalle : detallesOrdenados(reserva)) {
            Optional<Seat> asiento = seatRepository.findByFlightIdAndNumberSeatForUpdate(vueloIda, detalle.getOutboundSeatNumber());
            if (asiento.isEmpty() || !sePuedeOcupar(asiento.get(), reserva)) {
                return false;
            }
            asientos.add(asiento.get());
        }
        for (Seat seat : asientos) {
            seat.setStatusSeat(OCUPADO);
            seat.setBlockedByUserId(reserva.getCreadorId());
            seat.setBloqueadoHasta(null);
            seat.setReservaId(reserva.getId());
            seatRepository.save(seat);
            avisar(seat);
        }
        return true;
    }

    /**
     * Devuelve a DISPONIBLE los asientos de los pasajeros que son de esta reserva. Un asiento pagado
     * (OCUPADO) solo se suelta si lo ocupó esta misma reserva; un bloqueo temporal, si es de esta
     * reserva o del creador sin carrito. Nunca toca los de otro carrito del mismo usuario.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void liberarAsientos(Reservation reserva) {
        if (!CarritoCalculo.tieneVuelo(reserva)) {
            return;
        }
        UUID vueloIda = reserva.getFlightIds().get(0);
        for (ReservationDetail detalle : detallesOrdenados(reserva)) {
            seatRepository.findByFlightIdAndNumberSeatForUpdate(vueloIda, detalle.getOutboundSeatNumber())
                    .filter(s -> Objects.equals(reserva.getCreadorId(), s.getBlockedByUserId()))
                    .filter(s -> !DISPONIBLE.equals(s.getStatusSeat()))
                    .filter(s -> esDeLaReserva(s, reserva)
                            || (s.getReservaId() == null && RESERVADO_TEMPORAL.equals(s.getStatusSeat())))
                    .ifPresent(s -> {
                        s.setStatusSeat(DISPONIBLE);
                        s.setBlockedByUserId(null);
                        s.setBloqueadoHasta(null);
                        s.setReservaId(null);
                        seatRepository.save(s);
                        avisar(s);
                    });
        }
        desatarElegidosSinPasajero(reserva, vueloIda);
    }

    /**
     * Los asientos elegidos en el mapa quedan atados al carrito al entrar el vuelo aunque todavía no
     * tengan pasajero. Cuando el carrito suelta el vuelo siguen bloqueados por el usuario hasta su
     * vencimiento, pero ya sin carrito: si no, el carrito siguiente del usuario no podría usarlos.
     */
    private void desatarElegidosSinPasajero(Reservation reserva, UUID vueloIda) {
        if (reserva.getId() == null) {
            return;
        }
        for (UUID id : seatRepository.findIdsByFlightIdAndReservaId(vueloIda, reserva.getId())) {
            seatRepository.findByIdForUpdate(id)
                    .filter(s -> RESERVADO_TEMPORAL.equals(s.getStatusSeat()) && esDeLaReserva(s, reserva))
                    .ifPresent(s -> {
                        s.setReservaId(null);
                        seatRepository.save(s);
                    });
        }
    }

    /**
     * Confirma las retenciones de las estadías activas con el titular. Si el scheduler ya las
     * había liberado (pago tardío), retiene de nuevo y confirma. Si alguna no tiene lugar devuelve
     * false; los errores de comunicación se propagan. En los dos casos se sueltan solo las retenciones
     * creadas en este intento: las que ya tenía el carrito son suyas (otro intento pudo haberlas
     * confirmado y commiteado) y las libera quien cierre la reserva.
     */
    public boolean confirmarEstadias(Reservation reserva) {
        return confirmarEstadias(reserva, false);
    }

    /**
     * Pago tardío sobre una reserva EXPIRADA: sus retenciones son del scheduler, que las libera después
     * de su commit (quizás todavía no lo hizo). Confirmarlas sería perderlas cuando llegue esa
     * liberación, así que se toma una retención nueva por estadía y se confirma. Mismo contrato que
     * {@link #confirmarEstadias}: sin lugar suelta lo tomado y devuelve false.
     */
    public boolean retenerYConfirmarEstadias(Reservation reserva) {
        return confirmarEstadias(reserva, true);
    }

    private boolean confirmarEstadias(Reservation reserva, boolean renovar) {
        List<UUID> nuevas = new ArrayList<>();
        for (EstadiaHotel estadia : CarritoCalculo.estadiasActivas(reserva)) {
            UUID anterior = estadia.getRetencionId();
            try {
                if (renovar) {
                    retenerYConfirmar(reserva, estadia);
                } else {
                    confirmarUna(reserva, estadia);
                }
                if (!Objects.equals(anterior, estadia.getRetencionId())) {
                    nuevas.add(estadia.getRetencionId());
                }
            } catch (BookingException ex) {
                nuevas.forEach(this::liberarRetencion);
                if (!FALTA_DE_LUGAR.contains(ex.getCodigo())) {
                    throw ex;
                }
                log.warn("La estadía {} de la reserva {} ya no tiene lugar: {}", estadia.getId(), reserva.getId(), ex.getMessage());
                return false;
            }
        }
        return true;
    }

    private void confirmarUna(Reservation reserva, EstadiaHotel estadia) {
        try {
            hotelClient.confirmarRetencion(estadia.getRetencionId(), estadia.getTitularNombre());
        } catch (BookingException ex) {
            if (!"RETENCION_LIBERADA".equals(ex.getCodigo())) {
                throw ex;
            }
            retenerYConfirmar(reserva, estadia);
        }
    }

    /** Toma una retención nueva para la estadía, la confirma con el titular y la deja en la estadía. */
    private void retenerYConfirmar(Reservation reserva, EstadiaHotel estadia) {
        RetencionHotelResponse nueva = hotelClient.crearRetencion(new RetencionHotelRequest(
                reserva.getId(), reserva.getCreadorId(), estadia.getHotelId(), estadia.getTipoHabitacionId(),
                estadia.getCheckIn(), estadia.getCheckOut(), estadia.getCantidadHabitaciones(),
                estadia.getHuespedes(), Instant.now(clock).plus(RETENCION_DE_RESCATE)));
        try {
            hotelClient.confirmarRetencion(nueva.getRetencionId(), estadia.getTitularNombre());
        } catch (RuntimeException e) {
            liberarRetencion(nueva.getRetencionId());
            throw e;
        }
        estadia.setRetencionId(nueva.getRetencionId());
    }

    /** Libera las retenciones de las estadías activas. Mejor esfuerzo (D31). */
    public void liberarRetenciones(Reservation reserva) {
        CarritoCalculo.estadiasActivas(reserva).forEach(e -> liberarRetencion(e.getRetencionId()));
    }

    /** Mejor esfuerzo: si hotel-service no responde, la retención vence sola. */
    public void liberarRetencion(UUID retencionId) {
        try {
            hotelClient.liberarRetencion(retencionId);
        } catch (RuntimeException ex) {
            log.warn("No se pudo liberar la retención {}: {}", retencionId, ex.getMessage());
        }
    }

    /**
     * Pago en grupo (D-b4, D-b5): las retenciones de las estadías activas pasan a vencer en `nuevo`.
     * Son llamadas HTTP: llamar sin transacción abierta. Todo o nada: si una falla, las ya cambiadas
     * vuelven a `anterior` y se propaga el error.
     */
    public void cambiarVencimientoRetenciones(Reservation reserva, Instant nuevo, Instant anterior) {
        List<UUID> cambiadas = new ArrayList<>();
        for (EstadiaHotel estadia : CarritoCalculo.estadiasActivas(reserva)) {
            try {
                hotelClient.cambiarVencimiento(estadia.getRetencionId(), nuevo);
                cambiadas.add(estadia.getRetencionId());
            } catch (RuntimeException ex) {
                cambiadas.forEach(id -> volverAlVencimiento(id, anterior));
                throw ex;
            }
        }
    }

    /** Compensación de {@link #cambiarVencimientoRetenciones}: todas las estadías activas vuelven a `anterior`. */
    public void volverAlVencimiento(Reservation reserva, Instant anterior) {
        CarritoCalculo.estadiasActivas(reserva).forEach(e -> volverAlVencimiento(e.getRetencionId(), anterior));
    }

    /** Mejor esfuerzo. Si `anterior` ya pasó, el carrito venció: la retención se libera. */
    public void volverAlVencimiento(UUID retencionId, Instant anterior) {
        if (!anterior.isAfter(Instant.now(clock))) {
            liberarRetencion(retencionId);
            return;
        }
        try {
            hotelClient.cambiarVencimiento(retencionId, anterior);
        } catch (RuntimeException ex) {
            log.warn("No se pudo devolver la retención {} a su vencimiento anterior: {}", retencionId, ex.getMessage());
        }
    }

    /**
     * Libre, o ya de esta reserva (reintento idempotente), o bloqueado por el creador desde el mapa sin
     * carrito. Esto último no vale para una reserva EXPIRADA (pago tardío): el usuario puede tener ese
     * bloqueo para otra compra.
     */
    private static boolean sePuedeOcupar(Seat seat, Reservation reserva) {
        if (DISPONIBLE.equals(seat.getStatusSeat())) {
            return true;
        }
        boolean delCreador = Objects.equals(reserva.getCreadorId(), seat.getBlockedByUserId());
        if (!delCreador) {
            return false;
        }
        if (esDeLaReserva(seat, reserva)) {
            return RESERVADO_TEMPORAL.equals(seat.getStatusSeat()) || OCUPADO.equals(seat.getStatusSeat());
        }
        return RESERVADO_TEMPORAL.equals(seat.getStatusSeat()) && seat.getReservaId() == null
                && reserva.getEstado() != ReservationStatus.EXPIRADA;
    }

    private static boolean esDeLaReserva(Seat seat, Reservation reserva) {
        return seat.getReservaId() != null && seat.getReservaId().equals(reserva.getId());
    }

    private static List<ReservationDetail> detallesOrdenados(Reservation reserva) {
        List<ReservationDetail> orden = new ArrayList<>(reserva.getDetalles());
        orden.sort(Comparator.comparing(ReservationDetail::getOutboundSeatNumber,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return orden;
    }

    /** Avisa recien cuando la transaccion confirma; sin transaccion activa, al instante. */
    void avisar(Seat seat) {
        SeatResponse aviso = SeatResponse.builder()
                .seatNumber(seat.getNumberSeat())
                .seatUuid(seat.getSeatUuid())
                .seatStatus(seat.getStatusSeat())
                .blockedByUserId(seat.getBlockedByUserId())
                .build();
        String destino = "/topic/flight/" + seat.getFlightId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(destino, aviso);
                }
            });
        } else {
            enviar(destino, aviso);
        }
    }

    // El aviso es informativo: si falla el envío, la operación ya quedó guardada y no se revierte.
    private void enviar(String destino, SeatResponse aviso) {
        try {
            messagingTemplate.convertAndSend(destino, aviso);
        } catch (RuntimeException ex) {
            log.warn("No se pudo avisar el cambio de asiento a {}: {}", destino, ex.getMessage());
        }
    }
}
