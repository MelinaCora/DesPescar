package com.despescar.reservationservice.service;

import com.despescar.reservationservice.dto.grupo.PagoParteRequest;
import com.despescar.reservationservice.dto.grupo.ParteInternaResponse;
import com.despescar.reservationservice.dto.reservation.response.ConfirmacionPagoResponse;
import com.despescar.reservationservice.entity.GrupoPago;
import com.despescar.reservationservice.entity.ParteGrupo;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.EstadoParte;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.GrupoPagoRepository;
import java.time.Duration;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lo que payment-service necesita de un pago en grupo (CB3): leer una parte antes de cobrarla y
 * registrar su pago. El registro va en una transacción corta con el grupo bloqueado; la confirmación
 * de la reserva (última parte) va después, sin transacción ni locks, porque llama a hotel-service.
 * Los reembolsos nunca se piden desde acá (D-b13): el pago que llama está bloqueado en payment-service.
 */
@Service
@Slf4j
public class PagoParteService {

    static final String MONTO_NO_COINCIDE = ConfirmacionPagoResponse.MONTO_NO_COINCIDE;

    private final GrupoPagoRepository grupoRepository;
    private final BookingService bookingService;
    private final GrupoCierre cierre;
    private final CarritoSoporte soporte;
    private final TransactionTemplate transaccion;

    public PagoParteService(GrupoPagoRepository grupoRepository, BookingService bookingService, GrupoCierre cierre,
                            CarritoSoporte soporte, TransactionTemplate transaccion) {
        this.grupoRepository = grupoRepository;
        this.bookingService = bookingService;
        this.cierre = cierre;
        this.soporte = soporte;
        this.transaccion = transaccion;
    }

    /** La parte tal como está ahora. payment-service valida dueño, estados, plazo y moneda (D-b9). */
    public ParteInternaResponse parte(Long reservaId, int numero) {
        return transaccion.execute(estado -> {
            GrupoPago grupo = grupoRepository.findByReservation_Id(reservaId).orElseThrow(PagoParteService::parteNoEncontrada);
            ParteGrupo parte = grupo.parte(numero).orElseThrow(PagoParteService::parteNoEncontrada);
            long segundos = grupo.abierto()
                    ? Math.max(0, Duration.between(soporte.ahora(), grupo.getVenceEn()).toSeconds())
                    : 0;
            return new ParteInternaResponse(reservaId, parte.getNumero(), parte.getUsuarioId(), parte.getMonto(),
                    CarritoCalculo.MONEDA, parte.getEstado(), grupo.getEstado(), segundos);
        });
    }

    /** Qué hacer después de la transacción que registró el pago. */
    private enum Siguiente { NADA, CONFIRMAR, CERRAR_POR_MONTO }

    private record Registro(ConfirmacionPagoResponse respuesta, Siguiente siguiente, Long grupoId) {
        static Registro fin(ConfirmacionPagoResponse respuesta) {
            return new Registro(respuesta, Siguiente.NADA, null);
        }
    }

    /**
     * Registra el pago de una parte (D-b10). Sin @Transactional a propósito. Idempotente por tokenPago.
     * Una reserva sin grupo (o que no existe) responde RECHAZADA PARTE_NO_ENCONTRADA: el cobro se reembolsa.
     */
    public ConfirmacionPagoResponse confirmarPago(Long reservaId, int numero, PagoParteRequest pedido) {
        Registro registro = transaccion.execute(estado -> registrar(reservaId, numero, pedido));
        return switch (registro.siguiente()) {
            case NADA -> registro.respuesta();
            case CONFIRMAR -> finalizar(registro.grupoId(), reservaId);
            case CERRAR_POR_MONTO -> {
                // Con las partes ya guardadas como pagadas: el cierre marca los reembolsos de todas
                cierre.cerrar(registro.grupoId(), EstadoGrupo.CANCELADO, MONTO_NO_COINCIDE, false);
                yield registro.respuesta();
            }
        };
    }

    private Registro registrar(Long reservaId, int numero, PagoParteRequest pedido) {
        GrupoPago grupo = grupoRepository.findByReservaIdForUpdate(reservaId).orElse(null);
        ParteGrupo parte = grupo == null ? null : grupo.parte(numero).orElse(null);
        if (parte == null) {
            return Registro.fin(ConfirmacionPagoResponse.rechazada(ConfirmacionPagoResponse.PARTE_NO_ENCONTRADA,
                    "La reserva no tiene esa parte."));
        }
        if (parte.getEstado() == EstadoParte.PAGADA) {
            if (!Objects.equals(parte.getTokenPago(), pedido.tokenPago())) {
                return Registro.fin(ConfirmacionPagoResponse.rechazada(ConfirmacionPagoResponse.PAGO_DUPLICADO,
                        "Esa parte ya fue pagada con otro pago."));
            }
            // El mismo pago reenviado: se repite el resultado actual
            return switch (grupo.getEstado()) {
                case ABIERTO -> trasPagar(grupo);
                case COMPLETO -> new Registro(null, Siguiente.CONFIRMAR, grupo.getId());
                case CONFIRMADO -> Registro.fin(ConfirmacionPagoResponse.confirmada());
                case CANCELADO, VENCIDO -> Registro.fin(cerrado(grupo));
            };
        }
        if (!grupo.abierto()) {
            return Registro.fin(cerrado(grupo));
        }
        if (!soporte.ahora().isBefore(grupo.getVenceEn())) {
            // Lo cierra el scheduler (D-b14); este cobro se reembolsa ya
            return Registro.fin(ConfirmacionPagoResponse.cancelada(GrupoCierre.MOTIVO_VENCIDO,
                    "El plazo para pagar en grupo ya terminó."));
        }
        if (parte.getEstado() != EstadoParte.TOMADA || !Objects.equals(parte.getUsuarioId(), pedido.pagadorId())) {
            return Registro.fin(ConfirmacionPagoResponse.rechazada(ConfirmacionPagoResponse.PARTE_NO_ES_DEL_PAGADOR,
                    "Esa parte no es de quien pagó."));
        }
        if (parte.getMonto().compareTo(pedido.monto()) != 0) {
            return Registro.fin(ConfirmacionPagoResponse.rechazada(MONTO_NO_COINCIDE,
                    "El monto pagado no coincide con el de la parte."));
        }
        parte.pagar(pedido.tokenPago(), soporte.ahora());
        grupo.setActualizadoEn(soporte.ahora());
        log.info("Se pagó la parte {} del pago en grupo {} (reserva {})", numero, grupo.getId(), reservaId);
        return trasPagar(grupo);
    }

