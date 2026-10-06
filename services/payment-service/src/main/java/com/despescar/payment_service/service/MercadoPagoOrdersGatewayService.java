package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.exception.ProveedorPagoException;

/**
 * Mercado Pago Checkout API via Orders (payments.provider=mercadopago_orders): la tarjeta se
 * tokeniza en el front con MercadoPago.js y el back crea la orden con POST /v1/orders. El
 * transactionId de cada pago es el id de la orden (ORD...), y con el se consultan y reembolsan.
 *
 * <p>Ni el token ni las respuestas crudas de Mercado Pago (que repiten el token) se registran ni
 * viajan en las excepciones: los mensajes solo llevan el codigo HTTP y el codigo de error.
 */
@Service
@ConditionalOnProperty(name = "payments.provider", havingValue = "mercadopago_orders")
public class MercadoPagoOrdersGatewayService implements PaymentGatewayService, MercadoPagoOrdenGateway {

    static final String URL_BASE_POR_DEFECTO = "https://api.mercadopago.com";
    static final String RUTA_CHECKOUT = "/pago/mercadopago?pago=";
    static final String DESCRIPCION_RESUMEN = "DESPESCAR";
    private static final String MONEDA = "ARS";
    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final String publicKey;

    @Autowired
    public MercadoPagoOrdersGatewayService(
            @Value("${mercadopago.access-token:}") String accessToken,
            @Value("${mercadopago.public-key:}") String publicKey,
            @Value("${mercadopago.orders.base-url:" + URL_BASE_POR_DEFECTO + "}") String baseUrl,
            @Value("${mercadopago.connection-timeout-ms:5000}") int connectionTimeoutMs,
            @Value("${mercadopago.socket-timeout-ms:15000}") int socketTimeoutMs) {
        this(accessToken, publicKey, baseUrl, RestClient.builder().requestFactory(
                fabricaConTimeouts(connectionTimeoutMs, socketTimeoutMs)));
    }

