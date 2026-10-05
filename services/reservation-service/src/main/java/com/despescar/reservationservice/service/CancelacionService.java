package com.despescar.reservationservice.service;

import com.despescar.reservationservice.client.FlightClient;
import com.despescar.reservationservice.client.PaymentClient;
import com.despescar.reservationservice.dto.pagos.ReembolsoReservaResponse;
import com.despescar.reservationservice.dto.reservation.response.CancelacionResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.BookingRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cancelación de una reserva CONFIRMADA por su dueño, entera (vuelo y estadías juntos). Con la fila
 * de la reserva bloqueada se decide el reembolso ({@link ReembolsoCalculo}), se sueltan los asientos y
 * queda CANCELADA con lo que se devuelve. Después del commit, por HTTP y sin transacción: se liberan
 * las retenciones y el cupo del vuelo (mejor esfuerzo) y se pide el reembolso a payment-service. Si ese
 * pedido falla, la marca reembolsoPendiente lo deja para el reintento programado.
 */
@Service
@Slf4j
public class CancelacionService {

    public static final String MOTIVO = "CANCELADA_POR_USUARIO";

    private final BookingRepository bookingRepository;
    private final CarritoSoporte soporte;
    private final InventarioCarrito inventario;
    private final PaymentClient paymentClient;
    private final FlightClient flightClient;
    private final Clock clock;
    private final TransactionTemplate transaccion;

    public CancelacionService(BookingRepository bookingRepository, CarritoSoporte soporte, InventarioCarrito inventario,
                              PaymentClient paymentClient, FlightClient flightClient, Clock clock,
                              TransactionTemplate transaccion) {
        this.bookingRepository = bookingRepository;
        this.soporte = soporte;
        this.inventario = inventario;
        this.paymentClient = paymentClient;
        this.flightClient = flightClient;
        this.clock = clock;
        this.transaccion = transaccion;
    }

    /** Lo que se devolvería cancelando ahora. Sobre una reserva ya cancelada por el usuario, lo que se devolvió. */
    public CancelacionResponse vistaPrevia(Long id, Long usuarioId) {
        return transaccion.execute(estado -> {
            Reservation reserva = soporte.reservaDelUsuario(id, usuarioId);
            return canceladaPorUsuario(reserva) ? respuesta(reserva, ReembolsoCalculo.guardado(reserva))
                    : respuesta(reserva, calcularCancelable(reserva));
        });
    }

    /** Idempotente: sobre una reserva que el usuario ya canceló devuelve el mismo resultado y no hace nada más. */
    public CancelacionResponse cancelar(Long id, Long usuarioId) {
        Hecho hecho = transaccion.execute(estado -> {
            Reservation reserva = soporte.reservaDelUsuarioBloqueada(id, usuarioId);
            if (canceladaPorUsuario(reserva)) {
                return new Hecho(null, respuesta(reserva, ReembolsoCalculo.guardado(reserva)));
            }
            ReembolsoCalculo.Resultado calculo = calcularCancelable(reserva);
            for (ReembolsoCalculo.Item item : calculo.items()) {
                if (item.estadiaId() == null) {
                    reserva.setMontoReembolsadoVuelo(item.monto());
                } else {
                    estadia(reserva, item.estadiaId()).setMontoReembolsado(item.monto());
                }
            }
            reserva.setMontoReembolsado(calculo.total());
            reserva.setCanceladaEn(LocalDateTime.now(clock));
            reserva.setReembolsoPendiente(calculo.total().signum() > 0);
            inventario.liberarAsientos(reserva);
            reserva.setEstado(ReservationStatus.CANCELADA);
            reserva.setMotivoCancelacion(MOTIVO);
            bookingRepository.save(reserva);
            // respuesta() recorre las estadías: quedan cargadas para liberarlas después del commit
            return new Hecho(reserva, respuesta(reserva, calculo));
        });
        if (hecho.reserva() == null) {
            return hecho.respuesta();
        }
        Reservation reserva = hecho.reserva();
        inventario.liberarRetenciones(reserva);
        devolverCupos(reserva);
        log.info("El usuario {} canceló la reserva {}: se devuelven ${}.", usuarioId, id, reserva.getMontoReembolsado());
        boolean pendiente = reserva.isReembolsoPendiente() && !pedirReembolso(id, reserva.getMontoReembolsado());
        CancelacionResponse r = hecho.respuesta();
        return new CancelacionResponse(r.reservaId(), r.estado(), r.reembolsoTotal(), r.moneda(), r.pagoEnGrupo(),
                pendiente, r.detalle());
    }

