package com.despescar.reservationservice.client;

import com.despescar.reservationservice.dto.hotel.response.HotelLookupResponse;
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

import java.util.UUID;

@Component
@Slf4j
public class HotelClient {

    private final RestTemplate restTemplate;
    private final String hotelServiceUrl;

    public HotelClient(
            @Qualifier("hotelServiceRestTemplate") RestTemplate restTemplate,
            @Value("${hotel-service.url}") String hotelServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.hotelServiceUrl = sanitizeBaseUrl(hotelServiceUrl);
    }

    public HotelLookupResponse getHotelById(UUID hotelId) {
        try {
            HotelLookupResponse response = restTemplate.getForObject(
                    hotelServiceUrl + "/hoteles/{id}",
                    HotelLookupResponse.class,
                    hotelId
            );

            if (response == null) {
                throw new BookingException(
                        "HOTEL_SERVICE_EMPTY_RESPONSE",
                        "Hotel-Service devolvio una respuesta vacia al consultar el hotel " + hotelId + ".",
                        HttpStatus.BAD_GATEWAY
                );
            }

            return response;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new BookingException(
                    "HOTEL_NO_ENCONTRADO",
                    "El hotel " + hotelId + " no existe.",
                    HttpStatus.NOT_FOUND
            );
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new BookingException(
                    "SOLICITUD_HOTEL_INVALIDA",
                    "La consulta del hotel " + hotelId + " es invalida.",
                    HttpStatus.BAD_REQUEST
            );
        } catch (HttpClientErrorException ex) {
            throw new BookingException(
                    "HOTEL_SERVICE_CLIENT_ERROR",
                    "Hotel-Service rechazo la consulta del hotel " + hotelId + ".",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (HttpServerErrorException ex) {
            throw new BookingException(
                    "HOTEL_SERVICE_SERVER_ERROR",
                    "Hotel-Service no pudo procesar la consulta del hotel.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        } catch (ResourceAccessException ex) {
            HttpStatus status = isTimeout(ex) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
            String code = isTimeout(ex) ? "HOTEL_SERVICE_TIMEOUT" : "HOTEL_SERVICE_UNAVAILABLE";
            throw new BookingException(
                    code,
                    "No fue posible comunicarse con Hotel-Service.",
                    status
            );
        } catch (RestClientException ex) {
            log.error("Error inesperado consultando Hotel-Service", ex);
            throw new BookingException(
                    "HOTEL_SERVICE_ERROR",
                    "Se produjo un error al consultar informacion del hotel.",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private boolean isTimeout(ResourceAccessException ex) {
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timed out");
    }

    /**
     * Ajusta habitaciones disponibles en hotel-service.
     * delta negativo para reservar, positivo para liberar.
     */
    public void adjustRooms(UUID hotelId, int delta) {
        try {
            restTemplate.exchange(
                    hotelServiceUrl + "/hoteles/{id}/rooms?delta={delta}",
                    HttpMethod.PATCH,
                    new HttpEntity<>(new HttpHeaders()),
                    Void.class,
                    hotelId, delta
            );
        } catch (HttpClientErrorException ex) {
            log.error("Hotel-Service rechazo el ajuste de habitaciones para hotel {}: {}", hotelId, ex.getMessage());
            throw new BookingException(
                    "HOTEL_ROOMS_ADJUST_ERROR",
                    "No se pudo actualizar la disponibilidad del hotel " + hotelId + ".",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (Exception ex) {
            log.error("Error ajustando habitaciones del hotel {}", hotelId, ex);
            throw new BookingException(
                    "HOTEL_ROOMS_ADJUST_ERROR",
                    "No se pudo actualizar la disponibilidad del hotel " + hotelId + ".",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private String sanitizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BookingException(
                    "HOTEL_SERVICE_URL_INVALIDA",
                    "La propiedad hotel-service.url es obligatoria.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl;
    }
}

