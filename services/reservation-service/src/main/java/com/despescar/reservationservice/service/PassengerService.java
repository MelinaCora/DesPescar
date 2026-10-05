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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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
        Set<UUID> pedidos = new HashSet<>();
        for (PassengerAssignationRequest.PassengerItemDTO pasajero : pasajeros) {
            if (!pedidos.add(pasajero.getAsientoIda())) {
                throw new BookingException("ASIENTO_INVALIDO", "Cada pasajero necesita un asiento distinto.", HttpStatus.BAD_REQUEST);
            }
        }

        // Todos los asientos que toca la operación (pedidos y a soltar) se bloquean en un único orden
        // (por número, el mismo de InventarioCarrito): dos PUT cruzados no pueden trabarse entre sí.
        Map<UUID, Seat> pedidosPorId = new HashMap<>();
        TreeMap<String, UUID> aBloquear = new TreeMap<>(); // número -> id (null si solo se suelta)
        for (UUID id : pedidos) {
            Seat sinBloqueo = seatRepository.findById(id)
                    .filter(s -> vueloIda.equals(s.getFlightId()))
                    .orElseThrow(() -> new BookingException("ASIENTO_INVALIDO",
                            "El asiento elegido no es de este vuelo.", HttpStatus.BAD_REQUEST));
            aBloquear.put(sinBloqueo.getNumberSeat(), id);
        }
        for (ReservationDetail previo : reserva.getDetalles()) {
            if (previo.getOutboundSeatNumber() != null) {
                aBloquear.putIfAbsent(previo.getOutboundSeatNumber(), null);
            }
        }
        Map<String, Seat> bloqueados = new HashMap<>();
        for (Map.Entry<String, UUID> e : aBloquear.entrySet()) {
            Optional<Seat> seat = e.getValue() != null
                    ? seatRepository.findByIdForUpdate(e.getValue()).filter(s -> vueloIda.equals(s.getFlightId()))
                    : seatRepository.findByFlightIdAndNumberSeatForUpdate(vueloIda, e.getKey());
            if (e.getValue() != null) {
                Seat pedido = seat.orElseThrow(() -> new BookingException("ASIENTO_INVALIDO",
                        "El asiento elegido no es de este vuelo.", HttpStatus.BAD_REQUEST));
                pedidosPorId.put(e.getValue(), pedido);
            }
            seat.ifPresent(s -> bloqueados.put(e.getKey(), s));
        }

        List<ReservationDetail> nuevos = new ArrayList<>();
        for (PassengerAssignationRequest.PassengerItemDTO pasajero : pasajeros) {
            Seat asiento = pedidosPorId.get(pasajero.getAsientoIda());
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
        soltados.forEach(numero -> soltar(bloqueados.get(numero), authenticatedUserId));

        reserva.setEstado(CarritoCalculo.estadoAbierto(reserva));
        bookingRepository.save(reserva);
        inventario.alinearBloqueos(reserva);
    }

    private void soltar(Seat s, Long usuarioId) {
        if (s != null && InventarioCarrito.RESERVADO_TEMPORAL.equals(s.getStatusSeat())
                && usuarioId.equals(s.getBlockedByUserId())) {
            s.setStatusSeat(InventarioCarrito.DISPONIBLE);
            s.setBlockedByUserId(null);
            s.setBloqueadoHasta(null);
            seatRepository.save(s);
            inventario.avisar(s); // el mapa se entera recién al confirmarse la transacción
        }
    }
}
