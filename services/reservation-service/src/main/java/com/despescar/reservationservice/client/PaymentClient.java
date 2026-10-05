package com.despescar.reservationservice.client;

import com.despescar.reservationservice.dto.pagos.ReembolsoGrupoRequest;
import com.despescar.reservationservice.dto.pagos.ReembolsoGrupoResponse;
import com.despescar.reservationservice.dto.pagos.ReembolsoReservaRequest;
import com.despescar.reservationservice.dto.pagos.ReembolsoReservaResponse;
import java.math.BigDecimal;
import com.despescar.reservationservice.exception.BookingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Cliente de la API interna de payment-service (CB5). Se autentica con su propio token interno
 * (payment-service.sync-token), distinto del que usa payment-service para llamar a este servicio.
 */
@Component
@Slf4j
public class PaymentClient {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestTemplate restTemplate;
    private final String paymentServiceUrl;
    private final String syncToken;

    public PaymentClient(
            @Qualifier("paymentServiceRestTemplate") RestTemplate restTemplate,
            @Value("${payment-service.url}") String paymentServiceUrl,
            @Value("${payment-service.sync-token:}") String syncToken) {
        this.restTemplate = restTemplate;
        this.paymentServiceUrl = paymentServiceUrl.endsWith("/")
                ? paymentServiceUrl.substring(0, paymentServiceUrl.length() - 1) : paymentServiceUrl;
        this.syncToken = syncToken;
        if (sinToken()) {
            log.error("payment-service.sync-token esta vacio: payment-service va a rechazar con 401 los reembolsos "
                    + "de pagos en grupo. Configurar PAYMENT_SERVICE_SYNC_TOKEN.");
        }
    }

    /**
     * Pide que se reembolsen todas las partes pagadas de la reserva y se cancelen las pendientes.
     * Idempotente del lado de payment-service. Cualquier falla lanza BookingException: el que llama
     * deja la marca de reembolsos pendientes y reintenta.
     */
    public ReembolsoGrupoResponse reembolsarGrupo(Long reservaId, String motivo) {
        return pedir("/api/payments/internal/grupos/{reservaId}/reembolsos", new ReembolsoGrupoRequest(motivo),
                ReembolsoGrupoResponse.class, reservaId);
    }

    /**
     * Pide que se reembolse ese monto de la reserva que su dueño canceló, repartido entre sus pagos
     * aprobados. Idempotente del lado de payment-service. Cualquier falla lanza BookingException: el
     * que llama deja la marca de reembolso pendiente y reintenta.
     */
    public ReembolsoReservaResponse reembolsarReserva(Long reservaId, BigDecimal monto, String motivo) {
        return pedir("/api/payments/internal/reservas/{reservaId}/reembolso", new ReembolsoReservaRequest(monto, motivo),
                ReembolsoReservaResponse.class, reservaId);
    }

    private <T> T pedir(String ruta, Object cuerpo, Class<T> tipo, Long reservaId) {
        try {
            ResponseEntity<T> respuesta = restTemplate.exchange(
                    paymentServiceUrl + ruta,
                    HttpMethod.POST,
                    new HttpEntity<>(cuerpo, headers()),
                    tipo,
                    reservaId);
            if (respuesta.getBody() == null) {
                throw new BookingException("PAYMENT_SERVICE_EMPTY_RESPONSE",
                        "Payment-Service no informo el resultado de los reembolsos.", HttpStatus.BAD_GATEWAY);
            }
            return respuesta.getBody();
        } catch (HttpStatusCodeException ex) {
            avisarSiFaltaElToken(reservaId);
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403) {
                log.error("Payment-Service rechazo el token interno con estado {}: probablemente PAYMENT_SERVICE_SYNC_TOKEN "
                        + "no coincide entre los dos servicios.", status);
            }
            throw new BookingException("PAYMENT_SERVICE_ERROR",
                    "Payment-Service rechazo el pedido de reembolsos (HTTP " + status + ").", HttpStatus.BAD_GATEWAY);
        } catch (ResourceAccessException ex) {
            avisarSiFaltaElToken(reservaId);
            throw new BookingException("PAYMENT_SERVICE_UNAVAILABLE",
                    "No fue posible comunicarse con Payment-Service.", HttpStatus.SERVICE_UNAVAILABLE);
        } catch (RestClientException ex) {
            avisarSiFaltaElToken(reservaId);
            log.error("Error inesperado llamando a Payment-Service", ex);
            throw new BookingException("PAYMENT_SERVICE_ERROR",
                    "Se produjo un error al comunicarse con Payment-Service.", HttpStatus.BAD_GATEWAY);
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!sinToken()) {
            headers.set(INTERNAL_TOKEN_HEADER, syncToken);
        }
        return headers;
    }

    private boolean sinToken() {
        return syncToken == null || syncToken.isBlank();
    }

    /** Sin token los reembolsos no salen nunca: cada pedido fallido lo deja a la vista como error. */
    private void avisarSiFaltaElToken(Long reservaId) {
        if (sinToken()) {
            log.error("El pedido de reembolsos de la reserva {} fallo y payment-service.sync-token esta vacio: sin ese token "
                    + "payment-service rechaza todos los pedidos. Configurar PAYMENT_SERVICE_SYNC_TOKEN (el mismo valor "
                    + "en los dos servicios).", reservaId);
        }
    }
}
