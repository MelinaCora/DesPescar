package com.despescar.reservationservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.despescar.common.security.JwtService;
import com.despescar.reservationservice.config.SecurityConfig;
import com.despescar.reservationservice.dto.grupo.EditarPartesRequest;
import com.despescar.reservationservice.dto.grupo.GrupoResponse;
import com.despescar.reservationservice.dto.grupo.GrupoResumenResponse;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.EstadoParte;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.service.BookingService;
import com.despescar.reservationservice.service.CarritoService;
import com.despescar.reservationservice.service.GrupoPagoService;
import com.despescar.reservationservice.service.PassengerService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** API del grupo (contrato CB2): rutas, códigos, validación del cuerpo y usuario del token. */
@WebMvcTest({GrupoPagoController.class, BookingController.class})
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=" + GrupoPagoControllerTest.SECRET,
        "reservation-service.sync-token=token-de-payment"})
class GrupoPagoControllerTest {

    static final String SECRET = "test-secret-key-for-reservation-grupo-1234567890";
    private static final String TOKEN = "a".repeat(43);

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private GrupoPagoService grupoService;
    // BookingController comparte /api/bookings: se carga para probar que sus rutas con {id} no pisan las del grupo
    @MockitoBean
    private BookingService bookingService;
    @MockitoBean
    private PassengerService passengerService;
    @MockitoBean
    private CarritoService carritoService;
    @MockitoBean
    private com.despescar.reservationservice.service.CancelacionService cancelacionService;

