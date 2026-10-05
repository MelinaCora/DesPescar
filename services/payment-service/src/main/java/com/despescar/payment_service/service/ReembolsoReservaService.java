package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.despescar.payment_service.dto.response.ReembolsoReservaResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.entity.Refund;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.enums.RefundStatus;
import com.despescar.payment_service.repository.PaymentRepository;
import com.despescar.payment_service.repository.RefundRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Reembolsa un monto de una reserva que su dueño canceló. El monto se reparte entre los pagos
 * aprobados de la reserva (uno solo, o las partes de un pago en grupo) en proporción a lo que cobró
 * cada uno y nunca por encima de eso. Un reembolso parcial deja el pago APPROVED con su renglón en
 * el historial; si se devolvió entero pasa a REFUNDED.
 *
 * <p>Idempotente por pago y motivo: cada reembolso aprobado queda en refunds y un pedido repetido lo
 * cuenta sin volver a llamar al proveedor. Un pago por transacción, con la fila bloqueada. Si el
 * proveedor rechaza, el pago queda con "Reembolso manual pendiente" y cuenta en fallidos; una
 * llamada posterior vuelve a intentar solo esos.
 */
@Service
@Slf4j
public class ReembolsoReservaService {

    static final String MOTIVO_POR_DEFECTO = "CANCELACION_RESERVA";
    private static final int LARGO_HISTORIAL = 255;

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentHistoryService paymentHistoryService;
    private final PaymentGatewayService paymentGatewayService;
    private final TransactionTemplate transactionTemplate;

