package com.despescar.reservationservice.client;

import com.despescar.reservationservice.dto.packagecatalog.response.PackageLookupResponse;
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
public class PackageClient {

    private final RestTemplate restTemplate;
    private final String packageServiceUrl;

    public PackageClient(
            @Qualifier("packageServiceRestTemplate") RestTemplate restTemplate,
            @Value("${package-service.url}") String packageServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.packageServiceUrl = sanitizeBaseUrl(packageServiceUrl);
    }

    public PackageLookupResponse getPackageById(Long packageId, String authorizationHeader) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            PackageLookupResponse response = restTemplate.exchange(
                    packageServiceUrl + "/api/packages/{id}",
                    HttpMethod.GET,
                    entity,
                    PackageLookupResponse.class,
                    packageId
            ).getBody();

            if (response == null) {
                throw new BookingException(
                        "PACKAGE_SERVICE_EMPTY_RESPONSE",
                        "Package-Service devolvio una respuesta vacia al consultar el paquete " + packageId + ".",
                        HttpStatus.BAD_GATEWAY
                );
            }

            return response;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new BookingException(
                    "PACKAGE_NO_ENCONTRADO",
                    "El paquete " + packageId + " no existe.",
                    HttpStatus.NOT_FOUND
            );
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new BookingException(
                    "PACKAGE_SERVICE_UNAUTHORIZED",
                    "No fue posible validar el paquete seleccionado.",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new BookingException(
                    "SOLICITUD_PAQUETE_INVALIDA",
                    "La consulta del paquete " + packageId + " es invalida.",
                    HttpStatus.BAD_REQUEST
            );
        } catch (HttpClientErrorException ex) {
            throw new BookingException(
                    "PACKAGE_SERVICE_CLIENT_ERROR",
                    "Package-Service rechazo la consulta del paquete " + packageId + ".",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (HttpServerErrorException ex) {
            throw new BookingException(
                    "PACKAGE_SERVICE_SERVER_ERROR",
                    "Package-Service no pudo procesar la consulta del paquete.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        } catch (ResourceAccessException ex) {
            HttpStatus status = isTimeout(ex) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
            String code = isTimeout(ex) ? "PACKAGE_SERVICE_TIMEOUT" : "PACKAGE_SERVICE_UNAVAILABLE";
            throw new BookingException(
                    code,
                    "No fue posible comunicarse con Package-Service.",
                    status
            );
        } catch (RestClientException ex) {
            log.error("Error inesperado consultando Package-Service", ex);
            throw new BookingException(
                    "PACKAGE_SERVICE_ERROR",
                    "Se produjo un error al consultar informacion del paquete.",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private boolean isTimeout(ResourceAccessException ex) {
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timed out");
    }

    private String sanitizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BookingException(
                    "PACKAGE_SERVICE_URL_INVALIDA",
                    "La propiedad package-service.url es obligatoria.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl;
    }
}
