package com.despescar.reservationservice.service;

import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.repository.SeatRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeatService {

    /** Bloqueo desde el mapa; al entrar el vuelo al carrito pasa a vencer con el carrito (D9). */
    private static final Duration BLOQUEO = Duration.ofMinutes(15);

    private final SeatRepository seatRepository;
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
        Seat seat = seatRepository.findByIdForUpdate(seatUuid)
                .orElseThrow(() -> new RuntimeException("El asiento no existe en este vuelo"));
        if (!InventarioCarrito.DISPONIBLE.equals(seat.getStatusSeat())) {
            throw new RuntimeException("El asiento " + seatUuid + " ya fue reservado por otra persona.");
        }
        seat.setStatusSeat(InventarioCarrito.RESERVADO_TEMPORAL);
        seat.setBlockedByUserId(userId);
        seat.setBloqueadoHasta(LocalDateTime.now(clock).plus(BLOQUEO));
        return seatRepository.save(seat);
    }

    /** Solo se suelta un bloqueo temporal propio: un asiento pagado (OCUPADO) no se libera desde el mapa. */
    @Transactional
    public Seat unblockSeat(UUID seatUuid, Long userId) {
        Seat seat = seatRepository.findByIdForUpdate(seatUuid)
                .orElseThrow(() -> new RuntimeException("El asiento no existe en este vuelo"));
        if (userId == null || !userId.equals(seat.getBlockedByUserId())) {
            throw new RuntimeException("No tienes permisos para liberar un asiento que no te pertenece.");
        }
        if (!InventarioCarrito.RESERVADO_TEMPORAL.equals(seat.getStatusSeat())) {
            throw new RuntimeException("El asiento ya está pagado y no se puede liberar.");
        }
        seat.setStatusSeat(InventarioCarrito.DISPONIBLE);
        seat.setBlockedByUserId(null);
        seat.setBloqueadoHasta(null);
        return seatRepository.save(seat);
    }
}
