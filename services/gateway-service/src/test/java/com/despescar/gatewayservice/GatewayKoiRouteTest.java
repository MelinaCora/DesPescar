package com.despescar.gatewayservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.env.Environment;

@SpringBootTest
class GatewayKoiRouteTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Autowired
    private Environment environment;

    @Test
    void laRutaDeKoiEsperaHasta30Segundos() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();
        RouteDefinition koi = routes.stream().filter(r -> "koi-ia".equals(r.getId())).findFirst().orElseThrow();

        assertThat(String.valueOf(koi.getMetadata().get("response-timeout"))).isEqualTo("30000");
        assertThat(environment.getProperty("resilience4j.timelimiter.instances.koiCircuitBreaker.timeoutDuration",
                Duration.class)).isEqualTo(Duration.ofSeconds(31));
        // Las demás rutas siguen cortando a los 5 s
        assertThat(environment.getProperty("spring.cloud.gateway.httpclient.response-timeout", Duration.class))
                .isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void elLimiteDeKoiEsConfigurableYPorDefectoEs10() {
        assertThat(environment.getProperty("gateway.rate-limit.koi-messages-per-minute", Integer.class)).isEqualTo(10);
    }

    @Test
    void koiTieneSuPropioCircuitBreakerYLasDemasRutasElCompartido() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        for (RouteDefinition route : routes) {
            List<String> breakers = route.getFilters().stream()
                    .filter(f -> "CircuitBreaker".equals(f.getName()))
                    .map(f -> f.getArgs().get("name"))
                    .toList();
            String esperado = "koi-ia".equals(route.getId()) ? "koiCircuitBreaker" : "gatewayCircuitBreaker";
            assertThat(breakers).as(route.getId()).containsExactly(esperado);
        }
        assertThat(environment.getProperty("resilience4j.timelimiter.instances.koiCircuitBreaker.timeoutDuration",
                Duration.class)).isEqualTo(Duration.ofSeconds(31));
        assertThat(environment.getProperty("resilience4j.timelimiter.instances.gatewayCircuitBreaker.timeoutDuration",
                Duration.class)).isEqualTo(Duration.ofSeconds(5));
    }
}