    /** Reintenta los reembolsos que no se pudieron pedir al cancelar, hasta que payment-service responda. */
    @Scheduled(fixedDelay = 30000)
    public void pedirReembolsosPendientes() {
        List<Long> ids = transaccion.execute(estado -> bookingRepository.idsConReembolsoPendiente());
        for (Long id : ids == null ? List.<Long>of() : ids) {
            BigDecimal monto = transaccion.execute(estado -> bookingRepository.findById(id)
                    .map(Reservation::getMontoReembolsado).orElse(null));
            if (monto != null) {
                pedirReembolso(id, monto);
            }
        }
    }

    /** @return true si payment-service respondió (la marca queda apagada); false si hay que reintentar. */
    private boolean pedirReembolso(Long id, BigDecimal monto) {
        try {
            ReembolsoReservaResponse hecho = paymentClient.reembolsarReserva(id, monto, MOTIVO);
            transaccion.executeWithoutResult(estado -> bookingRepository.findByIdForUpdate(id).ifPresent(r -> {
                r.setReembolsoPendiente(false);
                bookingRepository.save(r);
            }));
            if (hecho.fallidos() > 0 || hecho.reembolsado() == null || hecho.reembolsado().compareTo(monto) != 0) {
                log.error("Reserva {} cancelada: se reembolsaron ${} de ${} y {} pagos quedaron para hacer a mano.", id,
                        hecho.reembolsado(), monto, hecho.fallidos());
            } else {
                log.info("Reserva {} cancelada: ${} reembolsados.", id, monto);
            }
            return true;
        } catch (RuntimeException ex) {
            log.warn("No se pudo pedir el reembolso de la reserva {}: {}. Se reintenta.", id, ex.getMessage());
            return false;
        }
    }

    private ReembolsoCalculo.Resultado calcularCancelable(Reservation reserva) {
        if (reserva.getEstado() != ReservationStatus.CONFIRMADA) {
            throw new BookingException("RESERVA_NO_CANCELABLE",
                    "Solo se puede cancelar una reserva confirmada.", HttpStatus.CONFLICT);
        }
        ReembolsoCalculo.Resultado calculo = ReembolsoCalculo.calcular(reserva, clock.instant(), clock.getZone());
        if (calculo.empezo()) {
            throw new BookingException("CANCELACION_FUERA_DE_PLAZO",
                    "La reserva ya no se puede cancelar porque el viaje ya empezó.", HttpStatus.CONFLICT);
        }
        return calculo;
    }

    private static boolean canceladaPorUsuario(Reservation reserva) {
        return reserva.getEstado() == ReservationStatus.CANCELADA && MOTIVO.equals(reserva.getMotivoCancelacion());
    }

    private static EstadiaHotel estadia(Reservation reserva, Long estadiaId) {
        return reserva.getEstadias().stream().filter(e -> estadiaId.equals(e.getId())).findFirst().orElseThrow();
    }

    private static CancelacionResponse respuesta(Reservation reserva, ReembolsoCalculo.Resultado calculo) {
        return new CancelacionResponse(reserva.getId(), reserva.getEstado(), calculo.total(), CarritoCalculo.MONEDA,
                reserva.getTipoPago() == PaymentType.SPLIT_PAYMENT, reserva.isReembolsoPendiente(),
                calculo.items().stream().map(i -> new CancelacionResponse.ItemDTO(i.tipo(), i.estadiaId(),
                        i.descripcion(), i.precio(), i.porcentaje(), i.monto())).toList());
    }

    /** Devuelve el cupo informativo de flight-service, como la confirmación lo descontó. Mejor esfuerzo. */
    private void devolverCupos(Reservation reserva) {
        if (!CarritoCalculo.tieneVuelo(reserva)) {
            return;
        }
        for (UUID flightId : reserva.getFlightIds()) {
            try {
                String numero = flightClient.getFlightByNumber(flightId).getFlightNumber();
                flightClient.adjustSeats(numero, reserva.getCantidadPasajeros());
            } catch (RuntimeException ex) {
                log.warn("No se pudo devolver el cupo del vuelo {} de la reserva {}: {}", flightId, reserva.getId(), ex.getMessage());
            }
        }
    }

    private record Hecho(Reservation reserva, CancelacionResponse respuesta) {
    }
}
