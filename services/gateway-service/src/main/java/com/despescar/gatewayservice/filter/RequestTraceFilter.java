package com.despescar.gatewayservice.filter;

import com.despescar.gatewayservice.util.GatewayRequestContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class RequestTraceFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestTraceFilter.class);

    private final MeterRegistry meterRegistry;

    public RequestTraceFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String headerRequestId = exchange.getRequest().getHeaders().getFirst(GatewayRequestContext.REQUEST_ID_HEADER);
        final String requestId = (headerRequestId == null || headerRequestId.isBlank())
                ? UUID.randomUUID().toString()
                : headerRequestId;

        exchange.getAttributes().put(GatewayRequestContext.REQUEST_ID_ATTRIBUTE, requestId);
        long startNs = System.nanoTime();
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getPath().value();

        ServerWebExchange tracedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(GatewayRequestContext.REQUEST_ID_HEADER, requestId)))
                .build();

        tracedExchange.getResponse().beforeCommit(() -> {
            tracedExchange.getResponse().getHeaders().set(GatewayRequestContext.REQUEST_ID_HEADER, requestId);
            return Mono.empty();
        });

        return chain.filter(tracedExchange).doFinally(signalType -> {
            long durationNs = System.nanoTime() - startNs;
            HttpStatusCode statusCode = tracedExchange.getResponse().getStatusCode();
            String status = statusCode == null ? "500" : Integer.toString(statusCode.value());
            String routeId = resolveRouteId(tracedExchange);
            String methodTag = method == null ? "UNKNOWN" : method.name();

            Timer.builder("despescar.gateway.requests")
                    .tag("routeId", routeId)
                    .tag("method", methodTag)
                    .tag("status", status)
                    .register(meterRegistry)
                    .record(durationNs, TimeUnit.NANOSECONDS);

            LOGGER.info("requestId={} method={} path={} routeId={} status={} durationMs={}",
                    requestId, methodTag, path, routeId, status, durationNs / 1_000_000.0);
        });
    }

    private String resolveRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return "unresolved";
        }
        return route.getId();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
