package com.despescar.reservationservice.client;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.despescar.reservationservice.dto.flight.response.FareLookupResponse;
import com.despescar.reservationservice.dto.flight.response.FlightLookupResponse;
import com.despescar.reservationservice.exception.BookingException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FlightClient {

    private final RestTemplate restTemplate;
    private final String flightServiceUrl;

    public FlightClient(
            @Qualifier("flightServiceRestTemplate") RestTemplate restTemplate,
            @Value("${flight-service.url}") String flightServiceUrl) {
        this.restTemplate = restTemplate;
        this.flightServiceUrl = sanitizeBaseUrl(flightServiceUrl);
    }

    public FlightLookupResponse getFlightByNumber(UUID flightId) {
        String targetUrl = flightServiceUrl + "/api/flights/" + flightId;

        try {
            ResponseEntity<FlightLookupResponse> response = restTemplate.exchange(
                    targetUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
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

    public FareLookupResponse getFareById(UUID fareId) {
        String targetUrl = flightServiceUrl + "/api/fares/" + fareId;

        try {
            ResponseEntity<FareLookupResponse> response = restTemplate.exchange(
                    targetUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    FareLookupResponse.class
            );

            if (response.getBody() == null) {
                throw new BookingException(
                        "TARIFA_VACIA",
                        "Flight-Service devolvio una tarifa vacia para " + fareId + ".",
                        HttpStatus.BAD_GATEWAY
                );
            }

            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new BookingException(
                    "TARIFA_NO_ENCONTRADA",
                    "La tarifa " + fareId + " no existe.",
                    HttpStatus.NOT_FOUND
            );
        } catch (HttpClientErrorException ex) {
            throw new BookingException(
                    "FARE_SERVICE_CLIENT_ERROR",
                    "Flight-Service rechazo la consulta de la tarifa " + fareId + ".",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (HttpServerErrorException ex) {
            throw new BookingException(
                    "FARE_SERVICE_SERVER_ERROR",
                    "Flight-Service no pudo procesar la consulta de la tarifa.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        } catch (ResourceAccessException ex) {
            HttpStatus status = isTimeout(ex) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
            String code = isTimeout(ex) ? "FARE_SERVICE_TIMEOUT" : "FARE_SERVICE_UNAVAILABLE";
            throw new BookingException(
                    code,
                    "No fue posible comunicarse con Flight-Service para consultar la tarifa.",
                    status
            );
        } catch (RestClientException ex) {
            log.error("Error inesperado consultando Flight-Service por tarifa", ex);
            throw new BookingException(
                    "FARE_SERVICE_ERROR",
                    "Se produjo un error al consultar informacion de la tarifa.",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    /**
     * Ajusta asientos disponibles en flight-service.
     * delta negativo para reservar, positivo para liberar.
     */
    public void adjustSeats(UUID flightId, int delta) { // Cambia String flightNumber por UUID flightId
        try {
            restTemplate.exchange(
                    flightServiceUrl + "/api/flights/number/{flightId}/seats?delta={delta}", // Quitamos el "/number/"
                    HttpMethod.PATCH,
                    new HttpEntity<>(new HttpHeaders()),
                    Void.class,
                    flightId, delta
            );
        } catch (HttpClientErrorException ex) {
            log.error("Flight-Service rechazo el ajuste de asientos para vuelo {}: {}", flightId, ex.getMessage());
            throw new BookingException(
                    "FLIGHT_SEATS_ADJUST_ERROR",
                    "No se pudo actualizar la disponibilidad del vuelo " + flightId + ".",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (Exception ex) {
            log.error("Error ajustando asientos del vuelo {}", flightId, ex);
            throw new BookingException(
                    "FLIGHT_SEATS_ADJUST_ERROR",
                    "No se pudo actualizar la disponibilidad del vuelo " + flightId + ".",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String token = request.getHeader("Authorization");
            if (token != null) {
                headers.set("Authorization", token);
            }
        }

        // Asegurar que las llamadas internas desde hilos de procesamiento (como pagos) lleven el token de servicio
        headers.set("X-Internal-Service-Token", "despescar-secreto-interno-9876"); // O lee esto desde @Value(${reservation-service.sync-token}) si lo configuras en las propiedades del reservation-service

        return headers;
    }

    private boolean isTimeout(ResourceAccessException ex) {
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timed out");
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
