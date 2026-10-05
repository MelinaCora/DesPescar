package com.despescar.payment_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.despescar.payment_service.client.dto.ConfirmacionReservaResponse;
import com.despescar.payment_service.client.dto.ReservationResponse;
import com.despescar.payment_service.exception.ReservationClientException;

class ReservationClientTest {

    private static final String BASE = "http://reservas";

    private MockRestServiceServer server;
    private ReservationClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new ReservationClient(restTemplate, BASE + "/", "token-pagos");
    }

    @Test
    void leeElCarritoDelContratoC3IgnorandoLoQueNoUsa() {
        server.expect(requestTo(BASE + "/api/bookings/internal/12"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Service-Token", "token-pagos"))
                .andRespond(withSuccess("""
                        {"idCarrito":12,"creadorId":7,"estadoGeneral":"PENDIENTE_PAGO","segundosRestantes":812,
                         "montoTotal":1060000.00,"moneda":"ARS","cantidadItems":2,"datosCompletos":true,
                         "vuelo":{"flightIds":["a"],"subtotal":480000.00},"estadias":[{"id":3}],"asientos":[]}
                        """, MediaType.APPLICATION_JSON));

        ReservationResponse r = client.getReservation(12L);

        assertThat(r.getIdCarrito()).isEqualTo(12L);
        assertThat(r.getCreadorId()).isEqualTo(7L);
        assertThat(r.getEstadoGeneral()).isEqualTo("PENDIENTE_PAGO");
        assertThat(r.getSegundosRestantes()).isEqualTo(812L);
        assertThat(r.getMontoTotal()).isEqualByComparingTo("1060000.00");
        assertThat(r.getMoneda()).isEqualTo("ARS");
        server.verify();
    }

    @Test
    void confirmaElPagoConPagadorTokenYMonto() {
        server.expect(requestTo(BASE + "/api/bookings/internal/12/payment-confirmed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", "token-pagos"))
                .andExpect(jsonPath("$.pagadorId").value(7))
                .andExpect(jsonPath("$.tokenPago").value("MOCK-abc"))
                .andExpect(jsonPath("$.monto").value(1060000.00))
                .andRespond(withSuccess("""
                        {"estado":"CANCELADA","motivo":"PAGO_TARDIO_SIN_DISPONIBILIDAD","mensaje":"Sin lugar."}
                        """, MediaType.APPLICATION_JSON));

        ConfirmacionReservaResponse r = client.confirmarPago(12L, 7L, "MOCK-abc", new BigDecimal("1060000.00"));

        assertThat(r.confirmada()).isFalse();
        assertThat(r.estado()).isEqualTo("CANCELADA");
        assertThat(r.motivo()).isEqualTo("PAGO_TARDIO_SIN_DISPONIBILIDAD");
        server.verify();
    }

    private void responde(int id, org.springframework.test.web.client.ResponseCreator respuesta) {
        server.expect(requestTo(BASE + "/api/bookings/internal/" + id + "/payment-confirmed")).andRespond(respuesta);
    }

    private static org.springframework.test.web.client.ResponseCreator json(HttpStatus status, String body) {
        return withStatus(status).body(body).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void soloLosCodigosConocidosDeUn400OUn404SeTratanComoRechazados() {
        responde(12, json(HttpStatus.BAD_REQUEST, "{\"codigo\":\"PAGADOR_INVALIDO\",\"mensaje\":\"x\"}"));
        responde(14, json(HttpStatus.BAD_REQUEST, "{\"codigo\":\"VALIDACION\",\"mensaje\":\"x\"}"));
        responde(13, json(HttpStatus.NOT_FOUND, "{\"codigo\":\"RESERVA_NO_ENCONTRADA\",\"mensaje\":\"x\"}"));

        assertThat(client.confirmarPago(12L, 7L, "t", BigDecimal.TEN).motivo()).isEqualTo("PEDIDO_INVALIDO");
        assertThat(client.confirmarPago(14L, 7L, "t", BigDecimal.TEN).motivo()).isEqualTo("PEDIDO_INVALIDO");
        ConfirmacionReservaResponse inexistente = client.confirmarPago(13L, 7L, "t", BigDecimal.TEN);
        assertThat(inexistente.estado()).isEqualTo("RECHAZADA");
        assertThat(inexistente.motivo()).isEqualTo("RESERVA_NO_ENCONTRADA");
    }

    @Test
    void unaRespuestaAmbiguaNoSeTomaComoRechazoYNoReembolsa() {
        responde(1, withStatus(HttpStatus.NOT_FOUND));
        responde(2, json(HttpStatus.NOT_FOUND, "{\"codigo\":\"RUTA_INEXISTENTE\"}"));
        responde(3, withBadRequest());
        responde(4, json(HttpStatus.BAD_REQUEST, "{\"codigo\":\"OTRA_COSA\"}"));
        responde(5, withStatus(HttpStatus.UNAUTHORIZED));
        responde(6, withStatus(HttpStatus.FORBIDDEN));
        responde(7, json(HttpStatus.CONFLICT, "{\"codigo\":\"ESTADO_INVALIDO\"}"));

        for (int id = 1; id <= 7; id++) {
            long reserva = id;
            assertThatThrownBy(() -> client.confirmarPago(reserva, 7L, "t", BigDecimal.TEN))
                    .as("reserva %d", reserva)
                    .isInstanceOf(ReservationClientException.class);
        }
    }

    @Test
    void laConfirmacionUsaSuPropioClienteConMasTiempo() {
        RestTemplate lectura = new RestTemplate();
        RestTemplate confirmacion = new RestTemplate();
        MockRestServiceServer serverConfirmacion = MockRestServiceServer.bindTo(confirmacion).build();
        ReservationClient dos = new ReservationClient(lectura, confirmacion, BASE, "token-pagos");
        serverConfirmacion.expect(requestTo(BASE + "/api/bookings/internal/12/payment-confirmed"))
                .andRespond(withSuccess("{\"estado\":\"CONFIRMADA\"}", MediaType.APPLICATION_JSON));

        assertThat(dos.confirmarPago(12L, 7L, "t", BigDecimal.TEN).confirmada()).isTrue();
        serverConfirmacion.verify();
    }

    @Test
    void siReservationServiceFallaNoSeSabeElResultadoYSeLanza() {
        server.expect(requestTo(BASE + "/api/bookings/internal/12/payment-confirmed"))
                .andRespond(withServiceUnavailable());

        assertThatThrownBy(() -> client.confirmarPago(12L, 7L, "t", BigDecimal.TEN))
                .isInstanceOf(ReservationClientException.class);
    }
}
