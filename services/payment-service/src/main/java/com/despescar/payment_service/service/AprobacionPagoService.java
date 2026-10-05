package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.despescar.payment_service.client.ReservationClient;
import com.despescar.payment_service.client.dto.ConfirmacionReservaResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentMethod;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lo que pasa cuando un proveedor aprueba un pago (webhook o conciliacion de Mercado Pago,
 * simulador del mock): queda APPROVED, se confirma la reserva y, si reservation-service no la
 * confirma (pago tardio sin lugar, monto distinto, reserva cancelada; D6 y D7), se reembolsa el
 * total. Si el reembolso no sale, el pago queda APPROVED con "Reembolso manual pendiente".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AprobacionPagoService {

    private static final int LARGO_HISTORIAL = 255;

    private final PaymentRepository paymentRepository;
    private final PaymentHistoryService paymentHistoryService;
    private final PaymentGatewayService paymentGatewayService;
    private final ReservationClient reservationClient;

    /**
     * @param montoPagado lo que informa el proveedor; si es null se usa el monto del pago. Se manda
     * con escala 2 (Mercado Pago puede informar 1060000 sin decimales).
     * Si reservation-service no responde lanza ReservationClientException y la transaccion se
     * deshace (el pago sigue como estaba y se puede reintentar).
     */
    @Transactional
    public Payment aprobar(Payment payment, String transactionId, PaymentMethod metodo, BigDecimal montoPagado) {
        if (payment.getStatus() == PaymentStatus.APPROVED || payment.getStatus() == PaymentStatus.REFUNDED) {
            return payment;
        }

        BigDecimal monto = (montoPagado != null ? montoPagado : payment.getAmount()).setScale(2, RoundingMode.HALF_UP);
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setTransactionId(transactionId);
        payment.setPaymentMethod(metodo);
        payment.setPaymentDate(LocalDateTime.now());
        Payment aprobado = paymentRepository.save(payment);
        paymentHistoryService.saveHistory(aprobado, PaymentStatus.APPROVED,
                "Pago aprobado por " + aprobado.getProvider() + ".");

        if (otroPagoAprobado(aprobado)) {
            // reservation-service responde CONFIRMADA a cualquier pago de una reserva ya confirmada:
            // el segundo cobro (p. ej. un pago reemplazado que igual se completo) se devuelve aca.
            return reembolsar(aprobado, monto, ConfirmacionReservaResponse.rechazada(
                    "PAGO_DUPLICADO", "La reserva ya fue pagada con otro pago."));
        }

        ConfirmacionReservaResponse confirmacion = reservationClient.confirmarPago(
                aprobado.getReservationId(), aprobado.getUserId(), transactionId, monto);

        if (confirmacion.confirmada()) {
            return aprobado;
        }
        return reembolsar(aprobado, monto, confirmacion);
    }

    private Payment reembolsar(Payment payment, BigDecimal monto, ConfirmacionReservaResponse confirmacion) {
        String motivo = confirmacion.motivo() != null ? confirmacion.motivo() : confirmacion.estado();

        RefundGatewayResponse reembolso;
        try {
            reembolso = paymentGatewayService.refund(payment.getTransactionId(), monto);
        } catch (RuntimeException ex) {
            reembolso = RefundGatewayResponse.builder().approved(false)
                    .message(ex.getClass().getSimpleName()).build();
        }

        if (reembolso != null && reembolso.isApproved()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            Payment reembolsado = paymentRepository.save(payment);
            paymentHistoryService.saveHistory(reembolsado, PaymentStatus.REFUNDED, recortar(
                    "Reembolso automatico (" + motivo + "): " + confirmacion.mensaje()));
            return reembolsado;
        }

        String detalle = reembolso == null ? "sin respuesta del proveedor" : reembolso.getMessage();
        log.warn("Reembolso manual pendiente del pago {} (reserva {}, motivo {}): {}",
                payment.getId(), payment.getReservationId(), motivo, detalle);
        paymentHistoryService.saveHistory(payment, PaymentStatus.APPROVED, recortar(
                "Reembolso manual pendiente (" + motivo + "): " + detalle));
        return payment;
    }

    private boolean otroPagoAprobado(Payment payment) {
        return paymentRepository.findByReservationId(payment.getReservationId()).stream()
                .anyMatch(otro -> !otro.getId().equals(payment.getId()) && otro.getStatus() == PaymentStatus.APPROVED);
    }

    private static String recortar(String texto) {
        return texto.length() <= LARGO_HISTORIAL ? texto : texto.substring(0, LARGO_HISTORIAL);
    }
}
