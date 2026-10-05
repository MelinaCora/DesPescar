package com.despescar.reservationservice.client;

import com.despescar.reservationservice.dto.hotel.RetencionHotelRequest;
import com.despescar.reservationservice.dto.hotel.RetencionHotelResponse;
import com.despescar.reservationservice.exception.BookingException;
import java.time.Instant;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Cliente de la API interna de retenciones de hotel-service. Se autentica con el token interno de
 * servicio (inventory.sync-token), nunca con el JWT del usuario.
 */
@Component
@Slf4j
public class HotelClient {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestTemplate restTemplate;
    private final String hotelServiceUrl;
    private final String inventoryToken;

    public HotelClient(
            @Qualifier("hotelServiceRestTemplate") RestTemplate restTemplate,
            @Value("${hotel-service.url}") String hotelServiceUrl,
            @Value("${inventory.sync-token:}") String inventoryToken) {
        this.restTemplate = restTemplate;
        this.hotelServiceUrl = sanitizeBaseUrl(hotelServiceUrl);
        this.inventoryToken = inventoryToken;
        if (inventoryToken == null || inventoryToken.isBlank()) {
            log.warn("inventory.sync-token esta vacio: hotel-service va a rechazar con 401 todas las retenciones. "
                    + "Configurar INVENTORY_SERVICE_TOKEN.");
        }
    }

    /**
     * Si la llamada termina en timeout, la retencion pudo haberse creado igual: queda huerfana y vence sola
     * por expiraEn. Por eso no se reintenta a ciegas.
     */
    public RetencionHotelResponse crearRetencion(RetencionHotelRequest pedido) {
        return llamar(hotelServiceUrl + "/internal/retenciones", pedido, RetencionHotelResponse.class);
    }

    public RetencionHotelResponse confirmarRetencion(UUID retencionId, String nombreTitular) {
        if (nombreTitular == null || nombreTitular.isBlank()) {
            throw new BookingException("SOLICITUD_HOTEL_INVALIDA",
                    "El nombre del titular es obligatorio para confirmar la estadía.", HttpStatus.BAD_REQUEST);
        }
        return llamar(hotelServiceUrl + "/internal/retenciones/" + retencionId + "/confirmar",
                Map.of("nombreTitular", nombreTitular), RetencionHotelResponse.class);
    }

    /**
     * Cambia el vencimiento de una retención RETENIDA (CB1): la alarga hasta el plazo de un pago en
     * grupo o la devuelve a su vencimiento anterior. El instante viaja en ISO (UTC).
     */
    public RetencionHotelResponse cambiarVencimiento(UUID retencionId, Instant expiraEn) {
        return llamar(hotelServiceUrl + "/internal/retenciones/" + retencionId + "/vencimiento",
                Map.of("expiraEn", expiraEn.toString()), RetencionHotelResponse.class);
    }

    public void liberarRetencion(UUID retencionId) {
        llamar(hotelServiceUrl + "/internal/retenciones/" + retencionId + "/liberar", null, Void.class);
    }

    private <T> T llamar(String url, Object cuerpo, Class<T> tipo) {
        try {
            ResponseEntity<T> respuesta = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(cuerpo, headers()), tipo);
            if (tipo != Void.class && respuesta.getBody() == null) {
                throw new BookingException("HOTEL_SERVICE_EMPTY_RESPONSE",
                        "Hotel-Service devolvio una respuesta vacia.", HttpStatus.BAD_GATEWAY);
            }
            return respuesta.getBody();
        } catch (HttpClientErrorException ex) {
            throw traducir(ex);
        } catch (HttpServerErrorException ex) {
            throw new BookingException("HOTEL_SERVICE_SERVER_ERROR",
                    "Hotel-Service no pudo procesar el pedido.", HttpStatus.SERVICE_UNAVAILABLE);
        } catch (ResourceAccessException ex) {
            boolean timeout = isTimeout(ex);
            throw new BookingException(timeout ? "HOTEL_SERVICE_TIMEOUT" : "HOTEL_SERVICE_UNAVAILABLE",
                    "No fue posible comunicarse con Hotel-Service.",
                    timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE);
        } catch (RestClientException ex) {
            log.error("Error inesperado llamando a Hotel-Service", ex);
            throw new BookingException("HOTEL_SERVICE_ERROR",
                    "Se produjo un error al comunicarse con Hotel-Service.", HttpStatus.BAD_GATEWAY);
        }
    }

    private BookingException traducir(HttpClientErrorException ex) {
        String cuerpo = ex.getResponseBodyAsString();
        JsonNode json = leer(cuerpo);
        String mensaje = texto(json, "error");
        int status = ex.getStatusCode().value();
        if (status == 409) {
            if ("RETENCION_LIBERADA".equals(texto(json, "codigo"))) {
                return new BookingException("RETENCION_LIBERADA",
                        conDefecto(mensaje, "La retención ya fue liberada."), HttpStatus.CONFLICT);
            }
            return new BookingException("SIN_DISPONIBILIDAD_HOTEL",
                    conDefecto(mensaje, "No quedan habitaciones de ese tipo para esas fechas."), HttpStatus.CONFLICT);
        }
        if (status == 404) {
            return new BookingException("HOTEL_NO_ENCONTRADO",
                    conDefecto(mensaje, "No se encontró la habitación o la retención en el hotel."), HttpStatus.NOT_FOUND);
        }
        if (status == 400) {
            return new BookingException("SOLICITUD_HOTEL_INVALIDA",
                    conDefecto(mensaje, "Los datos de la estadía no son válidos."), HttpStatus.BAD_REQUEST);
        }
        if (status == 401 || status == 403) {
            log.error("Hotel-Service rechazo el token interno con estado {}: probablemente INVENTORY_SERVICE_TOKEN "
                    + "no coincide con el de hotel-service.", status);
        } else {
            log.error("Hotel-Service rechazo el pedido interno con estado {}", status);
        }
        return new BookingException("HOTEL_SERVICE_CLIENT_ERROR", "Hotel-Service rechazo el pedido.",
                HttpStatus.BAD_GATEWAY);
    }

    private static JsonNode leer(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(cuerpo);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String texto(JsonNode json, String campo) {
        if (json == null || !json.has(campo) || !json.get(campo).isString()) {
            return null;
        }
        return json.get(campo).asString();
    }

    private static String conDefecto(String mensaje, String porDefecto) {
        return mensaje == null || mensaje.isBlank() ? porDefecto : mensaje;
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (inventoryToken != null && !inventoryToken.isBlank()) {
            headers.set(INTERNAL_TOKEN_HEADER, inventoryToken);
        }
        return headers;
    }

    private boolean isTimeout(ResourceAccessException ex) {
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timed out");
    }

    private String sanitizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BookingException("HOTEL_SERVICE_URL_INVALIDA",
                    "La propiedad hotel-service.url es obligatoria.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
