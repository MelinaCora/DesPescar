package com.despescar.payment_service.client;

import java.math.BigDecimal;
import java.util.Optional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.despescar.payment_service.client.dto.ConfirmacionReservaResponse;
import com.despescar.payment_service.client.dto.ParteReservaResponse;
import com.despescar.payment_service.client.dto.ProcessPaymentRequest;
import com.despescar.payment_service.client.dto.ReservationResponse;
import com.despescar.payment_service.exception.ReservationClientException;

@Component
public class ReservationClient {

    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private static final Pattern CODIGO = Pattern.compile("\"codigo\"\\s*:\\s*\"([A-Z_]+)\"");

    private final RestTemplate restTemplate;
    private final RestTemplate confirmacionRestTemplate;
    private final String reservationServiceUrl;
    private final String syncToken;

    @Autowired
    public ReservationClient(
            @Qualifier("reservationServiceRestTemplate") RestTemplate reservationServiceRestTemplate,
            @Qualifier("reservationServiceConfirmacionRestTemplate") RestTemplate confirmacionRestTemplate,
            @Value("${reservation-service.url}") String reservationServiceUrl,
            @Value("${reservation-service.sync-token:}") String syncToken) {

        this.restTemplate = reservationServiceRestTemplate;
        this.confirmacionRestTemplate = confirmacionRestTemplate;
        this.reservationServiceUrl = sanitizeBaseUrl(reservationServiceUrl);
        this.syncToken = syncToken;
    }

    public ReservationClient(RestTemplate restTemplate, String reservationServiceUrl, String syncToken) {
        this(restTemplate, restTemplate, reservationServiceUrl, syncToken);
    }

    public ReservationResponse getReservation(Long reservationId) {
        try {
            ResponseEntity<ReservationResponse> response = restTemplate.exchange(
                    reservationServiceUrl + "/api/bookings/internal/{reservationId}",
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    ReservationResponse.class,
                    reservationId
            );

            if (response.getBody() == null) {
                throw new ReservationClientException("Reservation-Service devolvio una reserva vacia.");
            }

            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ReservationClientException("La reserva " + reservationId + " no existe.");
        } catch (HttpClientErrorException ex) {
            throw new ReservationClientException("Reservation-Service rechazo la consulta de la reserva.", ex);
        } catch (HttpServerErrorException ex) {
            throw new ReservationClientException("Reservation-Service no pudo procesar la consulta de la reserva.", ex);
        } catch (ResourceAccessException ex) {
            HttpStatus status = isTimeout(ex) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
            throw new ReservationClientException("No fue posible comunicarse con Reservation-Service. Estado sugerido: " + status.value(), ex);
        } catch (RestClientException ex) {
            throw new ReservationClientException("Se produjo un error al consultar la reserva.", ex);
        }
    }

