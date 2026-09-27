package com.despescar.reservationservice.service;

import com.despescar.reservationservice.dto.passengers.request.PassengerAssignationRequest;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.BookingRepository;
import com.despescar.reservationservice.repository.BookingDetailRepository;
import com.despescar.reservationservice.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PassengerService {

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository detailRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public void assignPassengersToSeats(Long reservationId, PassengerAssignationRequest request) {

        // 1. Obtener y validar la reserva
        Reservation reserva = bookingRepository.findById(reservationId)
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "Reserva inexistente", HttpStatus.NOT_FOUND));

        if (!reserva.getCreadorId().equals(request.getSolicitanteId())) {
            throw new BookingException("ACCESO_DENEGADO", "Solo el creador puede asignar pasajeros", HttpStatus.FORBIDDEN);
        }

        if (!ReservationStatus.INICIADA.equals(reserva.getEstado())) {
            throw new BookingException("ESTADO_INVALIDO", "La reserva ya superó la fase de asignación", HttpStatus.BAD_REQUEST);
        }

        if (request.getPasajeros().size() != reserva.getCantidadPasajeros()) {
            throw new BookingException("CANTIDAD_INVALIDA",
                    "Debes asignar exactamente " + reserva.getCantidadPasajeros() + " pasajeros.", HttpStatus.BAD_REQUEST);
        }

        List<ReservationDetail> detallesNuevos = new ArrayList<>();

        for (var pasajeroDto : request.getPasajeros()) {

            // A) Buscar físicamente por UUID (Ya no necesitamos el flightId para la búsqueda)
            Seat asientoFisico = seatRepository.findByIdForUpdate(pasajeroDto.getAsientoIda())
                    .orElseThrow(() -> new BookingException("ASIENTO_INVALIDO",
                            "El asiento seleccionado no existe.", HttpStatus.BAD_REQUEST));

            // B) Validación crítica de bloqueo
            if (asientoFisico.getBlockedByUserId() == null || !asientoFisico.getBlockedByUserId().equals(request.getSolicitanteId())) {
                throw new BookingException("ASIENTO_NO_BLOQUEADO",
                        "El asiento " + asientoFisico.getNumberSeat() + " no está bloqueado por ti. Selecciónalo primero en el mapa.", HttpStatus.CONFLICT);
            }

            // C) Crear el detalle del pasajero (El Snapshot)
            ReservationDetail detalle = ReservationDetail.builder()
                    .reservation(reserva)
                    .passengerName(pasajeroDto.getNombreCompleto())
                    .passengerDni(pasajeroDto.getDniPasaporte())
                    .fareId(pasajeroDto.getTarifaId())
                    .fareName(pasajeroDto.getTarifaNombre())
                    .priceCharged(pasajeroDto.getPrecioTarifa())
                    // TRUCO CLAVE: Guardamos el string legible ("12A") extraído de la base de datos
                    .outboundSeatNumber(asientoFisico.getNumberSeat())
                    // Si tuvieras UUID para la vuelta, harías lo mismo buscando asientoVuelta
                    .returnSeatNumber(pasajeroDto.getAsientoVuelta() != null ? pasajeroDto.getAsientoVuelta().toString() : null)
                    .payerUserId(reserva.getTipoPago().name().equals("SINGLE_PAYMENT") ? reserva.getCreadorId() : null)
                    .paymentStatus(PaymentStatus.PENDIENTE)
                    .build();

            detallesNuevos.add(detalle);
        }

        // 3. Guardar los detalles
        detailRepository.saveAll(detallesNuevos);
        reserva.setDetalles(detallesNuevos);

        // 4. Avanzar la máquina de estados
        if (reserva.getTipoPago().name().equals("SINGLE_PAYMENT")) {
            reserva.setEstado(ReservationStatus.PENDIENTE_PAGO);
        } else {
            reserva.setEstado(ReservationStatus.ESPERANDO_PAGADORES);
        }

        bookingRepository.save(reserva);
    }
}