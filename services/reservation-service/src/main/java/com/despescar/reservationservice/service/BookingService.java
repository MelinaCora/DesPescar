package com.despescar.reservationservice.service;

import com.despescar.reservationservice.client.FlightClient;
import com.despescar.reservationservice.client.HotelClient;
import com.despescar.reservationservice.client.PackageClient;
import com.despescar.reservationservice.dto.flight.response.FlightLookupResponse;
import com.despescar.reservationservice.dto.hotel.response.HotelLookupResponse;
import com.despescar.reservationservice.dto.packagecatalog.response.PackageLookupResponse;
import com.despescar.reservationservice.dto.reservation.request.CreateReservationRequest;
import com.despescar.reservationservice.dto.reservation.request.ProcessPaymentRequest;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.ExtraBaggage;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.enums.ReservationPaymentState;
import com.despescar.reservationservice.enums.ReservationState;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.mapper.ReservationMapper;
import com.despescar.reservationservice.repository.BookingDetailRepository;
import com.despescar.reservationservice.repository.BookingRepository;
import com.despescar.reservationservice.repository.ExtraBaggageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private static final Set<String> ESTADOS_VUELO_NO_RESERVABLES = Set.of(
            "CANCELLED",
            "CANCELED",
            "DEPARTED",
            "ARRIVED",
            "LANDED",
            "COMPLETED"
    );


    private final BookingRepository bookingRepository;

    private final BookingDetailRepository detailRepository;

    private final ExtraBaggageRepository extraBaggageRepository;

    private final SimpMessagingTemplate messagingTemplate;

    private final ReservationMapper reservationMapper;

    private final FlightClient flightClient;

    private final HotelClient hotelClient;

    private final PackageClient packageClient;


    @Transactional
    public ReservationResponse crearReserva(CreateReservationRequest dto) {
        return crearReserva(dto, null);
    }

    @Transactional
    public ReservationResponse crearReserva(CreateReservationRequest dto, String authorizationHeader) {

        if (dto.getAsientos() == null || dto.getAsientos().isEmpty()) {

            throw new BookingException(
                    "SIN_ASIENTOS",
                    "Debe seleccionar al menos un asiento para crear la reserva.",
                    HttpStatus.BAD_REQUEST
            );
        }

        PackageLookupResponse paqueteSeleccionado = null;

        if (dto.getPackageId() != null) {
            if (authorizationHeader == null || authorizationHeader.isBlank()) {
                throw new BookingException(
                        "AUTORIZACION_REQUERIDA",
                        "Debe enviar un token valido para seleccionar un paquete.",
                        HttpStatus.UNAUTHORIZED
                );
            }

            paqueteSeleccionado = packageClient.getPackageById(dto.getPackageId(), authorizationHeader);
            validarPaqueteSeleccionado(paqueteSeleccionado, dto.getPackageId());

            if (dto.getVueloCodigo() == null || dto.getVueloCodigo().isBlank()) {
                dto.setVueloCodigo(paqueteSeleccionado.getFlightNumber());
            } else if (!dto.getVueloCodigo().equals(paqueteSeleccionado.getFlightNumber())) {
                throw new BookingException(
                        "PAQUETE_INCONSISTENTE",
                        "El vuelo enviado no coincide con el paquete seleccionado.",
                        HttpStatus.BAD_REQUEST
                );
            }

            if (dto.getHotelId() == null) {
                dto.setHotelId(paqueteSeleccionado.getHotelId());
            } else if (!dto.getHotelId().equals(paqueteSeleccionado.getHotelId())) {
                throw new BookingException(
                        "PAQUETE_INCONSISTENTE",
                        "El hotel enviado no coincide con el paquete seleccionado.",
                        HttpStatus.BAD_REQUEST
                );
            }
        }

        if (dto.getVueloCodigo() == null || dto.getVueloCodigo().isBlank()) {

            throw new BookingException(
                    "VUELO_CODIGO_INVALIDO",
                    "Debe informar un codigo de vuelo valido.",
                    HttpStatus.BAD_REQUEST
            );
        }


        FlightLookupResponse vuelo =
                flightClient.getFlightByNumber(dto.getVueloCodigo());

        validarEstadoVueloParaReserva(vuelo.getStatus(), dto.getVueloCodigo());

        if (dto.getHotelId() != null) {
            validarHotelExistente(dto.getHotelId());
        }


        Double precioAsiento = obtenerPrecioAsiento(vuelo, dto.getVueloCodigo());


        List<Reservation> reservasActivas =
                bookingRepository.findByEstado(
                        ReservationState.PENDIENTE
                );


        boolean carritoActivo =
                reservasActivas.stream()
                        .anyMatch(reserva ->
                                reserva.getCreadorId()
                                        .equals(dto.getCreadorId())
                        );


        if (carritoActivo) {

            throw new BookingException(
                    "CARRITO_DUPLICADO",
                    "Ya tienes un carrito compartido activo.",
                    HttpStatus.BAD_REQUEST
            );
        }


        long cantidadAsientosUnicos =
                dto.getAsientos()
                        .stream()
                        .map(CreateReservationRequest.AsientoSeleccionadoDTO::getNumeroAsiento)
                        .distinct()
                        .count();


        if (cantidadAsientosUnicos < dto.getAsientos().size()) {

            throw new BookingException(
                    "PETICION_INVALIDA",
                    "No puedes seleccionar el mismo asiento más de una vez.",
                    HttpStatus.BAD_REQUEST
            );
        }


        for (CreateReservationRequest.AsientoSeleccionadoDTO asientoDto : dto.getAsientos()) {


            boolean asientoOcupado =
                    bookingRepository.findByEstado(ReservationState.PENDIENTE)
                            .stream()
                            .filter(r ->
                                    r.getVueloCodigo()
                                            .equals(dto.getVueloCodigo())
                            )
                            .flatMap(r ->
                                    r.getDetalles().stream()
                            )
                            .anyMatch(detalle ->
                                    detalle.getNumeroAsiento()
                                            .equals(asientoDto.getNumeroAsiento())
                            );


            if (asientoOcupado) {

                throw new BookingException(
                        "ASIENTO_OCUPADO",
                        "El asiento "
                                + asientoDto.getNumeroAsiento()
                                + " ya está reservado.",
                        HttpStatus.CONFLICT
                );
            }
        }


        Reservation reserva =
                Reservation.builder()
                        .creadorId(dto.getCreadorId())
                        .packageId(dto.getPackageId())
                        .vueloCodigo(dto.getVueloCodigo())
                        .hotelId(dto.getHotelId())
                        .estado(ReservationState.PENDIENTE)
                        .limiteTiempo(
                                LocalDateTime.now()
                                        .plusMinutes(15)
                        )
                        .build();


        reserva = bookingRepository.save(reserva);


        List<ReservationDetail> detalles = new ArrayList<>();


        for (CreateReservationRequest.AsientoSeleccionadoDTO asientoDto : dto.getAsientos()) {


            ReservationDetail detalle =
                    ReservationDetail.builder()
                            .reserva(reserva)
                            .usuarioId(asientoDto.getUsuarioId())
                            .pagadorId(asientoDto.getPagadorId())
                            .numeroAsiento(asientoDto.getNumeroAsiento())
                            .precio(precioAsiento)
                            .estadoPago(
                                    ReservationPaymentState.PENDIENTE
                            )
                            .equipajes(new ArrayList<>())
                            .build();


            ReservationDetail detalleGuardado =
                    detailRepository.save(detalle);


            if (asientoDto.getEquipajes() != null) {


                asientoDto.getEquipajes()
                        .forEach(equipajeDto -> {


                            ExtraBaggage equipaje =
                                    ExtraBaggage.builder()
                                            .detalleReserva(detalleGuardado)
                                            .peso(equipajeDto.getPeso())
                                            .precio(
                                                    calcularPrecioEquipaje(
                                                            equipajeDto.getPeso()
                                                    )
                                            )
                                            .build();


                            detalleGuardado.agregarEquipaje(equipaje);

                            extraBaggageRepository.save(equipaje);

                        });
            }


            detalles.add(detalleGuardado);
        }


        reserva.setDetalles(detalles);


        return reservationMapper.toResponse(reserva);
    }


    private BigDecimal calcularPrecioEquipaje(Double peso) {


        return BigDecimal.valueOf(5)
                .multiply(BigDecimal.valueOf(peso));
    }

    private void validarEstadoVueloParaReserva(String estadoVuelo, String vueloCodigo) {
        if (estadoVuelo == null || estadoVuelo.isBlank()) {
            throw new BookingException(
                    "ESTADO_VUELO_INVALIDO",
                    "No fue posible validar el estado del vuelo " + vueloCodigo + ".",
                    HttpStatus.BAD_GATEWAY
            );
        }

        String estadoNormalizado = estadoVuelo.trim().toUpperCase(Locale.ROOT);
        if (ESTADOS_VUELO_NO_RESERVABLES.contains(estadoNormalizado)) {
            throw new BookingException(
                    "VUELO_NO_RESERVABLE",
                    "El vuelo " + vueloCodigo + " no admite reservas por su estado actual: " + estadoVuelo + ".",
                    HttpStatus.CONFLICT
            );
        }
    }

    private Double obtenerPrecioAsiento(FlightLookupResponse vuelo, String vueloCodigo) {
        if (vuelo.getPrice() == null || vuelo.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BookingException(
                    "PRECIO_VUELO_INVALIDO",
                    "Flight-Service devolvio un precio invalido para el vuelo " + vueloCodigo + ".",
                    HttpStatus.BAD_GATEWAY
            );
        }

        return vuelo.getPrice().doubleValue();
    }

    private void validarHotelExistente(UUID hotelId) {
        HotelLookupResponse hotel = hotelClient.getHotelById(hotelId);
        if (hotel.getId() == null) {
            throw new BookingException(
                    "HOTEL_SERVICE_EMPTY_RESPONSE",
                    "Hotel-Service devolvio una respuesta incompleta para el hotel " + hotelId + ".",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private void validarPaqueteSeleccionado(PackageLookupResponse paquete, Long packageId) {
        if (paquete.getId() == null) {
            throw new BookingException(
                    "PACKAGE_SERVICE_EMPTY_RESPONSE",
                    "Package-Service devolvio una respuesta incompleta para el paquete " + packageId + ".",
                    HttpStatus.BAD_GATEWAY
            );
        }

        if (!paquete.isActive()) {
            throw new BookingException(
                    "PAQUETE_INACTIVO",
                    "El paquete " + packageId + " no se encuentra activo.",
                    HttpStatus.CONFLICT
            );
        }

        if (paquete.getFlightNumber() == null || paquete.getFlightNumber().isBlank()) {
            throw new BookingException(
                    "PAQUETE_SIN_VUELO",
                    "El paquete " + packageId + " no tiene vuelo asociado.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (paquete.getHotelId() == null) {
            throw new BookingException(
                    "PAQUETE_SIN_HOTEL",
                    "El paquete " + packageId + " no tiene hotel asociado.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    public ReservationResponse obtenerReserva(Long id) {


        Reservation reserva =
                bookingRepository.findById(id)
                        .orElseThrow(() ->
                                new BookingException(
                                        "RESERVA_NO_ENCONTRADA",
                                        "La reserva no existe.",
                                        HttpStatus.NOT_FOUND
                                )
                        );


        validarExpiracion(reserva);


        return reservationMapper.toResponse(reserva);
    }

    @Transactional
    public String procesarPago(
            Long id,
            ProcessPaymentRequest dto
    ) {


        Reservation reserva =
                bookingRepository.findById(id)
                        .orElseThrow(() ->
                                new BookingException(
                                        "RESERVA_NO_ENCONTRADA",
                                        "La reserva no existe.",
                                        HttpStatus.NOT_FOUND
                                )
                        );


        if (!ReservationState.PENDIENTE.equals(reserva.getEstado())) {


            throw new BookingException(
                    "MODIFICACION_PROHIBIDA",
                    "No se puede realizar el pago porque la reserva está "
                            + reserva.getEstado(),
                    HttpStatus.BAD_REQUEST
            );
        }


        validarExpiracion(reserva);


        List<ReservationDetail> asientosAPagar =
                detailRepository
                        .findByReservaIdAndPagadorIdAndEstadoPago(
                                id,
                                dto.getPagadorId(),
                                ReservationPaymentState.PENDIENTE
                        );


        if (asientosAPagar.isEmpty()) {


            throw new BookingException(
                    "SIN_DEUDAS",
                    "No tienes pagos pendientes en este carrito.",
                    HttpStatus.BAD_REQUEST
            );
        }


        for (ReservationDetail detalle : asientosAPagar) {


            if (detalle.getNombrePasajero() == null
                    || detalle.getDniPasaporte() == null) {


                throw new BookingException(
                        "DOCUMENTACION_INCOMPLETA",
                        "Falta cargar documentación del asiento "
                                + detalle.getNumeroAsiento(),
                        HttpStatus.BAD_REQUEST
                );
            }
        }


        // Acá luego se reemplaza por la integración real con Payment Service
        boolean pagoExitoso = true;


        if (!pagoExitoso) {

            throw new BookingException(
                    "PAGO_RECHAZADO",
                    "El pago fue rechazado.",
                    HttpStatus.PAYMENT_REQUIRED
            );
        }


        asientosAPagar.forEach(
                detalle ->
                        detalle.setEstadoPago(
                                ReservationPaymentState.PAGADO
                        )
        );


        detailRepository.saveAll(asientosAPagar);


        long pendientes =
                detailRepository.countByReservaIdAndEstadoPago(
                        id,
                        ReservationPaymentState.PENDIENTE
                );


        if (pendientes == 0) {


            reserva.setEstado(
                    ReservationState.COMPLETADA
            );


            bookingRepository.save(reserva);


            notificarCambioEnTiempoReal(reserva);


            return "Reserva completada correctamente. Todos los pagos fueron realizados.";
        }


        notificarCambioEnTiempoReal(reserva);


        return "Pago realizado correctamente. Esperando pagos del resto del grupo.";
    }

    @Transactional
    public void cancelarReservaManualmente(Long id, Long usuarioId) {


        Reservation reserva =
                bookingRepository.findById(id)
                        .orElseThrow(() ->
                                new BookingException(
                                        "RESERVA_NO_ENCONTRADA",
                                        "La reserva no existe.",
                                        HttpStatus.NOT_FOUND
                                )
                        );


        if (!reserva.getCreadorId().equals(usuarioId)) {


            throw new BookingException(
                    "ACCESO_DENEGADO",
                    "Solo el creador del grupo puede cancelar la reserva.",
                    HttpStatus.FORBIDDEN
            );
        }


        if (!ReservationState.PENDIENTE.equals(reserva.getEstado())) {


            throw new BookingException(
                    "ESTADO_INVALIDO",
                    "La reserva no puede cancelarse porque está "
                            + reserva.getEstado(),
                    HttpStatus.BAD_REQUEST
            );
        }


        reserva.setEstado(
                ReservationState.CANCELADA
        );


        bookingRepository.save(reserva);


        List<ReservationDetail> detalles =
                detailRepository.findByReservaId(
                        reserva.getId()
                );


        for (ReservationDetail detalle : detalles) {


            if (ReservationPaymentState.PAGADO
                    .equals(detalle.getEstadoPago())) {


                detalle.setEstadoPago(
                        ReservationPaymentState.REEMBOLSADO
                );


            } else {


                detalle.setEstadoPago(
                        ReservationPaymentState.CANCELADO
                );
            }
        }


        detailRepository.saveAll(detalles);


        log.info(
                "Usuario {} canceló la reserva {}",
                usuarioId,
                id
        );


        notificarCambioEnTiempoReal(reserva);
    }


    private void validarExpiracion(Reservation reserva) {


        if (LocalDateTime.now()
                .isAfter(reserva.getLimiteTiempo())
                &&
                ReservationState.PENDIENTE
                        .equals(reserva.getEstado())) {


            reserva.setEstado(
                    ReservationState.EXPIRADA
            );


            reserva.getDetalles()
                    .forEach(detalle -> {


                        if (ReservationPaymentState.PAGADO
                                .equals(detalle.getEstadoPago())) {


                            detalle.setEstadoPago(
                                    ReservationPaymentState.REEMBOLSADO
                            );


                        } else {


                            detalle.setEstadoPago(
                                    ReservationPaymentState.CANCELADO
                            );
                        }

                    });


            bookingRepository.save(reserva);


            throw new BookingException(
                    "CARRITO_EXPIRADO",
                    "El tiempo límite de 15 minutos terminó.",
                    HttpStatus.GONE
            );
        }
    }


    private void notificarCambioEnTiempoReal(
            Reservation reserva
    ) {


        ReservationResponse response =
                reservationMapper.toResponse(reserva);


        messagingTemplate.convertAndSend(
                "/topic/reserva/" + reserva.getId(),
                response
        );
    }
}

