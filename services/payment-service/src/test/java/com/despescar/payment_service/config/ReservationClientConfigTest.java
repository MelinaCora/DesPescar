package com.despescar.payment_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

class ReservationClientConfigTest {

    private final ReservationClientConfig config = new ReservationClientConfig();

    private Object lectura(RestTemplate restTemplate) {
        return ReflectionTestUtils.getField(restTemplate.getRequestFactory(), "readTimeout");
    }

    @Test
    void laConfirmacionDelPagoTieneUnTiempoDeLecturaPropio() {
        RestTemplate normal = config.reservationServiceRestTemplate(3000, 5000);
        RestTemplate confirmacion = config.reservationServiceConfirmacionRestTemplate(3000, 15000);

        assertThat(lectura(normal)).isEqualTo(5000);
        assertThat(lectura(confirmacion)).isEqualTo(15000);
    }
}
