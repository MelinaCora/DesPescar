package com.despescar.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFiltersTest {

    private final JwtService jwtService = new JwtService(JwtSupport.SECRET);
    private final AtomicBoolean chainCalled = new AtomicBoolean();
    private final FilterChain chain = (req, res) -> chainCalled.set(true);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse run(Filter filter, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/algo");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    // --- Filtro tolerante (catalogos) ---

    @Test
    void tolerante_tokenValidAutenticaConElCorreoYElRol() throws Exception {
        run(new JwtAuthenticationFilter(jwtService), "Bearer " + JwtSupport.token("ana@mail.com", 5L, "SUPER_ADMIN", 60_000));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("ana@mail.com", auth.getName());
        assertEquals("ROLE_SUPER_ADMIN", auth.getAuthorities().iterator().next().getAuthority());
        assertTrue(chainCalled.get());
    }

    @Test
    void tolerante_tokenInvalidoOAusenteSigueSinAutenticar() throws Exception {
        MockHttpServletResponse sinToken = run(new JwtAuthenticationFilter(jwtService), null);
        MockHttpServletResponse basura = run(new JwtAuthenticationFilter(jwtService), "Bearer basura");
        MockHttpServletResponse vacio = run(new JwtAuthenticationFilter(jwtService), "Bearer ");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, sinToken.getStatus());
        assertEquals(200, basura.getStatus());
        assertEquals(200, vacio.getStatus());
        assertTrue(chainCalled.get());
    }

    // --- Filtro estricto (reservas y pagos) ---

    @Test
    void estricto_usaElIdDelUsuarioYConvierteUserEnCliente() throws Exception {
        run(new UserIdJwtAuthenticationFilter(jwtService), "Bearer " + JwtSupport.token("ana@mail.com", 42L, "USER", 60_000));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("42", auth.getName());
        assertEquals("ROLE_CLIENTE", auth.getAuthorities().iterator().next().getAuthority());
        assertTrue(chainCalled.get());
    }

    @Test
    void estricto_conservaLosRolesDeAdministrador() throws Exception {
        run(new UserIdJwtAuthenticationFilter(jwtService), "Bearer " + JwtSupport.token("a@mail.com", 1L, "SUPER_ADMIN", 60_000));

        assertEquals("ROLE_SUPER_ADMIN",
                SecurityContextHolder.getContext().getAuthentication().getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void estricto_sinTokenContinuaSinAutenticar() throws Exception {
        MockHttpServletResponse response = run(new UserIdJwtAuthenticationFilter(jwtService), null);

        assertEquals(200, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(chainCalled.get());
    }

    @Test
    void estricto_tokenInvalidoVencidoOSinUserIdResponde401SinContinuar() throws Exception {
        String[] malos = {
                "Bearer basura",
                "Bearer ",
                "Bearer " + JwtSupport.token("ana@mail.com", 1L, "USER", -60_000),
                "Bearer " + JwtSupport.token("ana@mail.com", null, "USER", 60_000),
                "Bearer " + JwtSupport.token("ana@mail.com", 1L, null, 60_000),
                "Bearer " + JwtSupport.token("ana@mail.com", 0L, "USER", 60_000)
        };
        for (String header : malos) {
            chainCalled.set(false);

            MockHttpServletResponse response = run(new UserIdJwtAuthenticationFilter(jwtService), header);

            assertEquals(401, response.getStatus(), header);
            assertTrue(response.getContentAsString().contains("Unauthorized"));
            assertEquals(false, chainCalled.get(), header);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
    }
}
