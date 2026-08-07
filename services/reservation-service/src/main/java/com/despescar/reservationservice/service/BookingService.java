package com.despescar.reservationservice.service;

import com.despescar.reservationservice.dto.passengers.request.PessengerRequest;
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


@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {


    private final BookingRepository bookingRepository;

    private final BookingDetailRepository detailRepository;

    private final ExtraBaggageRepository extraBaggageRepository;

    private final SimpMessagingTemplate messagingTemplate;

    private final ReservationMapper reservationMapper;


    @Transactional
    public ReservationResponse crearReserva(CreateReservationRequest dto) {


        if (dto.getAsientos() == null || dto.getAsientos().isEmpty()) {

            throw new BookingException(
                    "SIN_ASIENTOS",
                    "Debe seleccionar al menos un asiento para crear la reserva.",
                    HttpStatus.BAD_REQUEST
            );
        }


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
                        .vueloCodigo(dto.getVueloCodigo())
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
                            .precio(150.00)
                            .estadoPago(
                                    ReservationPaymentState.PENDIENTE
                            )
                            .nombrePasajero(
                                    asientoDto.getNombrePasajero()
                            )
                            .dniPasaporte(
                                    asientoDto.getDniPasaporte()
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
    public void cargarDocumentacion(Long id, PessengerRequest dto) {


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
                    "No se puede modificar la documentación porque la reserva está "
                            + reserva.getEstado(),
                    HttpStatus.BAD_REQUEST
            );
        }


        validarExpiracion(reserva);


        ReservationDetail detalle =
                detailRepository.findByReservaIdAndUsuarioId(
                        id,
                        dto.getUsuarioId()
                );


        if (detalle == null) {

            throw new BookingException(
                    "ASIENTO_NO_ASIGNADO",
                    "El usuario no tiene un asiento asignado en esta reserva.",
                    HttpStatus.BAD_REQUEST
            );
        }


        detalle.setNombrePasajero(
                dto.getNombrePasajero()
        );


        detalle.setDniPasaporte(
                dto.getDniPasaporte()
        );


        detailRepository.save(detalle);


        notificarCambioEnTiempoReal(reserva);
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


