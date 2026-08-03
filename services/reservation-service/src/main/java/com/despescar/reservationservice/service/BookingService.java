package com.despescar.reservationservice.service;

import com.despescar.reservationservice.dto.*;
import com.despescar.reservationservice.entity.ReservaDetalleEntity;
import com.despescar.reservationservice.entity.ReservaEntity;
import com.despescar.reservationservice.exception.BookingException; // Tu clase de excepciones
import com.despescar.reservationservice.repository.BookingDetailRepository;
import com.despescar.reservationservice.repository.BookingRepository;
import com.sun.net.httpserver.HttpsServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository detailRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ReservaResponseDTO crearReserva(CrearReservaDTO dto) {

        List<ReservaEntity> reservasActivasDelCreador = bookingRepository.findByEstado("PENDIENTE");
        boolean yaTieneCarritoActivo = reservasActivasDelCreador.stream()
                .anyMatch(r -> r.getCreadorId().equals(dto.getCreadorId()));

        if(yaTieneCarritoActivo) {
            throw new BookingException("CARRITO_DUPLICADO",
                    "Ya tienes un carrito compartido activo. Espera a que expire o completa el pago.",
                    HttpStatus.BAD_REQUEST);
        }

        long asientosUnicosEnPeticion = dto.getAsientos().stream()
                .map(CrearReservaDTO.AsientoSeleccionadoDTO::getNumeroAsiento)
                .distinct()
                .count();

        if(asientosUnicosEnPeticion < dto.getAsientos().size()) {
            throw new BookingException("PETICION_INVALIDA",
                    "No puedes solicitar el mismo asiento más de una vez en la misma reserva.", HttpStatus.BAD_REQUEST);
        }

        for(CrearReservaDTO.AsientoSeleccionadoDTO asientoDto : dto.getAsientos()) {
            boolean asientoYaReservado = bookingRepository.findByEstado("PENDIENTE").stream()
                    .filter(r -> r.getVueloCodigo().equals(dto.getVueloCodigo()))
                    .flatMap(r -> r.getDetalles().stream())
                    .anyMatch(d -> d.getNumeroAsiento().equals(asientoDto.getNumeroAsiento()));

            if (asientoYaReservado) {
                throw new BookingException("ASIENTO_OCUPADO",
                        "El asiento " + asientoDto.getNumeroAsiento() + " ya está reservado temporalmente por otro grupo.",
                        HttpStatus.CONFLICT);
            }
        }

        LocalDateTime limiteTiempo = LocalDateTime.now().plusMinutes(15);

        ReservaEntity reserva = ReservaEntity.builder()
                .creadorId(dto.getCreadorId())
                .vueloCodigo(dto.getVueloCodigo())
                .estado("PENDIENTE")
                .limiteTiempo(limiteTiempo)
                .build();

        reserva = bookingRepository.save(reserva);

        List<ReservaDetalleEntity> detalles = new ArrayList<>();

        for (CrearReservaDTO.AsientoSeleccionadoDTO asientoDto : dto.getAsientos()) {

            Double precioSimulado = 150.00;

            ReservaDetalleEntity detalle = ReservaDetalleEntity.builder()
                    .reserva(reserva)
                    .usuarioId(asientoDto.getUsuarioId())
                    .pagadorId(asientoDto.getPagadorId())
                    .numeroAsiento(asientoDto.getNumeroAsiento())
                    .precio(precioSimulado)
                    .estadoPago("PENDIENTE")
                    .nombrePasajero(null)
                    .dniPasaporte(null)
                    .build();

            detalles.add(detailRepository.save(detalle));
        }

        reserva.setDetalles(detalles);
        return mapearAResponseDTO(reserva);
    }

    public ReservaResponseDTO obtenerReserva(Long id) {
        ReservaEntity reserva = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));

        validarExpiracion(reserva);

        return mapearAResponseDTO(reserva);
    }

    @Transactional
    public void cargarDocumentacion(Long id, CargarPasajeroDTO dto) {
        ReservaEntity reserva = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));

        if(!"PENDIENTE".equals(reserva.getEstado())) {
            throw new BookingException("MODIFICACION_PROHIBIDA",
                    "No se puede modificar la documentación porque esta reserva ya está " +  reserva.getEstado(),
                    HttpStatus.BAD_REQUEST);
        }

        validarExpiracion(reserva);

        ReservaDetalleEntity detalle = detailRepository.findByReservaIdAndUsuarioId(id, dto.getUsuarioId());
        if(detalle == null) {
            throw new BookingException("ASIENTO_NO_ASIGNADO", "El usuario no tiene un asiento en esta reserva.", HttpStatus.BAD_REQUEST);
        }

        detalle.setNombrePasajero(dto.getNombrePasajero());
        detalle.setDniPasaporte(dto.getDniPasaporte());
        detailRepository.save(detalle);

        notificarCambioEnTiempoReal(reserva);
    }

    @Transactional
    public String procesarPago(Long id, ProcesarPagoDTO dto) {
        ReservaEntity reserva = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));

        validarExpiracion(reserva);

        List<ReservaDetalleEntity> asientosAPagar = detailRepository.findByReservaIdAndPagadorIdAndEstadoPago(id, dto.getPagadorId(), "PENDIENTE");
        if(asientosAPagar.isEmpty()) {
            throw new BookingException("SIN_DEUDAS", "No tienes pagos pendientes en este carrito.", HttpStatus.BAD_REQUEST);
        }

        for (ReservaDetalleEntity asiento : asientosAPagar) {
            if(asiento.getNombrePasajero() == null || asiento.getDniPasaporte() == null) {
                throw new BookingException("DOCUMENTACION_INCOMPLETA", "Debes cargar los datos de pasajero antes de pagar el asiento " + asiento.getNumeroAsiento(), HttpStatus.BAD_REQUEST);
            }
        }

        double montoTotal = asientosAPagar.stream().mapToDouble(ReservaDetalleEntity::getPrecio).sum();

        boolean pagoExitoso = true;
        if(!pagoExitoso) {
            throw new BookingException("PAGO_RECHAZADO", "La tarjeta no tiene fondos o fue rechazada.", HttpStatus.PAYMENT_REQUIRED);
        }

        asientosAPagar.forEach(asiento -> asiento.setEstadoPago("PAGADO"));
        detailRepository.saveAll(asientosAPagar);

        long pendientesTotales = detailRepository.countByReservaIdAndEstadoPago(id, "PENDIENTE");
        if(pendientesTotales == 0) {
            reserva.setEstado("COMPLETADA");
            bookingRepository.save(reserva);

            notificarCambioEnTiempoReal(reserva);
            return "Reseva Completada! Todos los pagos listos, boletos emitidos.";
        }

        notificarCambioEnTiempoReal(reserva);
        return "Pago parcial procesado correctamente. Esperando al resto del grupo.";
    }

    @Transactional
    public void cancelarReservaManualmente(Long id, Long usuarioId) {
        ReservaEntity reserva = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));

        if(!reserva.getCreadorId().equals(usuarioId)) {
            throw new BookingException("ACCESO_DENEGADO", "Solo el creado del grupo puede cancelar este carrito.", HttpStatus.FORBIDDEN);
        }

        if(!"PENDIENTE".equals(reserva.getEstado())) {
            throw new BookingException("ESTADO_INVALIDO", "Esta reserva ya no se puede cancelar porque está " + reserva.getEstado(), HttpStatus.BAD_REQUEST);
        }

        reserva.setEstado("EXPIRADA");
        bookingRepository.save(reserva);

        log.info("El creador ID {} ha cancelado manualmente el carrito ID {}. Asientos liberados.", usuarioId, id);

        notificarCambioEnTiempoReal(reserva);
    }

    private void validarExpiracion(ReservaEntity reserva) {
        if(LocalDateTime.now().isAfter(reserva.getLimiteTiempo()) && "PENDIENTE".equals(reserva.getEstado())) {
            reserva.setEstado("EXPIRADA");
            bookingRepository.save(reserva);
            throw new BookingException("CARRITO_EXPIRADO", "El tiempo límite de 15 minutos se termino.", HttpStatus.GONE);
        }
    }

    private void notificarCambioEnTiempoReal(ReservaEntity reserva) {
        ReservaResponseDTO response = mapearAResponseDTO(reserva);

        messagingTemplate.convertAndSend("/topic/reserva/" + reserva.getId(), response);
    }

    private ReservaResponseDTO mapearAResponseDTO(ReservaEntity reserva) {
        long segundos = Duration.between(LocalDateTime.now(), reserva.getLimiteTiempo()).toSeconds();

        List<ReservaResponseDTO.AsientoDetalleDTO> asientosDto = reserva.getDetalles().stream()
                .map(d -> ReservaResponseDTO.AsientoDetalleDTO.builder()
                        .numeroAsiento(d.getNumeroAsiento())
                        .usuarioId(d.getUsuarioId())
                        .pagadorId(d.getPagadorId())
                        .precio(d.getPrecio())
                        .estadoPago(d.getEstadoPago())
                        .nombrePasajero(d.getNombrePasajero())
                        .dniPasaporte(d.getDniPasaporte())
                        .build())
                .collect(Collectors.toList());

        return ReservaResponseDTO.builder()
                .idCarrito(reserva.getId())
                .vueloCodigo(reserva.getVueloCodigo())
                .estadoGeneral(reserva.getEstado())
                .segundosRestantes(Math.max(0, segundos))
                .asientos(asientosDto)
                .build();
    }
}