    public ReembolsoReservaService(PaymentRepository paymentRepository, RefundRepository refundRepository,
                                   PaymentHistoryService paymentHistoryService,
                                   PaymentGatewayService paymentGatewayService, TransactionTemplate transactionTemplate) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.paymentHistoryService = paymentHistoryService;
        this.paymentGatewayService = paymentGatewayService;
        this.transactionTemplate = transactionTemplate;
    }

    public ReembolsoReservaResponse reembolsar(Long reservationId, BigDecimal monto, String motivo) {
        String causa = motivo == null || motivo.isBlank() ? MOTIVO_POR_DEFECTO : motivo.trim();
        List<Refund> hechos = hechos(refundRepository.findByPaymentReservationId(reservationId), causa);
        // El reparto se calcula siempre sobre los mismos pagos: los aprobados y los que ya se reembolsaron por este motivo
        List<Payment> pagos = paymentRepository.findByReservationId(reservationId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.APPROVED || yaReembolsado(hechos, p.getId()) != null)
                .sorted(Comparator.comparing(Payment::getCreatedAt).thenComparing(Payment::getId))
                .toList();
        List<BigDecimal> partes = repartir(pagos.stream().map(Payment::getAmount).toList(), monto);
        BigDecimal reembolsado = BigDecimal.ZERO.setScale(2);
        int fallidos = 0;
        for (int i = 0; i < pagos.size(); i++) {
            BigDecimal parte = partes.get(i);
            if (parte.signum() <= 0) {
                continue;
            }
            UUID id = pagos.get(i).getId();
            BigDecimal hecho = transactionTemplate.execute(estado -> procesar(id, parte, causa));
            if (hecho == null) {
                fallidos++;
            } else {
                reembolsado = reembolsado.add(hecho);
            }
        }
        log.info("Reembolso de la reserva {} ({}): ${} de ${} pedidos, {} pagos, {} fallidos", reservationId, causa,
                reembolsado, monto, pagos.size(), fallidos);
        return new ReembolsoReservaResponse(reembolsado, fallidos);
    }

    /**
     * Reparte total entre los pagos en proporción a lo cobrado, con escala 2. Los centavos que sobran
     * del redondeo van al primero que todavía tenga margen. Nadie recibe más de lo que pagó.
     */
    static List<BigDecimal> repartir(List<BigDecimal> cobrados, BigDecimal total) {
        BigDecimal suma = cobrados.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal aRepartir = total.min(suma).setScale(2, RoundingMode.DOWN);
        List<BigDecimal> partes = new ArrayList<>();
        BigDecimal repartido = BigDecimal.ZERO;
        for (BigDecimal cobrado : cobrados) {
            BigDecimal parte = suma.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                    : aRepartir.multiply(cobrado).divide(suma, 2, RoundingMode.DOWN);
            partes.add(parte);
            repartido = repartido.add(parte);
        }
        BigDecimal resto = aRepartir.subtract(repartido);
        for (int i = 0; i < partes.size() && resto.signum() > 0; i++) {
            BigDecimal extra = resto.min(cobrados.get(i).subtract(partes.get(i)));
            partes.set(i, partes.get(i).add(extra));
            resto = resto.subtract(extra);
        }
        return partes;
    }

    /** Con el pago bloqueado. Devuelve lo reembolsado de ese pago por este motivo, o null si no se pudo. */
    private BigDecimal procesar(UUID id, BigDecimal monto, String motivo) {
        Payment pago = paymentRepository.findByIdParaActualizar(id).orElse(null);
        if (pago == null) {
            return null;
        }
        List<Refund> delPago = refundRepository.findByPaymentReservationId(pago.getReservationId()).stream()
                .filter(r -> r.getStatus() == RefundStatus.APPROVED && Objects.equals(r.getPayment().getId(), id))
                .toList();
        Refund previo = yaReembolsado(hechos(delPago, motivo), id);
        if (previo != null) {
            return previo.getAmount().setScale(2, RoundingMode.HALF_UP);
        }
        if (pago.getStatus() != PaymentStatus.APPROVED) {
            return null;
        }
        RefundGatewayResponse respuesta;
        try {
            respuesta = paymentGatewayService.refund(pago.getTransactionId(), monto);
        } catch (RuntimeException ex) {
            respuesta = RefundGatewayResponse.builder().approved(false).message(ex.getClass().getSimpleName()).build();
        }
        if (respuesta == null || !respuesta.isApproved()) {
            String detalle = respuesta == null ? "sin respuesta del proveedor" : respuesta.getMessage();
            log.warn("Reembolso manual pendiente de ${} del pago {} (reserva {}, motivo {}): {}", monto, pago.getId(),
                    pago.getReservationId(), motivo, detalle);
            paymentHistoryService.saveHistory(pago, PaymentStatus.APPROVED,
                    recortar("Reembolso manual pendiente de $" + monto + " (" + motivo + "): " + detalle));
            return null;
        }
        refundRepository.save(Refund.builder().payment(pago).amount(monto).reason(motivo)
                .status(RefundStatus.APPROVED).refundTransactionId(respuesta.getRefundTransactionId())
                .createdAt(LocalDateTime.now()).processedAt(LocalDateTime.now()).build());
        BigDecimal devuelto = delPago.stream().map(Refund::getAmount).reduce(monto, BigDecimal::add);
        if (devuelto.compareTo(pago.getAmount()) >= 0) {
            pago.setStatus(PaymentStatus.REFUNDED);
            paymentHistoryService.saveHistory(paymentRepository.save(pago), PaymentStatus.REFUNDED,
                    recortar("Reembolso total $" + monto + " (" + motivo + ")."));
        } else {
            paymentHistoryService.saveHistory(pago, PaymentStatus.APPROVED,
                    recortar("Reembolso parcial $" + monto + " (" + motivo + ")."));
        }
        return monto;
    }

    private static List<Refund> hechos(List<Refund> reembolsos, String motivo) {
        return reembolsos.stream()
                .filter(r -> r.getStatus() == RefundStatus.APPROVED && motivo.equals(r.getReason()))
                .toList();
    }

    private static Refund yaReembolsado(List<Refund> hechos, UUID pagoId) {
        return hechos.stream().filter(r -> Objects.equals(r.getPayment().getId(), pagoId)).findFirst().orElse(null);
    }

    private static String recortar(String texto) {
        return texto.length() <= LARGO_HISTORIAL ? texto : texto.substring(0, LARGO_HISTORIAL);
    }
}
