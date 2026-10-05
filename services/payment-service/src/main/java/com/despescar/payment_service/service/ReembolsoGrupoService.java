package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.despescar.payment_service.dto.response.ReembolsoGrupoResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.repository.PaymentRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Reembolsa las partes pagadas de un pago en grupo que se canceló o venció (D-b13, CB5): cada pago
 * APPROVED con parte se devuelve entero (D-b25), por lo que el proveedor cobró en ese cobro, cada
 * PENDING con parte se cancela y el resto no se toca. Un pago por transacción, bloqueado con findByIdParaActualizar, para que la llamada al
 * proveedor no sostenga más que esa fila. Si el proveedor rechaza o falla, el pago queda APPROVED
 * con "Reembolso manual pendiente" (D6) y cuenta en fallidos: reservation-service recibe 200 y no
 * insiste; una llamada posterior volvería a intentar solo esos.
 */
@Service
@Slf4j
public class ReembolsoGrupoService {

    static final String MOTIVO_POR_DEFECTO = "GRUPO_CERRADO";
    private static final int LARGO_HISTORIAL = 255;

    private final PaymentRepository paymentRepository;
    private final PaymentHistoryService paymentHistoryService;
    private final PaymentGatewayService paymentGatewayService;
    private final TransactionTemplate transactionTemplate;

    public ReembolsoGrupoService(PaymentRepository paymentRepository, PaymentHistoryService paymentHistoryService,
                                 PaymentGatewayService paymentGatewayService, TransactionTemplate transactionTemplate) {
        this.paymentRepository = paymentRepository;
        this.paymentHistoryService = paymentHistoryService;
        this.paymentGatewayService = paymentGatewayService;
        this.transactionTemplate = transactionTemplate;
    }

    private enum Resultado { REEMBOLSADO, CANCELADO, FALLIDO, NADA }

    public ReembolsoGrupoResponse reembolsarGrupo(Long reservationId, String motivo) {
        String causa = motivo == null || motivo.isBlank() ? MOTIVO_POR_DEFECTO : motivo.trim();
        int reembolsados = 0;
        int cancelados = 0;
        int fallidos = 0;
        List<UUID> ids = paymentRepository.idsDePartes(reservationId);
        for (UUID id : ids) {
            Resultado r = transactionTemplate.execute(estado -> procesar(id, causa));
            switch (r == null ? Resultado.NADA : r) {
                case REEMBOLSADO -> reembolsados++;
                case CANCELADO -> cancelados++;
                case FALLIDO -> fallidos++;
                default -> { }
            }
        }
        log.info("Reembolsos del grupo de la reserva {} ({}): {} reembolsados, {} cancelados, {} fallidos de {} pagos",
                reservationId, causa, reembolsados, cancelados, fallidos, ids.size());
        return new ReembolsoGrupoResponse(reembolsados, cancelados, fallidos);
    }

    /** Con el pago bloqueado: se decide por el estado actual, no por el de la lista (otro hilo pudo tocarlo). */
    private Resultado procesar(UUID id, String motivo) {
        Payment pago = paymentRepository.findByIdParaActualizar(id).orElse(null);
        if (pago == null || pago.getParteNumero() == null) {
            return Resultado.NADA;
        }
        if (pago.getStatus() == PaymentStatus.PENDING || pago.getStatus() == PaymentStatus.AUTHORIZED) {
            pago.setStatus(PaymentStatus.CANCELLED);
            Payment cancelado = paymentRepository.save(pago);
            paymentHistoryService.saveHistory(cancelado, PaymentStatus.CANCELLED,
                    recortar("Cancelado: el pago en grupo se cerro (" + motivo + ")."));
            return Resultado.CANCELADO;
        }
        if (pago.getStatus() != PaymentStatus.APPROVED) {
            return Resultado.NADA;
        }
        BigDecimal monto = MontoCobrado.de(paymentGatewayService, pago, pago.getTransactionId());
        RefundGatewayResponse reembolso;
        try {
            reembolso = paymentGatewayService.refund(pago.getTransactionId(), monto);
        } catch (RuntimeException ex) {
            reembolso = RefundGatewayResponse.builder().approved(false).message(ex.getClass().getSimpleName()).build();
        }
        if (reembolso != null && reembolso.isApproved()) {
            pago.setStatus(PaymentStatus.REFUNDED);
            Payment devuelto = paymentRepository.save(pago);
            paymentHistoryService.saveHistory(devuelto, PaymentStatus.REFUNDED,
                    recortar("Reembolso automatico de la parte " + pago.getParteNumero() + " (" + motivo + ")."));
            return Resultado.REEMBOLSADO;
        }
        String detalle = reembolso == null ? "sin respuesta del proveedor" : reembolso.getMessage();
        log.warn("Reembolso manual pendiente del pago {} (reserva {}, parte {}, motivo {}): {}",
                pago.getId(), pago.getReservationId(), pago.getParteNumero(), motivo, detalle);
        paymentHistoryService.saveHistory(pago, PaymentStatus.APPROVED,
                recortar("Reembolso manual pendiente (" + motivo + "): " + detalle));
        return Resultado.FALLIDO;
    }

    private static String recortar(String texto) {
        return texto.length() <= LARGO_HISTORIAL ? texto : texto.substring(0, LARGO_HISTORIAL);
    }
}
