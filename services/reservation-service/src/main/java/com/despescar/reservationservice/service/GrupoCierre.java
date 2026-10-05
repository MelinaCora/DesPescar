package com.despescar.reservationservice.service;

import com.despescar.reservationservice.entity.GrupoPago;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.BookingRepository;
import com.despescar.reservationservice.repository.GrupoPagoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cierre de un pago en grupo que no se completó (D-b14, D-b16). Orden de locks: grupo y después
 * reserva. Los asientos se liberan en la transacción; las retenciones, después del commit (HTTP,
 * mejor esfuerzo, D31). Los reembolsos no se piden acá: se marcan y los pide el scheduler (D-b13).
 */
@Component
@Slf4j
public class GrupoCierre {

    public static final String MOTIVO_CANCELADO = "GRUPO_CANCELADO";
    public static final String MOTIVO_VENCIDO = "PAGO_EN_GRUPO_VENCIDO";

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
            Reservation reserva = bookingRepository.findByIdForUpdate(grupo.getReservation().getId())
                    .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));
            inventario.liberarAsientos(reserva);
            reserva.getDetalles().forEach(d -> d.setPaymentStatus(PaymentStatus.CANCELADO));
            reserva.setEstado(ReservationStatus.CANCELADA);
            reserva.setMotivoCancelacion(motivo);
            bookingRepository.save(reserva);
            grupo.setEstado(estadoFinal);
            grupo.setMotivoCierre(motivo);
            grupo.setActualizadoEn(soporte.ahora());
            grupo.setReembolsosPendientes(grupo.hayPagadas());
            grupoRepository.save(grupo);
            CarritoCalculo.estadiasActivas(reserva).size(); // quedan cargadas para liberarlas después del commit
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
                    g.setEstado(EstadoGrupo.CANCELADO);
                    g.setMotivoCierre(motivo);
                    g.setActualizadoEn(soporte.ahora());
                    g.setReembolsosPendientes(true);
                    grupoRepository.save(g);
                    log.warn("El pago en grupo {} se pagó completo pero la reserva no se pudo confirmar ({}): se reembolsa.",
                            grupoId, motivo);
                }));
    }
}
