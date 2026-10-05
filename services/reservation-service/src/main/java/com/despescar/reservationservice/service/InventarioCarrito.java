package com.despescar.reservationservice.service;

import com.despescar.reservationservice.client.HotelClient;
import com.despescar.reservationservice.dto.hotel.RetencionHotelRequest;
import com.despescar.reservationservice.dto.hotel.RetencionHotelResponse;
import com.despescar.reservationservice.dto.reservation.response.SeatResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.entity.Seat;
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

    /** Los asientos que el creador tiene retenidos en el vuelo de ida vencen con el carrito (D9). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void alinearBloqueos(Reservation carrito) {
        if (!CarritoCalculo.tieneVuelo(carrito)) {
            return;
        }
        for (Seat seat : seatRepository.findByFlightId(carrito.getFlightIds().get(0))) {
            if (RESERVADO_TEMPORAL.equals(seat.getStatusSeat()) && Objects.equals(carrito.getCreadorId(), seat.getBlockedByUserId())) {
                seat.setBloqueadoHasta(carrito.getLimiteTiempo());
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
            if (asiento.isEmpty() || !sePuedeOcupar(asiento.get(), reserva.getCreadorId())) {
                return false;
            }
            asientos.add(asiento.get());
        }
        for (Seat seat : asientos) {
            seat.setStatusSeat(OCUPADO);
            seat.setBlockedByUserId(reserva.getCreadorId());
            seat.setBloqueadoHasta(null);
            seatRepository.save(seat);
            avisar(seat);
        }
        return true;
    }

    /** Devuelve a DISPONIBLE los asientos de los pasajeros que tiene tomados el creador. */
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
                    .ifPresent(s -> {
                        s.setStatusSeat(DISPONIBLE);
                        s.setBlockedByUserId(null);
                        s.setBloqueadoHasta(null);
                        seatRepository.save(s);
                        avisar(s);
                    });
        }
    }

    /**
     * Confirma las retenciones de las estadías activas con el titular. Si el scheduler ya las
     * había liberado (pago tardío), retiene de nuevo y confirma. Si alguna no tiene lugar, libera
     * lo confirmado en este intento y devuelve false. Los errores de comunicación se propagan.
     */
    public boolean confirmarEstadias(Reservation reserva) {
        List<UUID> tomadas = new ArrayList<>();
        for (EstadiaHotel estadia : CarritoCalculo.estadiasActivas(reserva)) {
            try {
                confirmarUna(reserva, estadia);
                tomadas.add(estadia.getRetencionId());
            } catch (BookingException ex) {
                if (!FALTA_DE_LUGAR.contains(ex.getCodigo())) {
                    tomadas.forEach(this::liberarRetencion);
                    throw ex;
                }
                log.warn("La estadía {} de la reserva {} ya no tiene lugar: {}", estadia.getId(), reserva.getId(), ex.getMessage());
                tomadas.forEach(this::liberarRetencion);
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

    private static boolean sePuedeOcupar(Seat seat, Long creador) {
        if (DISPONIBLE.equals(seat.getStatusSeat())) {
            return true;
        }
        return (RESERVADO_TEMPORAL.equals(seat.getStatusSeat()) || OCUPADO.equals(seat.getStatusSeat()))
                && Objects.equals(creador, seat.getBlockedByUserId());
    }

    private static List<ReservationDetail> detallesOrdenados(Reservation reserva) {
        List<ReservationDetail> orden = new ArrayList<>(reserva.getDetalles());
        orden.sort(Comparator.comparing(ReservationDetail::getOutboundSeatNumber,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return orden;
    }

    /** Avisa recien cuando la transaccion confirma; sin transaccion activa, al instante. */
    private void avisar(Seat seat) {
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
                    messagingTemplate.convertAndSend(destino, aviso);
                }
            });
        } else {
            messagingTemplate.convertAndSend(destino, aviso);
        }
    }
}
