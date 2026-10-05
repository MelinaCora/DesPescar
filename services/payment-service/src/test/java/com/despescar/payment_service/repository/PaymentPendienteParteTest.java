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

/** Un solo PENDING por (reserva, parte); las partes no chocan entre si ni con el pago sin parte (D-b9). */
@SpringBootTest
class PaymentPendienteParteTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @AfterEach
    void limpiar() {
        paymentRepository.deleteAll();
    }

    private Payment pago(Long reservaId, Integer parte, PaymentStatus estado) {
        return Payment.builder()
                .reservationId(reservaId).userId(7L).parteNumero(parte).amount(new BigDecimal("10.00"))
                .status(estado).currency("ARS").provider(PaymentProvider.MOCK).build();
    }

    @Test
    void dosPendientesDeLaMismaParteViolanElIndice() {
        paymentRepository.saveAndFlush(pago(1L, 2, PaymentStatus.PENDING));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(pago(1L, 2, PaymentStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void partesDistintasDeLaMismaReservaConviven() {
        paymentRepository.saveAndFlush(pago(2L, 1, PaymentStatus.PENDING));
        paymentRepository.saveAndFlush(pago(2L, 2, PaymentStatus.PENDING));
        paymentRepository.saveAndFlush(pago(2L, 3, PaymentStatus.PENDING));

        assertThat(paymentRepository.findByReservationId(2L)).hasSize(3);
    }

    @Test
    void unaParteYElPagoSinParteNoChocan() {
        Payment entero = paymentRepository.saveAndFlush(pago(3L, null, PaymentStatus.PENDING));
        Payment parte = paymentRepository.saveAndFlush(pago(3L, 1, PaymentStatus.PENDING));

        assertThat(entero.getPendienteDeReserva()).isEqualTo(3L);
        assertThat(entero.getPendienteDeParte()).isNull();
        assertThat(parte.getPendienteDeReserva()).isNull();
        assertThat(parte.getPendienteDeParte()).isEqualTo("3:1");
    }

    @Test
    void laMismaParteEnOtraReservaNoChoca() {
        paymentRepository.saveAndFlush(pago(4L, 2, PaymentStatus.PENDING));
        paymentRepository.saveAndFlush(pago(5L, 2, PaymentStatus.PENDING));

        assertThat(paymentRepository.findByReservationId(5L)).hasSize(1);
    }

    @Test
    void aprobarOCancelarLiberaElLugarDeLaParte() {
        Payment viejo = paymentRepository.saveAndFlush(pago(6L, 2, PaymentStatus.PENDING));
        viejo.setStatus(PaymentStatus.CANCELLED);
        Payment cancelado = paymentRepository.saveAndFlush(viejo); // la copia administrada es la que pasa por @PreUpdate
        assertThat(cancelado.getPendienteDeParte()).isNull();

        Payment nuevo = paymentRepository.saveAndFlush(pago(6L, 2, PaymentStatus.PENDING));
        nuevo.setStatus(PaymentStatus.APPROVED);
        paymentRepository.saveAndFlush(nuevo);

        assertThat(paymentRepository.findByReservationId(6L))
                .allMatch(p -> p.getPendienteDeParte() == null);
    }
}