    static String jwt(String rol, long userId) {
        return "Bearer " + Jwts.builder()
                .subject("usuario" + userId + "@mail.com")
                .claim("role", rol)
                .claim("userId", userId)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static String cliente() {
        return jwt("USER", 7L);
    }

    private static GrupoResponse grupo(Long usuario) {
        boolean organizador = usuario == 7L;
        return new GrupoResponse(12L, EstadoGrupo.ABIERTO, LocalDateTime.of(2026, 10, 6, 15, 0), 86100L,
                new BigDecimal("1060000.00"), "ARS", BigDecimal.ZERO, 3, 0L, organizador, organizador ? 1 : 2,
                TOKEN, true, null,
                List.of(new GrupoResponse.ParteDTO(1, new BigDecimal("353333.34"), EstadoParte.TOMADA, null, true, organizador),
                        new GrupoResponse.ParteDTO(2, new BigDecimal("353333.33"), EstadoParte.LIBRE, null, false, false),
                        new GrupoResponse.ParteDTO(3, new BigDecimal("353333.33"), EstadoParte.LIBRE, null, false, false)),
                new GrupoResponse.ViajeDTO(null, List.of()));
    }

    // ---------- rutas ----------

    @Test
    void lasRutasDeGruposNoSeConfundenConLasDeUnaReservaPorId() throws Exception {
        when(grupoService.misGrupos(9L)).thenReturn(List.of());
        when(grupoService.consultar(TOKEN, 9L)).thenReturn(grupo(9L));

        mockMvc.perform(get("/api/bookings/grupos/mios").header("Authorization", jwt("USER", 9L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post("/api/bookings/grupos/consultar").header("Authorization", jwt("USER", 9L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isOk());

        verify(grupoService).misGrupos(9L);
        verify(grupoService).consultar(TOKEN, 9L);
        org.mockito.Mockito.verifyNoInteractions(bookingService, carritoService);
    }

    // ---------- organizador ----------

    @Test
    void empezarElGrupoResponde201ConElGrupoYElTokenDelEnlace() throws Exception {
        when(grupoService.iniciar(12L, 3, 7L)).thenReturn(grupo(7L));

        mockMvc.perform(post("/api/bookings/12/grupo").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidadPartes\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservaId").value(12))
                .andExpect(jsonPath("$.estado").value("ABIERTO"))
                .andExpect(jsonPath("$.soyOrganizador").value(true))
                .andExpect(jsonPath("$.enlaceToken").value(TOKEN))
                .andExpect(jsonPath("$.partes.length()").value(3))
                .andExpect(jsonPath("$.partes[0].monto").value(353333.34));
    }

    @Test
    void empezarConOncePartesResponde400ValidacionSinLlamarAlServicio() throws Exception {
        mockMvc.perform(post("/api/bookings/12/grupo").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidadPartes\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
        verify(grupoService, never()).iniciar(anyLong(), anyInt(), anyLong());
    }

    @Test
    void empezarSinCantidadResponde400Validacion() throws Exception {
        mockMvc.perform(post("/api/bookings/12/grupo").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void losCodigosDelServicioSePropaganConSuEstado() throws Exception {
        when(grupoService.iniciar(12L, 3, 7L)).thenThrow(new BookingException(
                "GRUPO_YA_EXISTE", "Este carrito ya se está pagando en grupo.", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/bookings/12/grupo").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidadPartes\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("GRUPO_YA_EXISTE"))
                .andExpect(jsonPath("$.mensaje").value("Este carrito ya se está pagando en grupo."));
    }

    @Test
    void verElGrupoComoOrganizadorUsaElUsuarioDelToken() throws Exception {
        when(grupoService.verComoOrganizador(12L, 7L)).thenReturn(grupo(7L));

        mockMvc.perform(get("/api/bookings/12/grupo").header("Authorization", cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miParte").value(1));
    }

    @Test
    void editarPartesConCantidadLlegaAlServicioComoCantidad() throws Exception {
        when(grupoService.editarPartes(eq(12L), any(), eq(7L))).thenReturn(grupo(7L));

        mockMvc.perform(put("/api/bookings/12/grupo/partes").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidadPartes\":4}"))
                .andExpect(status().isOk());

        ArgumentCaptor<EditarPartesRequest> pedido = ArgumentCaptor.forClass(EditarPartesRequest.class);
        verify(grupoService).editarPartes(eq(12L), pedido.capture(), eq(7L));
        org.junit.jupiter.api.Assertions.assertEquals(4, pedido.getValue().cantidadPartes());
        org.junit.jupiter.api.Assertions.assertNull(pedido.getValue().montos());
    }

    @Test
    void editarPartesConMontosLosPasaConDosDecimales() throws Exception {
        when(grupoService.editarPartes(eq(12L), any(), eq(7L))).thenReturn(grupo(7L));

        mockMvc.perform(put("/api/bookings/12/grupo/partes").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montos\":[400000.00,330000.00,330000.00]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<EditarPartesRequest> pedido = ArgumentCaptor.forClass(EditarPartesRequest.class);
        verify(grupoService).editarPartes(eq(12L), pedido.capture(), eq(7L));
        org.junit.jupiter.api.Assertions.assertEquals(List.of(new BigDecimal("400000.00"),
                new BigDecimal("330000.00"), new BigDecimal("330000.00")), pedido.getValue().montos());
    }

    @Test
    void editarPartesConOnceMontosResponde400Validacion() throws Exception {
        String once = "[" + "100000.00,".repeat(10) + "60000.00]";
        mockMvc.perform(put("/api/bookings/12/grupo/partes").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"montos\":" + once + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        verify(grupoService, never()).editarPartes(anyLong(), any(), anyLong());
    }

    @Test
    void liberarUnaParteResponde200ConElGrupo() throws Exception {
        when(grupoService.liberarParte(12L, 2, 7L)).thenReturn(grupo(7L));

        mockMvc.perform(post("/api/bookings/12/grupo/partes/2/liberar").header("Authorization", cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value(12));
    }

    @Test
    void liberarConUnNumeroQueNoEsEnteroResponde400ParametroInvalido() throws Exception {
        mockMvc.perform(post("/api/bookings/12/grupo/partes/dos/liberar").header("Authorization", cliente()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void cancelarElGrupoResponde200ConElEstadoCancelado() throws Exception {
        GrupoResponse cancelado = new GrupoResponse(12L, EstadoGrupo.CANCELADO, LocalDateTime.of(2026, 10, 6, 15, 0), 0L,
                new BigDecimal("1060000.00"), "ARS", BigDecimal.ZERO, 3, 0L, true, 1, TOKEN, false, "GRUPO_CANCELADO",
                List.of(), new GrupoResponse.ViajeDTO(null, List.of()));
        when(grupoService.cancelar(12L, 7L)).thenReturn(cancelado);

        mockMvc.perform(delete("/api/bookings/12/grupo").header("Authorization", cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.motivoCierre").value("GRUPO_CANCELADO"));
    }

    // ---------- amigos y enlace ----------

    @Test
    void laParticipacionResponde200ParaQuienTieneParte() throws Exception {
        when(grupoService.participacion(12L, 9L)).thenReturn(grupo(9L));

        mockMvc.perform(get("/api/bookings/12/grupo/participacion").header("Authorization", jwt("USER", 9L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soyOrganizador").value(false))
                .andExpect(jsonPath("$.miParte").value(2));
    }

    @Test
    void consultarPorTokenLoRecibeEnElCuerpoYNoEnLaUrl() throws Exception {
        when(grupoService.consultar(TOKEN, 9L)).thenReturn(grupo(9L));

        mockMvc.perform(post("/api/bookings/grupos/consultar").header("Authorization", jwt("USER", 9L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value(12));
    }

    @Test
    void consultarConTokenVacioResponde400Validacion() throws Exception {
        mockMvc.perform(post("/api/bookings/grupos/consultar").header("Authorization", jwt("USER", 9L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        verify(grupoService, never()).consultar(anyString(), anyLong());
    }

    @Test
    void unEnlaceVencidoResponde410ConSuCodigo() throws Exception {
        when(grupoService.consultar(TOKEN, 9L)).thenThrow(new BookingException(
                "ENLACE_VENCIDO", "Este enlace ya no está activo: el pago en grupo terminó o venció.", HttpStatus.GONE));

        mockMvc.perform(post("/api/bookings/grupos/consultar").header("Authorization", jwt("USER", 9L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("ENLACE_VENCIDO"));
    }

    @Test
    void unirseConApodoLoPasaAlServicio() throws Exception {
        when(grupoService.unirse(TOKEN, "Juli", 9L)).thenReturn(grupo(9L));

        mockMvc.perform(post("/api/bookings/grupos/unirse").header("Authorization", jwt("USER", 9L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\",\"apodo\":\"Juli\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miParte").value(2));
    }

    @Test
    void unirseSinApodoLoPasaComoNull() throws Exception {
        when(grupoService.unirse(eq(TOKEN), isNull(), eq(9L))).thenReturn(grupo(9L));

        mockMvc.perform(post("/api/bookings/grupos/unirse").header("Authorization", jwt("USER", 9L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isOk());
        verify(grupoService).unirse(eq(TOKEN), isNull(), eq(9L));
    }

    @Test
    void unApodoConCaracteresRarosResponde400Validacion() throws Exception {
        mockMvc.perform(post("/api/bookings/grupos/unirse").header("Authorization", jwt("USER", 9L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\",\"apodo\":\"<script>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        verify(grupoService, never()).unirse(anyString(), any(), anyLong());
    }

    @Test
    void misGruposDevuelveLaListaDelUsuarioDelToken() throws Exception {
        when(grupoService.misGrupos(9L)).thenReturn(List.of(new GrupoResumenResponse(12L, TOKEN, EstadoGrupo.ABIERTO,
                LocalDateTime.of(2026, 10, 6, 15, 0), 86100L, false, 2, new BigDecimal("353333.33"),
                EstadoParte.TOMADA, "Córdoba")));

        mockMvc.perform(get("/api/bookings/grupos/mios").header("Authorization", jwt("USER", 9L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reservaId").value(12))
                .andExpect(jsonPath("$[0].destino").value("Córdoba"))
                .andExpect(jsonPath("$[0].enlaceToken").value(TOKEN));
    }

    // ---------- seguridad ----------

    @Test
    void sinSesionResponde401ConElCuerpoDelServicio() throws Exception {
        mockMvc.perform(get("/api/bookings/grupos/mios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        verify(grupoService, never()).misGrupos(anyLong());
    }

    @Test
    void unAdministradorDeHotelResponde403() throws Exception {
        mockMvc.perform(post("/api/bookings/12/grupo").header("Authorization", jwt("HOTEL_ADMIN", 3L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidadPartes\":3}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        verify(grupoService, never()).iniciar(anyLong(), anyInt(), anyLong());
    }

    @Test
    void elUsuarioSaleDelTokenAunqueElCuerpoTraigaOtro() throws Exception {
        when(grupoService.unirse(TOKEN, null, 9L)).thenReturn(grupo(9L));

        mockMvc.perform(post("/api/bookings/grupos/unirse?usuarioId=7").header("Authorization", jwt("USER", 9L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\",\"usuarioId\":7}"))
                .andExpect(status().isOk());
        verify(grupoService).unirse(TOKEN, null, 9L);
    }

    @Test
    void quienNoTieneParteRecibe404SinSaberSiElGrupoExiste() throws Exception {
        when(grupoService.participacion(12L, 9L)).thenThrow(new BookingException(
                "GRUPO_NO_ENCONTRADO", "No encontramos ese pago en grupo.", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/bookings/12/grupo/participacion").header("Authorization", jwt("USER", 9L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("GRUPO_NO_ENCONTRADO"));
    }

    @Test
    void elTokenDelEnlaceNoSeAceptaPorLaUrl() throws Exception {
        mockMvc.perform(get("/api/bookings/grupos/" + TOKEN).header("Authorization", jwt("USER", 9L)))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/bookings/grupos/consultar?token=" + TOKEN).header("Authorization", jwt("USER", 9L))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        verify(grupoService, never()).consultar(anyString(), anyLong());
    }

    @Test
    void elTokenInternoDePaymentServiceNoAbreLaApiDelGrupo() throws Exception {
        mockMvc.perform(get("/api/bookings/grupos/mios").header("X-Internal-Service-Token", "token-de-payment"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        verify(grupoService, never()).misGrupos(anyLong());
    }
}
