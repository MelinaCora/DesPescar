package com.despescar.reservationservice.service;

import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatService {
    private final SeatRepository seatRepository;

    public List<Seat> fetchSeatByFlight(UUID flightId) {
        List<Seat> seats = seatRepository.findByFlightId(flightId);

        if(seats.isEmpty()) {
            seats = generateSeatsInitial(flightId);

            seats = seatRepository.saveAll(seats);
        }

        return seats;
    }

    private List<Seat> generateSeatsInitial(UUID flightId) {
        List<Seat> newSeats = new ArrayList<>();
        String[] columns = {"A", "B", "C", "D", "E", "F"};
        int totalRows = 30;

        for (int row = 1; row <= totalRows; row++) {
            for(String column : columns) {
                Seat seat = new Seat();
                seat.setFlightId(flightId);
                seat.setNumberSeat(row + column);
                seat.setStatusSeat("DISPONIBLE");
                newSeats.add(seat);
            }
        }

        return newSeats;
    }

    @Transactional
    public Seat blockedSeat(UUID seatUuid, Long userId) {
        Seat seat = seatRepository.findByIdForUpdate(seatUuid)
                .orElseThrow(() -> new RuntimeException("El asiento no existe en este vuelo"));

        if(!seat.getStatusSeat().equals("DISPONIBLE")) {
            throw new RuntimeException("El asiento " + seatUuid + " ya fue reservado por otra persona.");
        }

        seat.setStatusSeat("RESERVADO_TEMPORAL");

        seat.setBlockedByUserId(userId);
        seat.setBloqueadoHasta(LocalDateTime.now().plusMinutes(15));

        return seatRepository.save(seat);
    }

    @Transactional
    public Seat unblockSeat(UUID seatUuid, Long userId) {
        Seat seat = seatRepository.findByIdForUpdate(seatUuid)
                .orElseThrow(() -> new RuntimeException("El asiento no existe en este vuelo"));

        if (userId != null && userId.equals(seat.getBlockedByUserId())) {
            seat.setStatusSeat("DISPONIBLE");
            seat.setBlockedByUserId(null);
            seat.setBloqueadoHasta(null);

            return seatRepository.save(seat);
        } else {
            throw new RuntimeException("No tienes permisos para liberar un asiento que no te pertenece.");
        }
    }
}
