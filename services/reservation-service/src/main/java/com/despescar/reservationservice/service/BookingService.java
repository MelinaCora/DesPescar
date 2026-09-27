package com.despescar.reservationservice.service;

import com.despescar.reservationservice.client.FlightClient;
import com.despescar.reservationservice.client.HotelClient;
import com.despescar.reservationservice.client.PackageClient;
import com.despescar.reservationservice.dto.flight.response.FlightLookupResponse;
import com.despescar.reservationservice.dto.hotel.response.HotelLookupResponse;
import com.despescar.reservationservice.dto.packagecatalog.response.PackageLookupResponse;
import com.despescar.reservationservice.dto.reservation.request.BookingInitRequest;
import com.despescar.reservationservice.dto.reservation.request.ProcessPaymentRequest;
import com.despescar.reservationservice.dto.reservation.request.SplitPaymentSetupRequest;
import com.despescar.reservationservice.dto.reservation.response.BookingInitResponse;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.mapper.ReservationMapper;
import com.despescar.reservationservice.repository.BookingDetailRepository;
import com.despescar.reservationservice.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private static final Set<String> ESTADOS_VUELO_NO_RESERVABLES = Set.of(
            "CANCELLED", "CANCELED", "DEPARTED", "ARRIVED", "LANDED", "COMPLETED"
    );

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository detailRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ReservationMapper reservationMapper;

    // Clientes Feign
    private final FlightClient flightClient;
    private final HotelClient hotelClient;
    private final PackageClient packageClient;


    /**
     * PASO 1: Iniciar la reserva (Crea el cascarón vacío)
     */
    @Transactional
    public BookingInitResponse initializeBooking(BookingInitRequest request, String authorizationHeader) {

        boolean carritoActivo = bookingRepository.findByEstado(ReservationStatus.INICIADA).stream()
                .anyMatch(r -> r.getCreadorId().equals(request.getCreadorId()));

        if (carritoActivo) {
            throw new BookingException("CARRITO_DUPLICADO", "Ya tienes una reserva en proceso.", HttpStatus.BAD_REQUEST);
        }

        if (request.getPackageId() != null) {
            validarYObtenerPaquete(request.getPackageId(), authorizationHeader);
        }

        if (request.getFlightIds() == null || request.getFlightIds().isEmpty()) {
            throw new BookingException("SIN_VUELOS", "Debe proporcionar al menos un vuelo.", HttpStatus.BAD_REQUEST);
        }

        if (request.getBaggageIds() == null || request.getBaggageIds().isEmpty()) {
            throw new BookingException("SIN_TARIFAS", "Debe proporcionar al menos una tarifa.", HttpStatus.BAD_REQUEST);
        }

        for (UUID flightId : request.getFlightIds()) {
            FlightLookupResponse vuelo = flightClient.getFlightByNumber(flightId);
            validarEstadoVueloParaReserva(vuelo.getStatus(), flightId.toString());
            validarAsientosDisponibles(vuelo, request.getCantidadPasajeros());
        }

        if (request.getHotelId() != null) {
            validarYObtenerHotel(request.getHotelId());
        }

        Reservation reserva = Reservation.builder()
                .creadorId(request.getCreadorId())
                .cantidadPasajeros(request.getCantidadPasajeros())
                .tipoPago(request.getPaymentType())
                .flightIds(request.getFlightIds())
                .hotelId(request.getHotelId())
                .packageId(request.getPackageId())
                .baggageIds(request.getBaggageIds())
                .estado(ReservationStatus.INICIADA)
                .limiteTiempo(LocalDateTime.now().plusMinutes(15))
                .build();

        Reservation reservaGuardada = bookingRepository.save(reserva);

        return BookingInitResponse.builder()
                .bookingId(reservaGuardada.getId())
                .status(reservaGuardada.getEstado().name())
                .paymentType(reservaGuardada.getTipoPago())
                .build();
    }

    @Transactional
    public String procesarPago(Long id, ProcessPaymentRequest dto) {

        Reservation reserva = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));

        if (!ReservationStatus.PENDIENTE_PAGO.equals(reserva.getEstado())) {
            throw new BookingException("MODIFICACION_PROHIBIDA", "La reserva no está lista para pago (Estado actual: " + reserva.getEstado() + ")", HttpStatus.BAD_REQUEST);
        }

        validarExpiracion(reserva);

        List<ReservationDetail> detallesAPagar = detailRepository
                .findByReservation_IdAndPayerUserIdAndPaymentStatus(id, dto.getPagadorId(), PaymentStatus.PENDIENTE);

        if (detallesAPagar.isEmpty()) {
            throw new BookingException("SIN_DEUDAS", "No tienes pagos pendientes en este carrito.", HttpStatus.BAD_REQUEST);
        }

        for (ReservationDetail detalle : detallesAPagar) {
            if (detalle.getPassengerName() == null || detalle.getPassengerDni() == null) {
                throw new BookingException("DOCUMENTACION_INCOMPLETA", "Falta documentación del pasajero asignado al asiento " + detalle.getOutboundSeatNumber(), HttpStatus.BAD_REQUEST);
            }
        }

        boolean pagoExitoso = true;
        if (!pagoExitoso) {
            throw new BookingException("PAGO_RECHAZADO", "El pago fue rechazado.", HttpStatus.PAYMENT_REQUIRED);
        }

        detallesAPagar.forEach(detalle -> detalle.setPaymentStatus(PaymentStatus.PAGADO));
        detailRepository.saveAll(detallesAPagar);

        long pendientes = detailRepository.countByReservation_IdAndPaymentStatus(id, PaymentStatus.PENDIENTE);

        if (pendientes == 0) {
            reserva.setEstado(ReservationStatus.CONFIRMADA);

            for (UUID flightId : reserva.getFlightIds()) {
                flightClient.adjustSeats(flightId.toString(), -reserva.getCantidadPasajeros());
            }
            if (reserva.getHotelId() != null) {
                hotelClient.adjustRooms(reserva.getHotelId(), -1);
            }

            bookingRepository.save(reserva);
            notificarCambioEnTiempoReal(reserva);
            return "Reserva confirmada. Todos los pagos fueron realizados.";
        }

        notificarCambioEnTiempoReal(reserva);
        return "Pago realizado correctamente. Esperando pagos del resto del grupo.";
    }

    public ReservationResponse obtenerReserva(Long id) {
        Reservation reserva = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));
        validarExpiracion(reserva);
        return reservationMapper.toResponse(reserva);
    }

    @Transactional
    public void cancelarReservaManualmente(Long id, Long usuarioId) {
        Reservation reserva = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));

        if (!reserva.getCreadorId().equals(usuarioId)) {
            throw new BookingException("ACCESO_DENEGADO", "Solo el creador puede cancelar la reserva.", HttpStatus.FORBIDDEN);
        }

        reserva.setEstado(ReservationStatus.CANCELADA);
        bookingRepository.save(reserva);

        List<ReservationDetail> detalles = detailRepository.findByReservation_Id(id);
        for (ReservationDetail detalle : detalles) {
            if (PaymentStatus.PAGADO.equals(detalle.getPaymentStatus())) {
                detalle.setPaymentStatus(PaymentStatus.REEMBOLSADO);
            } else {
                detalle.setPaymentStatus(PaymentStatus.CANCELADO);
            }
        }
        detailRepository.saveAll(detalles);

        log.info("Usuario {} canceló la reserva {}", usuarioId, id);
        notificarCambioEnTiempoReal(reserva);
    }


    private void validarExpiracion(Reservation reserva) {
        if (LocalDateTime.now().isAfter(reserva.getLimiteTiempo()) &&
                (ReservationStatus.INICIADA.equals(reserva.getEstado()) || ReservationStatus.PENDIENTE_PAGO.equals(reserva.getEstado()))) {

            reserva.setEstado(ReservationStatus.CANCELADA);
            bookingRepository.save(reserva);
            throw new BookingException("CARRITO_EXPIRADO", "El tiempo límite de 15 minutos terminó.", HttpStatus.GONE);
        }
    }

    private void validarEstadoVueloParaReserva(String estadoVuelo, String vueloCodigo) {
        if (estadoVuelo == null || estadoVuelo.isBlank()) {
            throw new BookingException("ESTADO_VUELO_INVALIDO", "Estado de vuelo inválido.", HttpStatus.BAD_GATEWAY);
        }
        String estadoNormalizado = estadoVuelo.trim().toUpperCase(Locale.ROOT);
        if (ESTADOS_VUELO_NO_RESERVABLES.contains(estadoNormalizado)) {
            throw new BookingException("VUELO_NO_RESERVABLE", "El vuelo no admite reservas.", HttpStatus.CONFLICT);
        }
    }

    private void validarAsientosDisponibles(FlightLookupResponse vuelo, int cantidadSolicitada) {
        if (vuelo.getAvailableSeats() != null && vuelo.getAvailableSeats() < cantidadSolicitada) {
            throw new BookingException("SIN_DISPONIBILIDAD", "El vuelo no tiene suficientes asientos.", HttpStatus.CONFLICT);
        }
    }

    private HotelLookupResponse validarYObtenerHotel(UUID hotelId) {
        HotelLookupResponse hotel = hotelClient.getHotelById(hotelId);
        if (hotel.getId() == null || hotel.getHabitacionesDisponibles() < 1) {
            throw new BookingException("SIN_DISPONIBILIDAD_HOTEL", "El hotel no tiene disponibilidad.", HttpStatus.CONFLICT);
        }
        return hotel;
    }

    private PackageLookupResponse validarYObtenerPaquete(Long packageId, String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new BookingException("AUTORIZACION_REQUERIDA", "Se requiere token para el paquete.", HttpStatus.UNAUTHORIZED);
        }
        PackageLookupResponse paquete = packageClient.getPackageById(packageId, authHeader);
        if (paquete.getId() == null || !paquete.isActive()) {
            throw new BookingException("PAQUETE_INACTIVO", "El paquete no está activo.", HttpStatus.CONFLICT);
        }
        return paquete;
    }

    private void notificarCambioEnTiempoReal(Reservation reserva) {
        ReservationResponse response = reservationMapper.toResponse(reserva);
        messagingTemplate.convertAndSend("/topic/reserva/" + reserva.getId(), response);
    }

    @Transactional
    public void setupSplitPayment(Long id, SplitPaymentSetupRequest request) {
        Reservation reserva = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));

        if (!reserva.getCreadorId().equals(request.getSolicitanteId())) {
            throw new BookingException("ACCESO_DENEGADO", "Solo el creador puede configurar el pago compartido.", HttpStatus.FORBIDDEN);
        }

        if (!ReservationStatus.ESPERANDO_PAGADORES.equals(reserva.getEstado())) {
            throw new BookingException("ESTADO_INVALIDO", "La reserva no está en fase de configuración de pago.", HttpStatus.BAD_REQUEST);
        }

        List<ReservationDetail> detalles = detailRepository.findByReservation_Id(id);

        for (SplitPaymentSetupRequest.PayerAssignationDTO asignacion : request.getAsignaciones()) {
            // Buscar el detalle correspondiente al asiento
            ReservationDetail detalleAsignado = detalles.stream()
                    .filter(d -> d.getOutboundSeatNumber().equals(asignacion.getAsientoIda()))
                    .findFirst()
                    .orElseThrow(() -> new BookingException("ASIENTO_INVALIDO", "El asiento " + asignacion.getAsientoIda() + " no pertenece a esta reserva.", HttpStatus.BAD_REQUEST));

            // Asignar el pagador
            detalleAsignado.setPayerUserId(asignacion.getPagadorId());
            detalleAsignado.setPayerEmail(asignacion.getPagadorEmail());
        }

        detailRepository.saveAll(detalles);

        // Avanzar el estado para habilitar los pagos
        reserva.setEstado(ReservationStatus.PENDIENTE_PAGO);
        bookingRepository.save(reserva);
    }
}