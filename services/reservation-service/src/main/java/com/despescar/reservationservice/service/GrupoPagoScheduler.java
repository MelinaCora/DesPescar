package com.despescar.reservationservice.service;

import com.despescar.reservationservice.client.PaymentClient;
import com.despescar.reservationservice.dto.pagos.ReembolsoGrupoResponse;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.repository.GrupoPagoRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lo que un pago en grupo necesita que pase solo: cerrarse al vencer (D-b14), reintentar la
 * confirmación si quedó COMPLETO (D-b15) —hasta grupo-pago.plazo-confirmacion; después se cancela y
 * se reembolsa— y pedir los reembolsos marcados (D-b13). Cada grupo se
 * procesa aparte: un error se registra y no frena a los demás. Ninguna llamada HTTP va dentro de
 * una transacción. Todo es idempotente: cada paso relee con lock antes de escribir.
 */
@Component
@Slf4j
public class GrupoPagoScheduler {

    static final Duration ESPERA_CONFIRMACION = Duration.ofMinutes(2);

    private final GrupoPagoRepository grupoRepository;
    private final GrupoCierre cierre;
    private final PagoParteService pagoParteService;
    private final PaymentClient paymentClient;
    private final CarritoSoporte soporte;
    private final TransactionTemplate transaccion;
    private final Duration plazoConfirmacion;

    public GrupoPagoScheduler(GrupoPagoRepository grupoRepository, GrupoCierre cierre, PagoParteService pagoParteService,
                              PaymentClient paymentClient, CarritoSoporte soporte, TransactionTemplate transaccion,
                              @Value("${grupo-pago.plazo-confirmacion:30m}") Duration plazoConfirmacion) {
        this.grupoRepository = grupoRepository;
        this.cierre = cierre;
        this.pagoParteService = pagoParteService;
        this.paymentClient = paymentClient;
        this.soporte = soporte;
        this.transaccion = transaccion;
        this.plazoConfirmacion = plazoConfirmacion;
    }

    /** Lo que se lee de un grupo antes de llamar afuera. */
    private record Datos(Long reservaId, String motivo, LocalDateTime completoDesde) {
    }

    @Scheduled(fixedDelay = 60000)
    public void cerrarVencidos() {
        for (Long id : ids(() -> grupoRepository.idsVencidos(EstadoGrupo.ABIERTO, soporte.ahora()))) {
            try {
                cierre.cerrar(id, EstadoGrupo.VENCIDO, GrupoCierre.MOTIVO_VENCIDO, true);
            } catch (RuntimeException ex) {
                log.error("No se pudo cerrar el pago en grupo vencido {}", id, ex);
            }
        }
    }

    @Scheduled(fixedDelay = 120000)
    public void reintentarConfirmaciones() {
        for (Long id : ids(() -> grupoRepository.idsSinCambiosDesde(EstadoGrupo.COMPLETO,
                soporte.ahora().minus(ESPERA_CONFIRMACION)))) {
            Datos datos = null;
            try {
                datos = datos(id);
                if (datos != null) {
                    pagoParteService.finalizar(id, datos.reservaId());
                }
            } catch (RuntimeException ex) {
                if (datos != null && pasoElPlazoDeConfirmacion(datos)) {
                    abandonarConfirmacion(id, ex);
                } else {
                    log.warn("El pago en grupo {} sigue sin poder confirmarse: {}", id, ex.getMessage());
                }
            }
        }
    }

    /** Los reintentos no son eternos: pasado el plazo en COMPLETO, el grupo se cancela y se reembolsa. */
    private boolean pasoElPlazoDeConfirmacion(Datos datos) {
        return datos.completoDesde() != null
                && !soporte.ahora().isBefore(datos.completoDesde().plus(plazoConfirmacion));
    }

    private void abandonarConfirmacion(Long id, RuntimeException causa) {
        try {
            log.error("El pago en grupo {} no se pudo confirmar en {} minutos (último error: {}): se cancela.", id,
                    plazoConfirmacion.toMinutes(), causa.getMessage());
            cierre.cancelarSinConfirmar(id, GrupoCierre.MOTIVO_SIN_CONFIRMAR);
        } catch (RuntimeException ex) {
            log.error("No se pudo cancelar el pago en grupo {} que no se confirmó", id, ex);
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void pedirReembolsos() {
        for (Long id : ids(grupoRepository::idsConReembolsosPendientes)) {
            try {
                Datos datos = datos(id);
                if (datos == null) {
                    continue;
                }
                ReembolsoGrupoResponse hecho = paymentClient.reembolsarGrupo(datos.reservaId(), datos.motivo());
                transaccion.executeWithoutResult(estado -> grupoRepository.findByIdForUpdate(id).ifPresent(g -> {
                    g.setReembolsosPendientes(false);
                    grupoRepository.save(g);
                }));
                if (hecho.fallidos() > 0) {
                    log.error("Pago en grupo {} (reserva {}): {} reembolsos quedaron para hacer a mano.", id,
                            datos.reservaId(), hecho.fallidos());
                } else {
                    log.info("Pago en grupo {} (reserva {}): {} partes reembolsadas y {} pagos pendientes cancelados.", id,
                            datos.reservaId(), hecho.reembolsados(), hecho.cancelados());
                }
            } catch (RuntimeException ex) {
                log.warn("No se pudieron pedir los reembolsos del pago en grupo {}: {}. Se reintenta.", id, ex.getMessage());
            }
        }
    }

    private List<Long> ids(java.util.function.Supplier<List<Long>> consulta) {
        List<Long> ids = transaccion.execute(estado -> consulta.get());
        return ids == null ? List.of() : ids;
    }

    private Datos datos(Long grupoId) {
        return transaccion.execute(estado -> grupoRepository.findById(grupoId)
                .map(g -> new Datos(g.getReservation().getId(),
                        g.getMotivoCierre() == null ? GrupoCierre.MOTIVO_CANCELADO : g.getMotivoCierre(),
                        g.getCompletoDesde() != null ? g.getCompletoDesde() : g.getActualizadoEn()))
                .orElse(null));
    }
}
