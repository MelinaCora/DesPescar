package com.despescar.hotelservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalServiceAuthenticationFilterTest {

    private static final String PATH = "/hoteles/3f2b8c1e-0000-0000-0000-000000000001/rooms";

    private final FilterChain chain = (req, res) -> { };

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse run(String configured, String method, String uri, String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        if (header != null) {
            request.addHeader("X-Internal-Service-Token", header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new InternalServiceAuthenticationFilter(configured).doFilter(request, response, chain);
        return response;
    }

    @Test
    void tokenCorrectoOtorgaElRolDeServicio() throws Exception {
        MockHttpServletResponse response = run("secreto", "PATCH", PATH, "secreto");

        assertEquals(200, response.getStatus());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("ROLE_SERVICE_RESERVATION",
                SecurityContextHolder.getContext().getAuthentication().getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void sinTokenOTokenDistintoDevuelve401() throws Exception {
        assertEquals(401, run("secreto", "PATCH", PATH, null).getStatus());
        assertEquals(401, run("secreto", "PATCH", PATH, "otro").getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void conVariableVaciaLaRutaQuedaCerrada() throws Exception {
        assertEquals(401, run("", "PATCH", PATH, "").getStatus());
        assertEquals(401, run("", "PATCH", PATH, "cualquiera").getStatus());
    }

    @Test
    void otrasRutasNoSeTocan() throws Exception {
        assertEquals(200, run("secreto", "GET", PATH, null).getStatus());
        assertEquals(200, run("secreto", "PATCH", "/otra/ruta", null).getStatus());
    }
}
