package com.despescar.reservationservice.service;

import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.BookingRepository;
import com.despescar.reservationservice.repository.SeatRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeatService {

    /** Bloqueo desde el mapa; al entrar el vuelo al carrito pasa a vencer con el carrito (D9). */
    private static final Duration BLOQUEO = Duration.ofMinutes(15);

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    public List<Seat> fetchSeatByFlight(UUID flightId) {
        List<Seat> seats = seatRepository.findByFlightId(flightId);
        if (seats.isEmpty()) {
            seats = seatRepository.saveAll(generateSeatsInitial(flightId));
        }
        return seats;
    }

    private List<Seat> generateSeatsInitial(UUID flightId) {
        List<Seat> newSeats = new ArrayList<>();
        String[] columns = {"A", "B", "C", "D", "E", "F"};
        int totalRows = 30;
        for (int row = 1; row <= totalRows; row++) {
            for (String column : columns) {
                Seat seat = new Seat();
                seat.setFlightId(flightId);
                seat.setNumberSeat(row + column);
                seat.setStatusSeat(InventarioCarrito.DISPONIBLE);
                newSeats.add(seat);
            }
        }
        return newSeats;
    }

    @Transactional
    public Seat blockedSeat(UUID seatUuid, Long userId) {
        Seat seat = buscar(seatUuid);
        if (!InventarioCarrito.DISPONIBLE.equals(seat.getStatusSeat())) {
            throw new BookingException("ASIENTO_NO_DISPONIBLE",
                    "El asiento " + seat.getNumberSeat() + " ya fue reservado por otra persona.", HttpStatus.CONFLICT);
        }
        seat.setStatusSeat(InventarioCarrito.RESERVADO_TEMPORAL);
        seat.setBlockedByUserId(userId);
        seat.setReservaId(null); // bloqueo desde el mapa: se ata al carrito al entrar el vuelo
        seat.setBloqueadoHasta(LocalDateTime.now(clock).plus(BLOQUEO));
        return seatRepository.save(seat);
    }

    /**
     * Solo se suelta un bloqueo temporal propio: un asiento pagado (OCUPADO) no se libera desde el mapa,
     * y tampoco el de una reserva que se está pagando en grupo (queda congelada hasta que el grupo cierre).
     */
    @Transactional
    public Seat unblockSeat(UUID seatUuid, Long userId) {
        Seat seat = buscar(seatUuid);
        if (userId == null || !userId.equals(seat.getBlockedByUserId())) {
            throw new BookingException("ASIENTO_NO_DISPONIBLE",
                    "No podés liberar un asiento que no es tuyo.", HttpStatus.CONFLICT);
        }
        if (!InventarioCarrito.RESERVADO_TEMPORAL.equals(seat.getStatusSeat())) {
            throw new BookingException("ASIENTO_OCUPADO",
                    "El asiento ya está pagado y no se puede liberar.", HttpStatus.CONFLICT);
        }
        if (seat.getReservaId() != null && bookingRepository.findById(seat.getReservaId())
                .filter(r -> r.getEstado() == ReservationStatus.ESPERANDO_PAGADORES).isPresent()) {
            throw new BookingException("PAGO_EN_GRUPO_EN_CURSO",
                    "No podés cambiar los asientos mientras el grupo está pagando.", HttpStatus.CONFLICT);
        }
        seat.setStatusSeat(InventarioCarrito.DISPONIBLE);
        seat.setBlockedByUserId(null);
        seat.setBloqueadoHasta(null);
        seat.setReservaId(null);
        return seatRepository.save(seat);
    }

    private Seat buscar(UUID seatUuid) {
        return seatRepository.findByIdForUpdate(seatUuid).orElseThrow(() -> new BookingException(
                "ASIENTO_NO_ENCONTRADO", "El asiento no existe en este vuelo.", HttpStatus.NOT_FOUND));
    }
}
