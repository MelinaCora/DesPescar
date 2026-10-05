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
 * Con un pago de parte se confirma la parte: PARTE_PAGADA (faltan partes) y CONFIRMADA (era la
 * última) son éxito; CANCELADA (grupo cerrado, vencido o sin lugar) y RECHAZADA se reembolsan igual
 * que en D6.
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
     * Llamar con el pago leido por {@code PaymentRepository.findByIdParaActualizar}, dentro de la
     * transaccion que lo bloqueo.
     * Siempre consulta a reservation-service: es quien sabe si la reserva ya se confirmo con otro
     * pago y responde RECHAZADA/PAGO_DUPLICADO, que se reembolsa como cualquier rechazo.
     *
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

        ConfirmacionReservaResponse confirmacion = confirmarEnReservas(aprobado, transactionId, monto);

        if (confirmacion.exito()) {
            return aprobado;
        }
        if ("PAGO_DUPLICADO".equals(confirmacion.motivo())) {
            log.warn("La reserva {} ya estaba confirmada con otro pago: se reembolsa el pago {}.",
                    aprobado.getReservationId(), aprobado.getId());
        }
        return reembolsar(aprobado, monto, confirmacion);
    }

    /**
     * Otro cobro aprobado de Mercado Pago sobre un pago que ya estaba APPROVED o REFUNDED. Antes de
     * devolverlo se le pregunta a reservation-service (idempotente por token): si la reserva se
     * confirmo justamente con ese cobro, es el verdadero (su respuesta se demoro y el otro llego
     * primero) y el pago pasa a apuntar a el; el cobro anterior se reembolsa si no lo estaba. Si
     * reservation-service no responde lanza ReservationClientException y no se reembolsa nada: la
     * transaccion se deshace y Mercado Pago reintenta la notificacion.
     */
    @Transactional
    public void reembolsarCobroDuplicado(
            Payment payment, String transactionId, BigDecimal montoPagado, PaymentMethod metodo) {
        BigDecimal monto = (montoPagado != null ? montoPagado : payment.getAmount()).setScale(2, RoundingMode.HALF_UP);

        ConfirmacionReservaResponse confirmacion = confirmarEnReservas(payment, transactionId, monto);

        if (confirmacion.exito()) {
            adoptarCobroConfirmado(payment, transactionId, metodo);
            return;
        }

        if (reembolsarCobro(payment, transactionId, monto)) {
            paymentHistoryService.saveHistory(payment, payment.getStatus(), recortar(
                    "Cobro duplicado " + transactionId + " reembolsado (PAGO_DUPLICADO)."));
        }
    }

    /**
     * Un pago sin parte confirma la reserva entera (C3); un pago de parte confirma su parte (CB3), y
     * reservation-service decide si con ella la reserva queda confirmada (D-b10, D-b11).
     */
    private ConfirmacionReservaResponse confirmarEnReservas(Payment payment, String transactionId, BigDecimal monto) {
        if (payment.esDeParte()) {
            return reservationClient.confirmarPagoParte(
                    payment.getReservationId(), payment.getParteNumero(), payment.getUserId(), transactionId, monto);
        }
        return reservationClient.confirmarPago(payment.getReservationId(), payment.getUserId(), transactionId, monto);
    }

    /**
     * El cobro que confirmo la reserva pasa a ser el del pago (con su metodo y fecha); el anterior se
     * devuelve si no se habia devuelto, por el monto que Mercado Pago cobro en ese cobro.
     */
    private void adoptarCobroConfirmado(Payment payment, String transactionId, PaymentMethod metodo) {
        String anterior = payment.getTransactionId();
        boolean anteriorReembolsado = payment.getStatus() == PaymentStatus.REFUNDED;

        payment.setStatus(PaymentStatus.APPROVED);
        payment.setTransactionId(transactionId);
        if (metodo != null) {
            payment.setPaymentMethod(metodo);
        }
        payment.setPaymentDate(LocalDateTime.now());
        Payment guardado = paymentRepository.save(payment);
        paymentHistoryService.saveHistory(guardado, PaymentStatus.APPROVED, recortar(
                "El cobro " + transactionId + " confirmo la reserva; el pago pasa a ese cobro (antes " + anterior + ")."));

        if (!anteriorReembolsado && anterior != null) {
            if (reembolsarCobro(guardado, anterior, MontoCobrado.de(paymentGatewayService, guardado, anterior))) {
                paymentHistoryService.saveHistory(guardado, PaymentStatus.APPROVED, recortar(
                        "Cobro anterior " + anterior + " reembolsado (PAGO_DUPLICADO)."));
            }
        }
    }

    /** Pide el reembolso del cobro; si no sale deja "Reembolso manual pendiente" y devuelve false. */
    private boolean reembolsarCobro(Payment payment, String transactionId, BigDecimal monto) {
        RefundGatewayResponse reembolso;
        try {
            reembolso = paymentGatewayService.refund(transactionId, monto);
        } catch (RuntimeException ex) {
            reembolso = RefundGatewayResponse.builder().approved(false)
                    .message(ex.getClass().getSimpleName()).build();
        }

        if (reembolso != null && reembolso.isApproved()) {
            return true;
        }
        String detalle = reembolso == null ? "sin respuesta del proveedor" : reembolso.getMessage();
        log.warn("Reembolso manual pendiente del cobro duplicado {} del pago {}: {}",
                transactionId, payment.getId(), detalle);
        paymentHistoryService.saveHistory(payment, payment.getStatus(), recortar(
                "Reembolso manual pendiente del cobro duplicado " + transactionId + " (PAGO_DUPLICADO): " + detalle));
        return false;
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

    private static String recortar(String texto) {
        return texto.length() <= LARGO_HISTORIAL ? texto : texto.substring(0, LARGO_HISTORIAL);
    }
}