    /**
     * Una parte de un pago en grupo (contrato CB3). Vacío si reservation-service responde 404 (la
     * reserva no tiene grupo o el grupo no tiene esa parte). Cualquier otra falla es
     * ReservationClientException (502 para el cliente): no se cobra sin saber la parte.
     */
    public Optional<ParteReservaResponse> getParte(Long reservationId, int numero) {
        try {
            ResponseEntity<ParteReservaResponse> response = restTemplate.exchange(
                    reservationServiceUrl + "/api/bookings/internal/{reservationId}/partes/{numero}",
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    ParteReservaResponse.class,
                    reservationId, numero
            );
            if (response.getBody() == null) {
                throw new ReservationClientException("Reservation-Service devolvio una parte vacia.");
            }
            return Optional.of(response.getBody());
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (HttpClientErrorException ex) {
            throw new ReservationClientException("Reservation-Service rechazo la consulta de la parte.", ex);
        } catch (HttpServerErrorException ex) {
            throw new ReservationClientException("Reservation-Service no pudo procesar la consulta de la parte.", ex);
        } catch (ResourceAccessException ex) {
            throw new ReservationClientException("No fue posible comunicarse con Reservation-Service.", ex);
        } catch (RestClientException ex) {
            throw new ReservationClientException("Se produjo un error al consultar la parte.", ex);
        }
    }

    /**
     * Avisa a reservation-service que el pago se cobro (contrato C3) y devuelve si la reserva quedo
     * CONFIRMADA. Un 400 o 404 se devuelve como RECHAZADA (hay que reembolsar). Caidas, 5xx y
     * otros 4xx lanzan ReservationClientException: no se sabe el resultado y se reintenta.
     */
    public ConfirmacionReservaResponse confirmarPago(
            Long reservationId,
            Long pagadorId,
            String tokenPago,
            BigDecimal monto) {

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .pagadorId(pagadorId)
                .tokenPago(tokenPago)
                .monto(monto)
                .build();

        try {
            ResponseEntity<ConfirmacionReservaResponse> response = confirmacionRestTemplate.exchange(
                    reservationServiceUrl + "/api/bookings/internal/{reservationId}/payment-confirmed",
                    HttpMethod.POST,
                    new HttpEntity<>(request, buildHeaders()),
                    ConfirmacionReservaResponse.class,
                    reservationId
            );

            ConfirmacionReservaResponse body = response.getBody();
            if (body == null || body.estado() == null) {
                throw new ReservationClientException("Reservation-Service no informo el resultado de la confirmacion.");
            }
            return body;
        } catch (HttpClientErrorException ex) {
            // Solo los codigos de reservation-service cuentan como rechazo (se reembolsa); cualquier otra
            // respuesta (otro 404 de una ruta equivocada, 401, 403, 409) es ambigua y no se reembolsa.
            String codigo = codigoDe(ex);
            if (ex.getStatusCode().value() == 404 && "RESERVA_NO_ENCONTRADA".equals(codigo)) {
                return ConfirmacionReservaResponse.rechazada(
                        "RESERVA_NO_ENCONTRADA", "La reserva " + reservationId + " no existe.");
            }
            if (ex.getStatusCode().value() == 400 && ("PAGADOR_INVALIDO".equals(codigo) || "VALIDACION".equals(codigo))) {
                return ConfirmacionReservaResponse.rechazada("PEDIDO_INVALIDO", ex.getResponseBodyAsString());
            }
            throw new ReservationClientException(
                    "Reservation-Service rechazo la confirmacion del pago (HTTP " + ex.getStatusCode().value() + ").", ex);
        } catch (HttpServerErrorException ex) {
            throw new ReservationClientException("Reservation-Service no pudo confirmar el pago.", ex);
        } catch (ResourceAccessException ex) {
            throw new ReservationClientException("No fue posible confirmar el pago con Reservation-Service.", ex);
        } catch (RestClientException ex) {
            throw new ReservationClientException("Se produjo un error al confirmar el pago.", ex);
        }
    }

    /**
     * Avisa que se cobró una parte de un pago en grupo (contrato CB3). PARTE_PAGADA y CONFIRMADA son
     * éxito; CANCELADA y RECHAZADA se reembolsan. Un 404 PARTE_NO_ENCONTRADA o un 400 VALIDACION se
     * devuelven como RECHAZADA; caídas, 5xx y cualquier otra respuesta lanzan ReservationClientException
     * (no se sabe el resultado y se reintenta). Usa el RestTemplate de confirmación (15 s): la última
     * parte confirma estadías y asientos.
     */
    public ConfirmacionReservaResponse confirmarPagoParte(
            Long reservationId, int numero, Long pagadorId, String tokenPago, BigDecimal monto) {

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .pagadorId(pagadorId)
                .tokenPago(tokenPago)
                .monto(monto)
                .build();

        try {
            ResponseEntity<ConfirmacionReservaResponse> response = confirmacionRestTemplate.exchange(
                    reservationServiceUrl + "/api/bookings/internal/{reservationId}/partes/{numero}/pago-confirmado",
                    HttpMethod.POST,
                    new HttpEntity<>(request, buildHeaders()),
                    ConfirmacionReservaResponse.class,
                    reservationId, numero
            );
            ConfirmacionReservaResponse body = response.getBody();
            if (body == null || body.estado() == null) {
                throw new ReservationClientException("Reservation-Service no informo el resultado de la parte.");
            }
            return body;
        } catch (HttpClientErrorException ex) {
            String codigo = codigoDe(ex);
            if (ex.getStatusCode().value() == 404 && "PARTE_NO_ENCONTRADA".equals(codigo)) {
                return ConfirmacionReservaResponse.rechazada(
                        "PARTE_NO_ENCONTRADA", "La parte " + numero + " de la reserva " + reservationId + " no existe.");
            }
            if (ex.getStatusCode().value() == 400 && "VALIDACION".equals(codigo)) {
                return ConfirmacionReservaResponse.rechazada("PEDIDO_INVALIDO", ex.getResponseBodyAsString());
            }
            throw new ReservationClientException(
                    "Reservation-Service rechazo la confirmacion de la parte (HTTP " + ex.getStatusCode().value() + ").", ex);
        } catch (HttpServerErrorException ex) {
            throw new ReservationClientException("Reservation-Service no pudo confirmar la parte.", ex);
        } catch (ResourceAccessException ex) {
            throw new ReservationClientException("No fue posible confirmar la parte con Reservation-Service.", ex);
        } catch (RestClientException ex) {
            throw new ReservationClientException("Se produjo un error al confirmar la parte.", ex);
        }
    }

    private static String codigoDe(HttpClientErrorException ex) {
        Matcher m = CODIGO.matcher(ex.getResponseBodyAsString());
        return m.find() ? m.group(1) : null;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (syncToken != null && !syncToken.isBlank()) {
            headers.set(INTERNAL_SERVICE_TOKEN_HEADER, syncToken);
        }
        return headers;
    }

    private boolean isTimeout(ResourceAccessException ex) {
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timed out");
    }

    private String sanitizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("La propiedad reservation-service.url es obligatoria.");
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