    /** Con el grupo ABIERTO y bloqueado: faltan partes, o están todas y hay que confirmar (D-b11). */
    private Registro trasPagar(GrupoPago grupo) {
        if (!grupo.todasPagadas()) {
            long faltan = grupo.getPartes().size() - grupo.cantidadPagadas();
            return Registro.fin(ConfirmacionPagoResponse.partePagada(
                    "Parte pagada. Faltan " + faltan + " de " + grupo.getPartes().size() + "."));
        }
        if (grupo.montoPagado().compareTo(CarritoCalculo.montoTotal(grupo.getReservation())) != 0) {
            log.error("Las partes del pago en grupo {} no suman el total de la reserva: se cancela y se reembolsa.", grupo.getId());
            return new Registro(ConfirmacionPagoResponse.cancelada(MONTO_NO_COINCIDE,
                    "Las partes pagadas no suman el total del carrito. Se reembolsa a todos."),
                    Siguiente.CERRAR_POR_MONTO, grupo.getId());
        }
        grupo.setEstado(EstadoGrupo.COMPLETO);
        grupo.setCompletoDesde(soporte.ahora());
        grupo.setActualizadoEn(soporte.ahora());
        return new Registro(null, Siguiente.CONFIRMAR, grupo.getId());
    }

    private static ConfirmacionPagoResponse cerrado(GrupoPago grupo) {
        String motivo = grupo.getMotivoCierre() == null ? ConfirmacionPagoResponse.GRUPO_CERRADO : grupo.getMotivoCierre();
        return ConfirmacionPagoResponse.cancelada(motivo, "El pago en grupo ya terminó.");
    }

    /**
     * Confirma la reserva de un grupo COMPLETO (D-b11) y cierra el grupo según el resultado. Sin
     * transacción abierta. La usan la última parte, su reintento y el scheduler (D-b15). Si
     * hotel-service falla, la excepción se propaga y el grupo sigue COMPLETO para reintentar (el
     * scheduler deja de reintentar al pasar el plazo de confirmación y lo cancela).
     */
    public ConfirmacionPagoResponse finalizar(Long grupoId, Long reservaId) {
        ConfirmacionPagoResponse resultado = bookingService.confirmarReservaPagada(reservaId, "GRUPO-" + grupoId);
        if (ConfirmacionPagoResponse.CONFIRMADA.equals(resultado.estado())) {
            transaccion.executeWithoutResult(estado -> grupoRepository.findByIdForUpdate(grupoId)
                    .filter(g -> g.getEstado() == EstadoGrupo.COMPLETO)
                    .ifPresent(g -> {
                        g.setEstado(EstadoGrupo.CONFIRMADO);
                        g.setActualizadoEn(soporte.ahora());
                        grupoRepository.save(g);
                    }));
            log.info("El pago en grupo {} se completó y la reserva {} quedó confirmada.", grupoId, reservaId);
            return resultado;
        }
        if (ConfirmacionPagoResponse.CANCELADA.equals(resultado.estado())) {
            cierre.marcarCanceladoTrasConfirmar(grupoId, resultado.motivo());
            return resultado;
        }
        // RECHAZADA no debería pasar (el carrito está congelado con sus datos completos) y reintentar no la
        // cambia: se cancela la reserva y se reembolsa a todos. Si justo otra confirmación la confirmó, el
        // cierre no hace nada y el reintento encuentra la reserva CONFIRMADA.
        log.error("La reserva {} del pago en grupo {} no se pudo confirmar: {} {}", reservaId, grupoId,
                resultado.estado(), resultado.motivo());
        if (cierre.cancelarSinConfirmar(grupoId, GrupoCierre.MOTIVO_SIN_CONFIRMAR)) {
            return ConfirmacionPagoResponse.cancelada(GrupoCierre.MOTIVO_SIN_CONFIRMAR,
                    "No se pudo confirmar la reserva. Se reembolsa a todos.");
        }
        throw new BookingException("CONFIRMACION_DE_GRUPO_FALLIDA",
                "No se pudo confirmar la reserva del pago en grupo.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static BookingException parteNoEncontrada() {
        return new BookingException("PARTE_NO_ENCONTRADA", "La reserva no tiene esa parte.", HttpStatus.NOT_FOUND);
    }
}
