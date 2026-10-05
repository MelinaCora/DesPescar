package com.despescar.reservationservice.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.despescar.reservationservice.config.PaymentClientConfig;
import com.despescar.reservationservice.dto.pagos.ReembolsoGrupoResponse;
import com.despescar.reservationservice.exception.BookingException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class PaymentClientTest {

    private static final String BASE = "http://localhost:8084";
    private static final String URL = BASE + "/api/payments/internal/grupos/12/reembolsos";

    private MockRestServiceServer server;
    private PaymentClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new PaymentClientConfig().paymentServiceRestTemplate(3000, 10000);
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new PaymentClient(restTemplate, BASE + "/", "token-pagos");
    }

    @Test
    void pideLosReembolsosConElTokenYElMotivo() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", "token-pagos"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(jsonPath("$.motivo").value("PAGO_EN_GRUPO_VENCIDO"))
                .andRespond(withSuccess("{\"reembolsados\":2,\"cancelados\":1,\"fallidos\":0,\"otro\":true}",
                        MediaType.APPLICATION_JSON));

        ReembolsoGrupoResponse r = client.reembolsarGrupo(12L, "PAGO_EN_GRUPO_VENCIDO");

        server.verify();
        assertEquals(new ReembolsoGrupoResponse(2, 1, 0), r);
    }

    @Test
    void unTokenRechazadoEsUnErrorDePasarela() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        BookingException ex = assertThrows(BookingException.class, () -> client.reembolsarGrupo(12L, "GRUPO_CANCELADO"));

        assertEquals("PAYMENT_SERVICE_ERROR", ex.getCodigo());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    void unaCaidaEsServicioNoDisponible() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("Connection refused")));

        BookingException ex = assertThrows(BookingException.class, () -> client.reembolsarGrupo(12L, "GRUPO_CANCELADO"));

        assertEquals("PAYMENT_SERVICE_UNAVAILABLE", ex.getCodigo());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void unErrorDelServidorTambienSeReintenta() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        BookingException ex = assertThrows(BookingException.class, () -> client.reembolsarGrupo(12L, "GRUPO_CANCELADO"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    void sinTokenCadaPedidoFallidoQuedaComoErrorConLaVariableQueFalta() {
        RestTemplate restTemplate = new PaymentClientConfig().paymentServiceRestTemplate(3000, 10000);
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new PaymentClient(restTemplate, BASE, " ");
        Logger logger = (Logger) LoggerFactory.getLogger(PaymentClient.class);
        ListAppender<ILoggingEvent> registro = new ListAppender<>();
        registro.start();
        logger.addAppender(registro);
        try {
            server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
            assertThrows(BookingException.class, () -> client.reembolsarGrupo(12L, "GRUPO_CANCELADO"));
            server.reset();
            server.expect(requestTo(URL)).andRespond(withException(new IOException("Connection refused")));
            assertThrows(BookingException.class, () -> client.reembolsarGrupo(12L, "GRUPO_CANCELADO"));
        } finally {
            logger.detachAppender(registro);
        }

        long errores = registro.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR && e.getFormattedMessage().contains("PAYMENT_SERVICE_SYNC_TOKEN"))
                .count();
        assertEquals(2, errores);
        assertTrue(registro.list.stream().noneMatch(e -> e.getLevel() == Level.WARN));
    }
}
