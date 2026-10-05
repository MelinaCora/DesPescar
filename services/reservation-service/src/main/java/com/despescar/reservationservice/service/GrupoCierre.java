package com.despescar.reservationservice.service;

import com.despescar.reservationservice.entity.GrupoPago;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.BookingRepository;
import com.despescar.reservationservice.repository.GrupoPagoRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cierre de un pago en grupo que no se completó (D-b14, D-b16). Orden de locks: grupo y después
 * reserva. Los asientos se liberan en la transacción; las retenciones, después del commit (HTTP,
 * mejor esfuerzo, D31). Los reembolsos no se piden acá: se marcan y los pide el scheduler (D-b13).
 * También cierra el grupo que se pagó completo y no se pudo confirmar.
 */
@Component
@Slf4j
public class GrupoCierre {

    public static final String MOTIVO_CANCELADO = "GRUPO_CANCELADO";
    public static final String MOTIVO_VENCIDO = "PAGO_EN_GRUPO_VENCIDO";
    public static final String MOTIVO_SIN_CONFIRMAR = "CONFIRMACION_FALLIDA";

    private final GrupoPagoRepository grupoRepository;
    private final BookingRepository bookingRepository;
    private final InventarioCarrito inventario;
    private final CarritoSoporte soporte;
    private final TransactionTemplate transaccion;

    public GrupoCierre(GrupoPagoRepository grupoRepository, BookingRepository bookingRepository,
                       InventarioCarrito inventario, CarritoSoporte soporte, TransactionTemplate transaccion) {
        this.grupoRepository = grupoRepository;
        this.bookingRepository = bookingRepository;
        this.inventario = inventario;
        this.soporte = soporte;
        this.transaccion = transaccion;
    }

    /**
     * Cierra un grupo ABIERTO con ese estado y motivo, y cancela su reserva. Idempotente: si, releído
     * con lock, el grupo ya no está ABIERTO (o, con soloSiVencido, todavía no venció), no hace nada.
     * Tampoco si la reserva bloqueada no está esperando pagadores (por ejemplo CONFIRMADA): queda en el log.
     *
     * @return true si lo cerró en esta llamada.
     */
    public boolean cerrar(Long grupoId, EstadoGrupo estadoFinal, String motivo, boolean soloSiVencido) {
        Reservation cerrada = transaccion.execute(estado -> {
            GrupoPago grupo = grupoRepository.findByIdForUpdate(grupoId).orElse(null);
            if (grupo == null || !grupo.abierto()) {
                return null;
            }
            if (soloSiVencido && soporte.ahora().isBefore(grupo.getVenceEn())) {
                return null;
            }
            Reservation reserva = reservaBloqueada(grupo);
            if (reserva.getEstado() != ReservationStatus.ESPERANDO_PAGADORES) {
                // No debería pasar: un grupo ABIERTO tiene su reserva esperando pagadores. No se cierra a ciegas.
                log.error("El pago en grupo {} está ABIERTO pero su reserva {} está {}: no se cancela ni se cierra el grupo; "
                        + "hay que revisarlo a mano.", grupoId, reserva.getId(), reserva.getEstado());
                return null;
            }
            cancelarReserva(reserva, motivo);
            cerrarGrupo(grupo, estadoFinal, motivo);
            return reserva;
        });
        if (cerrada == null) {
            return false;
        }
        inventario.liberarRetenciones(cerrada);
        log.warn("El pago en grupo {} de la reserva {} se cerró como {} ({}).", grupoId, cerrada.getId(), estadoFinal, motivo);
        return true;
    }

    /**
     * La última parte se pagó pero la reserva no se pudo confirmar (sin lugar): la confirmación de R7
     * ya canceló la reserva y soltó el inventario. Solo queda cerrar el grupo y pedir los reembolsos.
     */
    public void marcarCanceladoTrasConfirmar(Long grupoId, String motivo) {
        transaccion.executeWithoutResult(estado -> grupoRepository.findByIdForUpdate(grupoId)
                .filter(g -> g.getEstado() == EstadoGrupo.COMPLETO)
                .ifPresent(g -> {
                    cerrarGrupo(g, EstadoGrupo.CANCELADO, motivo);
                    log.warn("El pago en grupo {} se pagó completo pero la reserva no se pudo confirmar ({}): se reembolsa.",
                            grupoId, motivo);
                }));
    }

