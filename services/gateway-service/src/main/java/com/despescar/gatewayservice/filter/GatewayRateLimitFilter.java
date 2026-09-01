package com.despescar.gatewayservice.filter;

import com.despescar.gatewayservice.util.GatewayResponseWriter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {

    private final int requestsPerMinute;
    private final Cache<String, Bucket> buckets;

    public GatewayRateLimitFilter(@Value("${gateway.rate-limit.requests-per-minute:120}") int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofMinutes(30))
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == HttpMethod.OPTIONS || isBypassedPath(exchange)) {
            return chain.filter(exchange);
        }

        String key = resolveClientIp(exchange);
        Bucket bucket = buckets.get(key, this::newBucket);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            exchange.getResponse().getHeaders().set("Retry-After", "60");
            return GatewayResponseWriter.writeError(
                    exchange,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too Many Requests",
                    "Rate limit excedido. Reintenta en unos segundos."
            );
        }

        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    private Bucket newBucket(String key) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() == null || exchange.getRequest().getRemoteAddress().getAddress() == null) {
            return "unknown";
        }
        return Objects.requireNonNull(exchange.getRequest().getRemoteAddress().getAddress()).getHostAddress();
    }

    private boolean isBypassedPath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return path.startsWith("/api/auth")
                || path.startsWith("/actuator")
                || path.startsWith("/fallback");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
