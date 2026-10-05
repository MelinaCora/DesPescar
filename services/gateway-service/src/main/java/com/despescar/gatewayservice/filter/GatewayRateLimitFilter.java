package com.despescar.gatewayservice.filter;

import com.despescar.gatewayservice.util.GatewayResponseWriter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {

    private final int requestsPerMinute;
    private final int koiMessagesPerMinute;
    private final int grupoRequestsPerMinute;
    private final Set<String> trustedProxies;
    private final Cache<String, Bucket> buckets;
    private final Cache<String, Bucket> koiBuckets;
    private final Cache<String, Bucket> grupoBuckets;

    /** Para los tests existentes: grupos con 20 pedidos por minuto. */
    public GatewayRateLimitFilter(int requestsPerMinute, int koiMessagesPerMinute, String trustedProxies) {
        this(requestsPerMinute, koiMessagesPerMinute, 20, trustedProxies);
    }

    @Autowired
    public GatewayRateLimitFilter(
            @Value("${gateway.rate-limit.requests-per-minute:120}") int requestsPerMinute,
            @Value("${gateway.rate-limit.koi-messages-per-minute:10}") int koiMessagesPerMinute,
            @Value("${gateway.rate-limit.grupo-requests-per-minute:20}") int grupoRequestsPerMinute,
            @Value("${gateway.rate-limit.trusted-proxies:}") String trustedProxies) {
        this.requestsPerMinute = requestsPerMinute;
        this.koiMessagesPerMinute = koiMessagesPerMinute;
        this.grupoRequestsPerMinute = grupoRequestsPerMinute;
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.buckets = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofMinutes(30))
                .build();
        this.koiBuckets = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofMinutes(30))
                .build();
        this.grupoBuckets = Caffeine.newBuilder()
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

        // Cada POST a KOI es una llamada al modelo de lenguaje: balde propio, más chico (spec 3.6)
        if (method == HttpMethod.POST && isKoiPath(exchange.getRequest().getPath().value())) {
            Bucket koiBucket = koiBuckets.get(key, k -> newBucket(koiMessagesPerMinute));
            if (!koiBucket.tryConsume(1)) {
                exchange.getResponse().getHeaders().set("Retry-After", "60");
                return GatewayResponseWriter.writeError(
                        exchange,
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests",
                        "Demasiados mensajes a KOI. Espera un minuto y segui."
                );
            }
        }

        // Consultar y sumarse a un grupo de pago (D-b20): balde propio contra el barrido de enlaces
        if (isGrupoPath(exchange.getRequest().getPath().value())) {
            Bucket grupoBucket = grupoBuckets.get(key, k -> newBucket(grupoRequestsPerMinute));
            if (!grupoBucket.tryConsume(1)) {
                exchange.getResponse().getHeaders().set("Retry-After", "60");
                return GatewayResponseWriter.writeError(
                        exchange,
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests",
                        "Demasiados pedidos sobre pagos en grupo. Espera un minuto y volve a intentar."
                );
            }
        }

        Bucket bucket = buckets.get(key, k -> newBucket(requestsPerMinute));
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

    private Bucket newBucket(int perMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(perMinute)
                .refillGreedy(perMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private static boolean isKoiPath(String path) {
        return path.equals("/api/koi") || path.startsWith("/api/koi/");
    }

    // Se ignoran los parametros de matriz (;x=y) de cada segmento, como hace el ruteo
    private static boolean isGrupoPath(String path) {
        String limpio = path.replaceAll(";[^/]*", "");
        return limpio.equals("/api/bookings/grupos") || limpio.startsWith("/api/bookings/grupos/");
    }

    // X-Forwarded-For lo controla el cliente: solo se mira si la conexion viene de un proxy de confianza,
    // y entonces se toma la IP mas a la derecha que no sea otro proxy de confianza.
    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }
        String remoteIp = remote.getAddress().getHostAddress();
        if (!trustedProxies.contains(remoteIp)) {
            return remoteIp;
        }
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor == null || xForwardedFor.isBlank()) {
            return remoteIp;
        }
        String[] chain = xForwardedFor.split(",");
        for (int i = chain.length - 1; i >= 0; i--) {
            String ip = chain[i].trim();
            if (!ip.isEmpty() && !trustedProxies.contains(ip)) {
                return ip;
            }
        }
        return remoteIp;
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
