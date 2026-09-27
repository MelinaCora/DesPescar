package com.despescar.reservationservice.client;

import com.despescar.reservationservice.dto.flight.response.FlightLookupResponse;
import com.despescar.reservationservice.exception.BookingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
@Slf4j
public class FlightClient {

    private final RestTemplate restTemplate;
    private final String flightServiceUrl;

    public FlightClient(
            @Qualifier("flightServiceRestTemplate") RestTemplate restTemplate,
            @Value("${flight-service.url}") String flightServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.flightServiceUrl = sanitizeBaseUrl(flightServiceUrl);
    }

    public FlightLookupResponse getFlightByNumber(UUID flightId) {
        String targetUrl = flightServiceUrl + "/api/flights/" + flightId;
        log.info("🔍 Intentando conectar con Flight-Service en la URL: {}", targetUrl); // <-- AÑADE ESTO

        try {
            HttpHeaders headers = new HttpHeaders();
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String token = request.getHeader("Authorization");
                if (token != null) {
                    headers.set("Authorization", token);
                }
            }

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<FlightLookupResponse> response = restTemplate.exchange(
                    targetUrl, // Usamos la variable directa para probar
                    HttpMethod.GET,
                    requestEntity,
                    FlightLookupResponse.class
            );

            if (response.getBody() == null) {
                throw new BookingException(
                        "FLIGHT_SERVICE_EMPTY_RESPONSE",
                        "Flight-Service devolvio una respuesta vacia al consultar el vuelo " + flightId + ".",
                        HttpStatus.BAD_GATEWAY
                );
            }

            return response.getBody();

        } catch (HttpClientErrorException.NotFound ex) {
            throw new BookingException(
                    "VUELO_NO_ENCONTRADO",
                    "El vuelo " + flightId + " no existe.",
                    HttpStatus.NOT_FOUND
            );
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new BookingException(
                    "SOLICITUD_VUELO_INVALIDA",
                    "La consulta del vuelo " + flightId + " es invalida.",
                    HttpStatus.BAD_REQUEST
            );
        } catch (HttpClientErrorException ex) {
            throw new BookingException(
                    "FLIGHT_SERVICE_CLIENT_ERROR",
                    "Flight-Service rechazo la consulta del vuelo " + flightId + ".",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (HttpServerErrorException ex) {
            throw new BookingException(
                    "FLIGHT_SERVICE_SERVER_ERROR",
                    "Flight-Service no pudo procesar la consulta del vuelo.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        } catch (ResourceAccessException ex) {
            HttpStatus status = isTimeout(ex) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
            String code = isTimeout(ex) ? "FLIGHT_SERVICE_TIMEOUT" : "FLIGHT_SERVICE_UNAVAILABLE";
            throw new BookingException(
                    code,
                    "No fue posible comunicarse con Flight-Service.",
                    status
            );
        } catch (RestClientException ex) {
            log.error("Error inesperado consultando Flight-Service", ex);
            throw new BookingException(
                    "FLIGHT_SERVICE_ERROR",
                    "Se produjo un error al consultar informacion del vuelo.",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private boolean isTimeout(ResourceAccessException ex) {
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timed out");
    }

    /**
     * Ajusta asientos disponibles en flight-service.
     * delta negativo para reservar, positivo para liberar.
     */
    public void adjustSeats(String flightNumber, int delta) {
        try {
            restTemplate.exchange(
                    flightServiceUrl + "/api/flights/number/{flightNumber}/seats?delta={delta}",
                    HttpMethod.PATCH,
                    new HttpEntity<>(new HttpHeaders()),
                    Void.class,
                    flightNumber, delta
            );
        } catch (HttpClientErrorException ex) {
            log.error("Flight-Service rechazo el ajuste de asientos para vuelo {}: {}", flightNumber, ex.getMessage());
            throw new BookingException(
                    "FLIGHT_SEATS_ADJUST_ERROR",
                    "No se pudo actualizar la disponibilidad del vuelo " + flightNumber + ".",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (Exception ex) {
            log.error("Error ajustando asientos del vuelo {}", flightNumber, ex);
            throw new BookingException(
                    "FLIGHT_SEATS_ADJUST_ERROR",
                    "No se pudo actualizar la disponibilidad del vuelo " + flightNumber + ".",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private String sanitizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BookingException(
                    "FLIGHT_SERVICE_URL_INVALIDA",
                    "La propiedad flight-service.url es obligatoria.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl;
    }
}

