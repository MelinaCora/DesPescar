package com.despescar.reservationservice.client;

import com.despescar.reservationservice.dto.flight.response.FlightLookupResponse;
import com.despescar.reservationservice.exception.BookingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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

    public FlightLookupResponse getFlightByNumber(String flightNumber) {
        try {
            FlightLookupResponse response = restTemplate.getForObject(
                    flightServiceUrl + "/api/flights/number/{flightNumber}",
                    FlightLookupResponse.class,
                    flightNumber
            );

            if (response == null) {
                throw new BookingException(
                        "FLIGHT_SERVICE_EMPTY_RESPONSE",
                        "Flight-Service devolvio una respuesta vacia al consultar el vuelo " + flightNumber + ".",
                        HttpStatus.BAD_GATEWAY
                );
            }

            return response;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new BookingException(
                    "VUELO_NO_ENCONTRADO",
                    "El vuelo " + flightNumber + " no existe.",
                    HttpStatus.NOT_FOUND
            );
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new BookingException(
                    "SOLICITUD_VUELO_INVALIDA",
                    "La consulta del vuelo " + flightNumber + " es invalida.",
                    HttpStatus.BAD_REQUEST
            );
        } catch (HttpClientErrorException ex) {
            throw new BookingException(
                    "FLIGHT_SERVICE_CLIENT_ERROR",
                    "Flight-Service rechazo la consulta del vuelo " + flightNumber + ".",
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