    /** Para pruebas: permite atar el builder a MockRestServiceServer. */
    MercadoPagoOrdersGatewayService(String accessToken, String publicKey, String baseUrl, RestClient.Builder builder) {
        if (!hasText(accessToken) || !hasText(publicKey)) {
            throw new IllegalStateException(
                    "PAYMENT_PROVIDER=mercadopago_orders necesita MERCADOPAGO_ACCESS_TOKEN y MERCADOPAGO_PUBLIC_KEY"
                            + " (credenciales de prueba de Mercado Pago).");
        }
        this.publicKey = publicKey;
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private static SimpleClientHttpRequestFactory fabricaConTimeouts(int connectionTimeoutMs, int socketTimeoutMs) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(connectionTimeoutMs));
        fabrica.setReadTimeout(Duration.ofMillis(socketTimeoutMs));
        return fabrica;
    }

    // ----- PaymentGatewayService -----

    /** El "checkout" es nuestra propia pagina: el cobro recien se pide con el token (POST /{id}/orden). */
    @Override
    public PaymentCheckoutResponse createCheckout(String paymentId, BigDecimal amount, String currency) {
        if (!MONEDA.equalsIgnoreCase(currency)) {
            throw new ProveedorPagoException("Solo se cobra en pesos argentinos (ARS).", null);
        }
        if (amount == null || amount.signum() <= 0) {
            throw new ProveedorPagoException("El pago no tiene un importe para cobrar.", null);
        }
        return PaymentCheckoutResponse.builder()
                .preferenceId(null)
                .checkoutUrl(RUTA_CHECKOUT + paymentId)
                .message("Checkout de Mercado Pago (Orders) listo.")
                .build();
    }

    @Override
    public PaymentGatewayResponse getPaymentStatus(String transactionId) {
        return consultarOrden(transactionId).toGatewayResponse();
    }

    /**
     * Reembolso de la orden: total si el monto cubre lo cobrado, parcial si es menor. Nunca lanza:
     * si no sale devuelve approved=false y quien llama lo deja como reembolso manual pendiente.
     */
    @Override
    public RefundGatewayResponse refund(String transactionId, BigDecimal amount) {
        if (!esIdDeOrden(transactionId)) {
            return rechazado("Refund rejected: invalid Mercado Pago order id.");
        }
        if (amount == null || amount.signum() <= 0) {
            return rechazado("Refund rejected: invalid amount.");
        }
        BigDecimal monto = amount.setScale(2, RoundingMode.HALF_UP);
        try {
            OrdenMercadoPago orden = consultarOrden(transactionId);
            BigDecimal cobrado = orden.totalPaidAmount() != null && orden.totalPaidAmount().signum() > 0
                    ? orden.totalPaidAmount() : orden.totalAmount();
            boolean total = cobrado == null || monto.compareTo(cobrado) >= 0;

            Map<String, Object> cuerpo = null;
            if (!total) {
                if (orden.transaccionId() == null) {
                    return rechazado("Refund rejected: the order has no transaction to refund partially.");
                }
                cuerpo = Map.of("transactions", List.of(Map.of(
                        "id", orden.transaccionId(), "amount", montoComoTexto(monto))));
            }
            String clave = "despescar-reembolso-" + transactionId + "-" + monto.unscaledValue();
            Map<String, Object> cuerpoFinal = cuerpo;
            Respuesta respuesta = enviar(() -> {
                RestClient.RequestBodySpec req = restClient.post()
                        .uri("/v1/orders/{id}/refund", transactionId)
                        .header("X-Idempotency-Key", clave);
                return cuerpoFinal == null ? req : req.body(cuerpoFinal);
            });
            if (respuesta.codigo() >= 300) {
                return rechazado("Mercado Pago rechazo el reembolso (HTTP " + respuesta.codigo()
                        + (respuesta.error() != null ? ", " + respuesta.error() : "") + ").");
            }
            String estado = texto(respuesta.cuerpo(), "status");
            String detalle = texto(respuesta.cuerpo(), "status_detail");
            boolean ok = "refunded".equalsIgnoreCase(estado) || "partially_refunded".equalsIgnoreCase(detalle)
                    || "refunded".equalsIgnoreCase(detalle);
            if (!ok) {
                return rechazado("Mercado Pago informo la orden en estado " + estado + "/" + detalle + ".");
            }
            return RefundGatewayResponse.builder()
                    .approved(true)
                    .refundTransactionId(idDelReembolso(respuesta.cuerpo(), transactionId))
                    .message(total ? "Reembolso total aprobado por Mercado Pago."
                            : "Reembolso parcial aprobado por Mercado Pago.")
                    .build();
        } catch (ProveedorPagoException ex) {
            return rechazado(ex.getMessage());
        } catch (RuntimeException ex) {
            return rechazado("No se pudo pedir el reembolso a Mercado Pago (" + ex.getClass().getSimpleName() + ").");
        }
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MERCADO_PAGO_ORDERS;
    }

    // ----- MercadoPagoOrdenGateway -----

    @Override
    public OrdenMercadoPago crearOrden(CrearOrden pedido) {
        BigDecimal monto = pedido.amount().setScale(2, RoundingMode.HALF_UP);
        String montoTexto = montoComoTexto(monto);

        Map<String, Object> metodo = new LinkedHashMap<>();
        metodo.put("id", pedido.paymentMethodId());
        metodo.put("type", pedido.paymentTypeId());
        metodo.put("token", pedido.token());
        metodo.put("installments", pedido.installments());
        metodo.put("statement_descriptor", DESCRIPCION_RESUMEN);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("type", "online");
        cuerpo.put("processing_mode", "automatic");
        cuerpo.put("total_amount", montoTexto);
        cuerpo.put("external_reference", pedido.paymentId());
        if (hasText(pedido.payerEmail())) {
            cuerpo.put("payer", Map.of("email", pedido.payerEmail()));
        }
        cuerpo.put("transactions", Map.of("payments", List.of(
                Map.of("amount", montoTexto, "payment_method", metodo))));

        String clave = claveDeOrden(pedido.paymentId(), pedido.token());
        Respuesta respuesta = enviar(() -> restClient.post()
                .uri("/v1/orders")
                .header("X-Idempotency-Key", clave)
                .body(cuerpo));

        // 402: la orden se creo pero la transaccion fallo (tarjeta rechazada); el cuerpo trae la orden.
        if (respuesta.codigo() >= 300 && respuesta.codigo() != 402) {
            throw new ProveedorPagoException("Mercado Pago no acepto la orden (HTTP " + respuesta.codigo()
                    + (respuesta.error() != null ? ", " + respuesta.error() : "") + ").", null);
        }
        OrdenMercadoPago orden = leerOrden(respuesta.cuerpo());
        if (orden.id() == null) {
            throw new ProveedorPagoException("Mercado Pago no devolvio el id de la orden.", null);
        }
        return orden;
    }

    @Override
    public OrdenMercadoPago consultarOrden(String ordenId) {
        if (!esIdDeOrden(ordenId)) {
            throw new ProveedorPagoException("El id de la orden de Mercado Pago no es valido.", null);
        }
        Respuesta respuesta = enviar(() -> restClient.get().uri("/v1/orders/{id}", ordenId));
        if (respuesta.codigo() >= 300) {
            throw new ProveedorPagoException("Mercado Pago no devolvio la orden " + ordenId + " (HTTP "
                    + respuesta.codigo() + ").", null);
        }
        return leerOrden(respuesta.cuerpo());
    }

    @Override
    public String publicKey() {
        return publicKey;
    }

    // ----- HTTP -----

    private record Respuesta(int codigo, Map<String, Object> cuerpo, String error) {
    }

    /**
     * Ejecuta la llamada y devuelve codigo + cuerpo sin lanzar por 4xx/5xx. Las excepciones de red
     * se envuelven sin su causa: el mensaje original puede repetir el cuerpo (y el token).
     */
    private Respuesta enviar(Supplier<RestClient.RequestHeadersSpec<?>> llamada) {
        try {
            return llamada.get()
                    .exchange((request, response) -> {
                        int codigo = response.getStatusCode().value();
                        Map<String, Object> cuerpo;
                        try {
                            cuerpo = response.bodyTo(MAPA);
                        } catch (RuntimeException ex) {
                            cuerpo = null;
                        }
                        return new Respuesta(codigo, cuerpo == null ? Map.of() : cuerpo, codigoDeError(cuerpo));
                    });
        } catch (RestClientException ex) {
            throw new ProveedorPagoException(
                    "No se pudo hablar con Mercado Pago (" + ex.getClass().getSimpleName() + ").", null);
        }
    }

    /** Primer codigo de error del cuerpo ({"errors":[{"code":...}]}) o el campo "error"/"code". */
    private static String codigoDeError(Map<String, Object> cuerpo) {
        if (cuerpo == null) {
            return null;
        }
        Object errores = cuerpo.get("errors");
        if (errores instanceof List<?> lista && !lista.isEmpty() && lista.get(0) instanceof Map<?, ?> primero) {
            Object code = primero.get("code");
            if (code != null) {
                return code.toString();
            }
        }
        Object error = cuerpo.get("error") != null ? cuerpo.get("error") : cuerpo.get("code");
        return error == null ? null : error.toString();
    }

    /**
     * Una orden que Mercado Pago crea pero cuya transaccion falla (tarjeta rechazada) llega como HTTP 402
     * con {"errors":[...],"data":{<la orden>}}: la orden va anidada en "data", no en el nivel superior.
     */
    @SuppressWarnings("unchecked")
    static OrdenMercadoPago leerOrden(Map<String, Object> respuesta) {
        Map<String, Object> cuerpo = respuesta.get("id") == null && respuesta.get("data") instanceof Map<?, ?> data
                ? (Map<String, Object>) data : respuesta;
        Map<String, Object> pago = Map.of();
        Object transacciones = cuerpo.get("transactions");
        if (transacciones instanceof Map<?, ?> t && t.get("payments") instanceof List<?> pagos && !pagos.isEmpty()
                && pagos.get(0) instanceof Map<?, ?> primero) {
            pago = (Map<String, Object>) primero;
        }
        Map<String, Object> metodo = pago.get("payment_method") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        return new OrdenMercadoPago(
                texto(cuerpo, "id"),
                texto(cuerpo, "status"),
                texto(cuerpo, "status_detail"),
                texto(cuerpo, "external_reference"),
                monto(cuerpo, "total_amount"),
                monto(cuerpo, "total_paid_amount"),
                texto(pago, "id"),
                texto(pago, "status"),
                texto(pago, "status_detail"),
                texto(metodo, "type"),
                texto(metodo, "id"));
    }

    private static String idDelReembolso(Map<String, Object> cuerpo, String ordenId) {
        Object transacciones = cuerpo.get("transactions");
        if (transacciones instanceof Map<?, ?> t && t.get("refunds") instanceof List<?> reembolsos
                && !reembolsos.isEmpty() && reembolsos.get(reembolsos.size() - 1) instanceof Map<?, ?> ultimo
                && ultimo.get("id") != null) {
            return ultimo.get("id").toString();
        }
        return ordenId;
    }

    private static String texto(Map<String, Object> mapa, String clave) {
        Object valor = mapa == null ? null : mapa.get(clave);
        return valor == null ? null : valor.toString();
    }

    private static BigDecimal monto(Map<String, Object> mapa, String clave) {
        String valor = texto(mapa, clave);
        try {
            return valor == null || valor.isBlank() ? null : new BigDecimal(valor).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static String montoComoTexto(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Fija por pago y token: reintentar el mismo cobro no crea dos ordenes. No contiene el token. */
    static String claveDeOrden(String paymentId, String token) {
        return "despescar-orden-" + paymentId + "-" + huella(token);
    }

    private static String huella(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static boolean esIdDeOrden(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,64}");
    }

    private static RefundGatewayResponse rechazado(String mensaje) {
        return RefundGatewayResponse.builder().approved(false).refundTransactionId(null).message(mensaje).build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
