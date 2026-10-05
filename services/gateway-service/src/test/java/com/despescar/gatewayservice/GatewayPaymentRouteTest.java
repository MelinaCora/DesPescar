package com.despescar.gatewayservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

@SpringBootTest
class GatewayPaymentRouteTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void faresRouteShouldBeExposedAndBaggagePoliciesRouteShouldNotExist() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        RouteDefinition flightsRoute = routes.stream()
                .filter(route -> "flights".equals(route.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(flightsRoute.getPredicates())
                .anySatisfy(predicate -> assertThat(predicate.getArgs().values())
                        .anyMatch(value -> value.contains("/api/fares/**") && !value.contains("baggage-policies")));
    }

    @Test
    void paymentsRouteShouldPreserveApiPaymentsPrefix() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertThat(routes).isNotNull();

        RouteDefinition paymentsRoute = routes.stream()
                .filter(route -> "payments".equals(route.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(paymentsRoute.getUri().toString()).isEqualTo("http://localhost:8084");
        assertThat(paymentsRoute.getPredicates())
                .anySatisfy(predicate -> assertThat(predicate.getArgs().values()).contains("/api/payments/**"));
        assertThat(paymentsRoute.getFilters())
                .noneSatisfy(filter -> assertThat(filter.getName()).isEqualTo("RewritePath"));
    }
}
