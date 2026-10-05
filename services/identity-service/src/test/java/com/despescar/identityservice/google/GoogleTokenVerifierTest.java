package com.despescar.identityservice.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.despescar.identityservice.exception.GoogleLoginException;
import com.despescar.identityservice.exception.GoogleLoginNotConfiguredException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "123-abc.apps.googleusercontent.com";

    private MockRestServiceServer server;
    private GoogleTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        verifier = new GoogleTokenVerifier(builder, CLIENT_ID);
    }

    @Test
    void tokenValidoDevuelveCorreoYNombres() {
        server.expect(requestTo(Matchers.startsWith(GoogleTokenVerifier.TOKENINFO_URL)))
                .andExpect(queryParam("id_token", "tok"))
                .andRespond(withSuccess("""
                        {"aud":"%s","email":"Ana@Gmail.com","email_verified":"true",
                         "given_name":"Ana","family_name":"Pérez","name":"Ana Pérez"}
                        """.formatted(CLIENT_ID), MediaType.APPLICATION_JSON));

        GoogleIdentity identity = verifier.verify("tok");

        assertThat(identity.email()).isEqualTo("ana@gmail.com");
        assertThat(identity.firstName()).isEqualTo("Ana");
        assertThat(identity.lastName()).isEqualTo("Pérez");
    }

    @Test
    void sinApellidoUsaUnoPorDefecto() {
        server.expect(requestTo(Matchers.startsWith(GoogleTokenVerifier.TOKENINFO_URL)))
                .andRespond(withSuccess("""
                        {"aud":"%s","email":"solo@gmail.com","email_verified":"true","name":"Solo"}
                        """.formatted(CLIENT_ID), MediaType.APPLICATION_JSON));

        GoogleIdentity identity = verifier.verify("tok");

        assertThat(identity.firstName()).isEqualTo("Solo");
        assertThat(identity.lastName()).isNotBlank();
    }

    @Test
    void tokenDeOtraAppSeRechaza() {
        server.expect(requestTo(Matchers.startsWith(GoogleTokenVerifier.TOKENINFO_URL)))
                .andRespond(withSuccess("""
                        {"aud":"otra-app","email":"ana@gmail.com","email_verified":"true"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> verifier.verify("tok")).isInstanceOf(GoogleLoginException.class);
    }

    @Test
    void correoSinVerificarSeRechaza() {
        server.expect(requestTo(Matchers.startsWith(GoogleTokenVerifier.TOKENINFO_URL)))
                .andRespond(withSuccess("""
                        {"aud":"%s","email":"ana@gmail.com","email_verified":"false"}
                        """.formatted(CLIENT_ID), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> verifier.verify("tok")).isInstanceOf(GoogleLoginException.class);
    }

    @Test
    void tokenInvalidoSegunGoogleSeRechaza() {
        server.expect(requestTo(Matchers.startsWith(GoogleTokenVerifier.TOKENINFO_URL)))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> verifier.verify("tok")).isInstanceOf(GoogleLoginException.class);
    }

    @Test
    void sinClientIdNoEstaConfigurado() {
        GoogleTokenVerifier sinConfig = new GoogleTokenVerifier(RestClient.builder(), " ");

        assertThat(sinConfig.isConfigured()).isFalse();
        assertThat(sinConfig.getClientId()).isNull();
        assertThatThrownBy(() -> sinConfig.verify("tok")).isInstanceOf(GoogleLoginNotConfiguredException.class);
    }
}
