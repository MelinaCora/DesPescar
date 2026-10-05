package com.despescar.payment_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.despescar.payment_service.client.dto.ParteReservaResponse;
import com.despescar.payment_service.exception.ReservationClientException;

/** GET de una parte (contrato CB3). En PB2 se le suman los tests de confirmarPagoParte. */
class ReservationClientParteTest {

    private static final String BASE = "http://reservas";

    private MockRestServiceServer server;
    private ReservationClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new ReservationClient(restTemplate, BASE, "token-pagos");
    }

    @Test
    void leeLaParteConSuDuenoMontoYEstados() {
        server.expect(requestTo(BASE + "/api/bookings/internal/12/partes/2"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Service-Token", "token-pagos"))
                .andRespond(withSuccess("""
                        {"reservaId":12,"numero":2,"usuarioId":9,"monto":353333.33,"moneda":"ARS",
                         "estadoParte":"TOMADA","estadoGrupo":"ABIERTO","segundosRestantes":86100,"otro":1}
                        """, MediaType.APPLICATION_JSON));

        ParteReservaResponse p = client.getParte(12L, 2).orElseThrow();

        assertThat(p.getReservaId()).isEqualTo(12L);
        assertThat(p.getNumero()).isEqualTo(2);
        assertThat(p.getUsuarioId()).isEqualTo(9L);
        assertThat(p.getMonto()).isEqualByComparingTo("353333.33");
        assertThat(p.getMoneda()).isEqualTo("ARS");
        assertThat(p.getEstadoParte()).isEqualTo("TOMADA");
        assertThat(p.getEstadoGrupo()).isEqualTo("ABIERTO");
        assertThat(p.getSegundosRestantes()).isEqualTo(86100L);
        server.verify();
    }

    @Test
    void unaParteLibreLlegaConUsuarioNull() {
        server.expect(requestTo(BASE + "/api/bookings/internal/12/partes/3"))
                .andRespond(withSuccess("""
                        {"reservaId":12,"numero":3,"usuarioId":null,"monto":353333.33,"moneda":"ARS",
                         "estadoParte":"LIBRE","estadoGrupo":"ABIERTO","segundosRestantes":86100}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getParte(12L, 3).orElseThrow().getUsuarioId()).isNull();
    }

    @Test
    void un404EsOptionalVacio() {
        server.expect(requestTo(BASE + "/api/bookings/internal/12/partes/2"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"codigo\":\"PARTE_NO_ENCONTRADA\",\"mensaje\":\"...\",\"timestamp\":\"2026-10-05T10:00:00\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThat(client.getParte(12L, 2)).isEqualTo(Optional.empty());
    }

    @Test
    void un5xxOUn401SonErrorDeComunicacion() {
        server.expect(requestTo(BASE + "/api/bookings/internal/12/partes/2")).andRespond(withServerError());
        assertThatThrownBy(() -> client.getParte(12L, 2)).isInstanceOf(ReservationClientException.class);

        server.reset();
        server.expect(requestTo(BASE + "/api/bookings/internal/12/partes/2"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.getParte(12L, 2)).isInstanceOf(ReservationClientException.class);
    }

    @Test
    void unCuerpoVacioEsErrorDeComunicacion() {
        server.expect(requestTo(BASE + "/api/bookings/internal/12/partes/2"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getParte(12L, 2)).isInstanceOf(ReservationClientException.class);
    }
}
