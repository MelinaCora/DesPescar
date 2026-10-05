package com.despescar.payment_service.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;

@SpringBootTest
class PaymentPendienteUnicoTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @AfterEach
    void limpiar() {
        paymentRepository.deleteAll();
    }

    private Payment pago(Long reservaId, PaymentStatus estado) {
        return Payment.builder()
                .reservationId(reservaId).userId(7L).amount(new BigDecimal("10.00"))
                .status(estado).currency("ARS").provider(PaymentProvider.MOCK).build();
    }

    @Test
    void dosPendientesDeLaMismaReservaViolanElIndice() {
        paymentRepository.saveAndFlush(pago(1L, PaymentStatus.PENDING));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(pago(1L, PaymentStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void losPagosQueNoEstanPendientesNoCuentan() {
        paymentRepository.saveAndFlush(pago(2L, PaymentStatus.PENDING));
        paymentRepository.saveAndFlush(pago(2L, PaymentStatus.REJECTED));
        paymentRepository.saveAndFlush(pago(2L, PaymentStatus.REJECTED));
        paymentRepository.saveAndFlush(pago(2L, PaymentStatus.CANCELLED));
        paymentRepository.saveAndFlush(pago(2L, PaymentStatus.CANCELLED));
        paymentRepository.saveAndFlush(pago(3L, PaymentStatus.PENDING));

        assertThat(paymentRepository.findByReservationId(2L)).hasSize(5);
    }

    @Test
    void cancelarUnPendienteLiberaElLugarParaOtro() {
        Payment viejo = paymentRepository.saveAndFlush(pago(4L, PaymentStatus.PENDING));
        viejo.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.saveAndFlush(viejo);

        paymentRepository.saveAndFlush(pago(4L, PaymentStatus.PENDING));

        assertThat(paymentRepository.findByReservationId(4L)).hasSize(2);
    }
}