    /** Lo que deja la transacción de {@link #cancelarSinConfirmar}; reserva es null si ya estaba cancelada. */
    private record SinConfirmar(Reservation reserva, Long reservaId, Duration enCompleto) {
    }

    /**
     * Un grupo COMPLETO cuya reserva no se pudo confirmar (venció el plazo de reintentos o el rechazo es
     * definitivo): cancela la reserva, suelta el inventario y cierra el grupo para reembolsar todas las
     * partes. Con el grupo y la reserva bloqueados: si una confirmación en curso ya la confirmó, no hace
     * nada (ese intento cierra el grupo como CONFIRMADO); y una que todavía no terminó ya no puede
     * confirmarla, porque relee la reserva bloqueada y la encuentra CANCELADA.
     *
     * @return true si el grupo quedó cancelado en esta llamada.
     */
    public boolean cancelarSinConfirmar(Long grupoId, String motivo) {
        SinConfirmar hecho = transaccion.execute(estado -> {
            GrupoPago grupo = grupoRepository.findByIdForUpdate(grupoId).orElse(null);
            if (grupo == null || grupo.getEstado() != EstadoGrupo.COMPLETO) {
                return null;
            }
            Reservation reserva = reservaBloqueada(grupo);
            if (reserva.getEstado() == ReservationStatus.CONFIRMADA) {
                return null;
            }
            boolean cancelar = reserva.getEstado() == ReservationStatus.ESPERANDO_PAGADORES;
            if (cancelar) {
                cancelarReserva(reserva, motivo);
            }
            LocalDateTime desde = grupo.getCompletoDesde() != null ? grupo.getCompletoDesde() : grupo.getActualizadoEn();
            Duration enCompleto = desde == null ? Duration.ZERO : Duration.between(desde, soporte.ahora());
            cerrarGrupo(grupo, EstadoGrupo.CANCELADO, motivo);
            return new SinConfirmar(cancelar ? reserva : null, reserva.getId(), enCompleto);
        });
        if (hecho == null) {
            return false;
        }
        if (hecho.reserva() != null) {
            inventario.liberarRetenciones(hecho.reserva());
        }
        log.error("El pago en grupo {} llevaba {} minutos pagado completo sin poder confirmar la reserva {}: se cancela ({}) "
                + "y se reembolsan todas las partes.", grupoId, hecho.enCompleto().toMinutes(), hecho.reservaId(), motivo);
        return true;
    }

    private Reservation reservaBloqueada(GrupoPago grupo) {
        return bookingRepository.findByIdForUpdate(grupo.getReservation().getId())
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));
    }

    /** Dentro de la transacción y con la reserva bloqueada: suelta los asientos y la deja CANCELADA. */
    private void cancelarReserva(Reservation reserva, String motivo) {
        inventario.liberarAsientos(reserva);
        reserva.getDetalles().forEach(d -> d.setPaymentStatus(PaymentStatus.CANCELADO));
        reserva.setEstado(ReservationStatus.CANCELADA);
        reserva.setMotivoCancelacion(motivo);
        bookingRepository.save(reserva);
        CarritoCalculo.estadiasActivas(reserva).size(); // quedan cargadas para liberarlas después del commit
    }

    /**
     * Todo cierre deja la marca de reembolsos, haya o no partes pagadas: con ella payment-service
     * también cancela los pagos de partes que quedaron pendientes.
     */
    private void cerrarGrupo(GrupoPago grupo, EstadoGrupo estadoFinal, String motivo) {
        grupo.setEstado(estadoFinal);
        grupo.setMotivoCierre(motivo);
        grupo.setActualizadoEn(soporte.ahora());
        grupo.setReembolsosPendientes(true);
        grupoRepository.save(grupo);
    }
}
