package com.despescar.identityservice.google;

import com.despescar.identityservice.exception.GoogleLoginException;
import com.despescar.identityservice.exception.GoogleLoginNotConfiguredException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Verifica un ID token de Google contra el endpoint tokeninfo de Google, que valida la firma y la
 * vigencia. Aca solo se controla que el token sea para esta app (aud) y que el correo este verificado.
 */
@Component
public class GoogleTokenVerifier {

    static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    private final RestClient restClient;
    private final String clientId;

    public GoogleTokenVerifier(
            RestClient.Builder restClientBuilder,
            @Value("${google.client-id:}") String clientId
    ) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    public boolean isConfigured() {
        return !clientId.isBlank();
    }

    public String getClientId() {
        return isConfigured() ? clientId : null;
    }

    public GoogleIdentity verify(String idToken) {
        if (!isConfigured()) {
            throw new GoogleLoginNotConfiguredException();
        }
        Map<String, Object> claims;
        try {
            claims = restClient.get()
                    .uri(TOKENINFO_URL + "?id_token={token}", idToken)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientException e) {
            // Google responde 400 cuando el token es invalido o vencio
            throw new GoogleLoginException("No pudimos validar tu cuenta de Google. Probá de nuevo.");
        }
        if (claims == null || !clientId.equals(claims.get("aud"))) {
            throw new GoogleLoginException("La credencial de Google no corresponde a esta aplicación.");
        }
        String email = asText(claims.get("email"));
        if (email.isBlank() || !"true".equals(asText(claims.get("email_verified")))) {
            throw new GoogleLoginException("Tu cuenta de Google no tiene un correo verificado.");
        }
        return new GoogleIdentity(
                email.toLowerCase(),
                nombreONada(asText(claims.get("given_name")), asText(claims.get("name")), "Usuario"),
                nombreONada(asText(claims.get("family_name")), "", "Google")
        );
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** Google no siempre manda nombre y apellido por separado; nunca se guarda un nombre vacio. */
    private static String nombreONada(String preferido, String alternativo, String porDefecto) {
        if (!preferido.isBlank()) return preferido;
        if (!alternativo.isBlank()) return alternativo;
        return porDefecto;
    }
}
