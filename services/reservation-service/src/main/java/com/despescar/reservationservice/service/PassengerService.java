package com.despescar.reservationservice.service;

import com.despescar.reservationservice.dto.passengers.request.PassengerAssignationRequest;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.BookingRepository;
import com.despescar.reservationservice.repository.SeatRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pasajeros de la parte de vuelo. El precio y la tarifa salen del carrito (calculados con
 * flight-service al entrar el vuelo, D1); lo que mande el cliente se ignora. Un segundo PUT
 * reemplaza los pasajeros: vacía y vuelve a llenar la colección administrada (orphanRemoval).
 */
@Service
@RequiredArgsConstructor
public class PassengerService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final CarritoSoporte soporte;
    private final InventarioCarrito inventario;

    @Transactional
    public void assignPassengersToSeats(Long reservationId, PassengerAssignationRequest request, Long authenticatedUserId) {
        Reservation reserva = soporte.reservaDelUsuario(reservationId, authenticatedUserId);
        soporte.verificarModificable(reserva);
        if (!CarritoCalculo.tieneVuelo(reserva)) {
            throw new BookingException("SIN_VUELO", "El carrito no tiene vuelo.", HttpStatus.BAD_REQUEST);
        }
        List<PassengerAssignationRequest.PassengerItemDTO> pasajeros = request.getPasajeros();
        if (pasajeros == null || pasajeros.size() != reserva.getCantidadPasajeros()) {
            throw new BookingException("CANTIDAD_INVALIDA",
                    "Debés cargar exactamente " + reserva.getCantidadPasajeros() + " pasajeros.", HttpStatus.BAD_REQUEST);
        }

        UUID vueloIda = reserva.getFlightIds().get(0);
        Set<UUID> elegidos = new HashSet<>();
        List<ReservationDetail> nuevos = new ArrayList<>();
        for (PassengerAssignationRequest.PassengerItemDTO pasajero : pasajeros) {
            if (!elegidos.add(pasajero.getAsientoIda())) {
                throw new BookingException("ASIENTO_INVALIDO", "Cada pasajero necesita un asiento distinto.", HttpStatus.BAD_REQUEST);
            }
            Seat asiento = seatRepository.findByIdForUpdate(pasajero.getAsientoIda())
                    .filter(s -> vueloIda.equals(s.getFlightId()))
                    .orElseThrow(() -> new BookingException("ASIENTO_INVALIDO",
                            "El asiento elegido no es de este vuelo.", HttpStatus.BAD_REQUEST));
            if (!InventarioCarrito.RESERVADO_TEMPORAL.equals(asiento.getStatusSeat())
                    || !authenticatedUserId.equals(asiento.getBlockedByUserId())) {
                throw new BookingException("ASIENTO_NO_BLOQUEADO", "El asiento " + asiento.getNumberSeat()
                        + " no está bloqueado por vos. Elegilo primero en el mapa.", HttpStatus.CONFLICT);
            }
            nuevos.add(ReservationDetail.builder()
                    .reservation(reserva)
                    .passengerName(pasajero.getNombreCompleto().trim())
                    .passengerDni(pasajero.getDniPasaporte().trim())
                    .fareId(reserva.getBaggageIds().isEmpty() ? null : reserva.getBaggageIds().get(0))
                    .fareName(reserva.getTarifasVuelo())
                    .priceCharged(reserva.getPrecioVueloPorPasajero())
                    .fareCurrency(CarritoCalculo.MONEDA)
                    .outboundSeatNumber(asiento.getNumberSeat())
                    .returnSeatNumber(null) // D10: solo se elige asiento de ida
                    .payerUserId(reserva.getCreadorId())
                    .paymentStatus(PaymentStatus.PENDIENTE)
                    .build());
        }

        // Reemplazo (no suma): los asientos que dejan de usarse vuelven a estar libres.
        Set<String> numerosNuevos = nuevos.stream().map(ReservationDetail::getOutboundSeatNumber).collect(Collectors.toSet());
        List<String> soltados = reserva.getDetalles().stream()
                .map(ReservationDetail::getOutboundSeatNumber)
                .filter(n -> n != null && !numerosNuevos.contains(n))
                .toList();
        reserva.getDetalles().clear();
        reserva.getDetalles().addAll(nuevos);
        soltados.forEach(numero -> soltar(vueloIda, numero, authenticatedUserId));

        reserva.setEstado(CarritoCalculo.estadoAbierto(reserva));
        bookingRepository.save(reserva);
        inventario.alinearBloqueos(reserva);
    }

    private void soltar(UUID vuelo, String numero, Long usuarioId) {
        seatRepository.findByFlightIdAndNumberSeatForUpdate(vuelo, numero)
                .filter(s -> InventarioCarrito.RESERVADO_TEMPORAL.equals(s.getStatusSeat()))
                .filter(s -> usuarioId.equals(s.getBlockedByUserId()))
                .ifPresent(s -> {
                    s.setStatusSeat(InventarioCarrito.DISPONIBLE);
                    s.setBlockedByUserId(null);
                    s.setBloqueadoHasta(null);
                    seatRepository.save(s);
                });
    }
}
