package com.despescar.gatewayservice.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayRateLimitFilterTest {

    private final GatewayFilterChain chain = exchange -> Mono.empty();

    private ServerWebExchange enviar(GatewayRateLimitFilter filter, MockServerHttpRequest.BaseBuilder<?> builder,
            String remota, String forwardedFor) {
        try {
            if (remota != null) {
                builder.remoteAddress(new InetSocketAddress(InetAddress.getByName(remota), 40000));
            }
            if (forwardedFor != null) {
                builder.header("X-Forwarded-For", forwardedFor);
            }
        } catch (java.net.UnknownHostException e) {
            throw new IllegalStateException(e);
        }
        ServerWebExchange exchange = MockServerWebExchange.from(builder.build());
        filter.filter(exchange, chain).block();
        return exchange;
    }

    private boolean limitado(ServerWebExchange exchange) {
        return exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
    }

    @Test
    void losMensajesAKoiTienenUnLimitePropioPorIp() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(120, 2, "");

        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions"), "1.1.1.1", null))).isFalse();
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions/a/messages"), "1.1.1.1", null))).isFalse();
        ServerWebExchange tercero = enviar(filter, MockServerHttpRequest.post("/api/koi/sessions/a/messages"), "1.1.1.1", null);
        assertThat(limitado(tercero)).isTrue();
        assertThat(tercero.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");

        // Otra IP tiene su propio balde; leer el historial (GET) y el resto de la API no consumen el de KOI
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions/b/messages"), "2.2.2.2", null))).isFalse();
        assertThat(limitado(enviar(filter, MockServerHttpRequest.get("/api/koi/sessions/a/messages"), "1.1.1.1", null))).isFalse();
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/bookings/carrito/estadias"), "1.1.1.1", null))).isFalse();
    }

    @Test
    void elLimiteDeKoiTambienAplicaALaRutaSinBarraFinal() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(120, 1, "");

        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi"), "4.4.4.4", null))).isFalse();
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi"), "4.4.4.4", null))).isTrue();
    }

    @Test
    void elLimiteGeneralSigueValiendoParaTodo() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(2, 10, "");

        assertThat(limitado(enviar(filter, MockServerHttpRequest.get("/api/hotels"), "3.3.3.3", null))).isFalse();
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions"), "3.3.3.3", null))).isFalse();
        assertThat(limitado(enviar(filter, MockServerHttpRequest.get("/api/hotels"), "3.3.3.3", null))).isTrue();
    }

    @Test
    void sinProxiesDeConfianzaElForwardedForDelClienteSeIgnora() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(120, 2, "");

        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions"), "5.5.5.5", "9.9.9.1"))).isFalse();
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions"), "5.5.5.5", "9.9.9.2"))).isFalse();
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions"), "5.5.5.5", "9.9.9.3"))).isTrue();
    }

    @Test
    void unaIpRemotaQueNoEsProxyDeConfianzaTampocoPuedeFalsearElForwardedFor() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(120, 1, "10.0.0.1");

        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions"), "5.5.5.5", "9.9.9.1"))).isFalse();
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions"), "5.5.5.5", "9.9.9.2"))).isTrue();
    }

    @Test
    void detrasDeUnProxyDeConfianzaSeUsaLaIpDelClienteMasADerechaQueNoEsProxy() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(120, 1, "10.0.0.1, 10.0.0.2");

        // El cliente antepone una IP falsa; el proxy agrega la real (6.6.6.6) y hay otro proxy en la cadena
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions"), "10.0.0.1",
                "1.2.3.4, 6.6.6.6, 10.0.0.2"))).isFalse();
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions"), "10.0.0.1",
                "7.7.7.7, 6.6.6.6, 10.0.0.2"))).isTrue();
        // Otro cliente real detras del mismo proxy tiene su propio balde
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions"), "10.0.0.1",
                "8.8.8.8"))).isFalse();
    }

    @Test
    void sinIpRemotaSeAgrupaComoDesconocida() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(120, 1, "");

        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions"), null, "9.9.9.1"))).isFalse();
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions"), null, "9.9.9.2"))).isTrue();
    }
}
