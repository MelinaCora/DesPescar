package com.despescar.hotelservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.hotelservice.dto.TramoDto;
import com.despescar.hotelservice.dto.internal.RetencionRequest;
import com.despescar.hotelservice.dto.internal.RetencionResponse;
import com.despescar.hotelservice.entity.EstadoRetencion;
import com.despescar.hotelservice.entity.Hotel;
import com.despescar.hotelservice.entity.Retencion;
import com.despescar.hotelservice.entity.TipoHabitacion;
import com.despescar.hotelservice.entity.TramoCancelacion;
import com.despescar.hotelservice.exception.ConflictoException;
import com.despescar.hotelservice.exception.HotelNotFoundException;
import com.despescar.hotelservice.exception.RetencionNoEncontradaException;
import com.despescar.hotelservice.exception.SolicitudInvalidaException;
import com.despescar.hotelservice.repository.RetencionRepository;
import com.despescar.hotelservice.repository.TipoHabitacionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetencionServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant AHORA = Instant.parse("2026-11-01T15:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZONA);
    private static final LocalDate D10 = LocalDate.of(2026, 11, 10);
    private static final LocalDate D12 = LocalDate.of(2026, 11, 12);
    private static final Instant VENCE = AHORA.plusSeconds(900);

    @Mock
    private TipoHabitacionRepository tipoRepository;
    @Mock
    private RetencionRepository retencionRepository;

    private RetencionService service;
    private Hotel hotel;
    private TipoHabitacion doble;

    @BeforeEach
    void setUp() {
        service = new RetencionService(tipoRepository, retencionRepository, RELOJ);
        hotel = new Hotel();
        hotel.setId(UUID.randomUUID());
        hotel.setNombre("Sheraton Córdoba");
        hotel.setCiudad("Córdoba");
        hotel.setPais("Argentina");
        hotel.setDireccion("Calle 1");
        hotel.setEstrellas(5);
        hotel.setPoliticaCancelacion(new ArrayList<>(List.of(new TramoCancelacion(48, 100), new TramoCancelacion(0, 0))));
        doble = new TipoHabitacion();
        doble.setId(UUID.randomUUID());
        doble.setNombre("Doble");
        doble.setCapacidad(2);
        doble.setPrecioPorNoche(new BigDecimal("100000.00"));
        doble.setCantidadUnidades(3);
        hotel.agregarHabitacion(doble);
    }

    private RetencionRequest pedido(int cantidad, int huespedes) {
        return new RetencionRequest(12L, 7L, hotel.getId(), doble.getId(), D10, D12, cantidad, huespedes, VENCE);
    }

    private void tipoEncontrado() {
        when(tipoRepository.findByIdForUpdate(doble.getId())).thenReturn(Optional.of(doble));
    }

    /** Otra reserva ya ocupa `cantidad` unidades en esas noches. */
    private void ocupadas(int cantidad) {
        Retencion otra = new Retencion();
        otra.setCheckIn(D10);
        otra.setCheckOut(D12);
        otra.setCantidad(cantidad);
        when(retencionRepository.findActivasQueSolapan(anyCollection(), eq(D10), eq(D12), eq(AHORA)))
                .thenReturn(List.of(otra));
    }

    private void guardaConId() {
        when(retencionRepository.save(any(Retencion.class))).thenAnswer(inv -> {
            Retencion r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
    }

    private Retencion existente(EstadoRetencion estado, Instant expira) {
        Retencion r = new Retencion();
        r.setId(UUID.randomUUID());
        r.setReservaId(12L);
        r.setUsuarioId(7L);
        r.setTipoHabitacionId(doble.getId());
        r.setCheckIn(D10);
        r.setCheckOut(D12);
        r.setCantidad(2);
        r.setHuespedes(3);
        r.setEstado(estado);
        r.setExpiraEn(expira);
        when(retencionRepository.findByIdForUpdate(r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void creaLaRetencionConPrecioYCopiaDeLaPolitica() {
        tipoEncontrado();
        ocupadas(1);
        guardaConId();

        RetencionResponse r = service.crear(pedido(2, 3));

        assertNotNull(r.retencionId());
        assertEquals(EstadoRetencion.RETENIDA, r.estado());
        assertEquals(new BigDecimal("400000.00"), r.precioTotal());
        assertEquals(2, r.noches());
        assertEquals("ARS", r.moneda());
        assertEquals("Sheraton Córdoba", r.hotelNombre());
        assertEquals("Doble", r.tipoHabitacionNombre());
        assertEquals(List.of(new TramoDto(48, 100), new TramoDto(0, 0)), r.politicaCancelacion());
        assertEquals(VENCE, r.expiraEn());
    }

    @Test
    void sinUnidadesLibresResponde409() {
        tipoEncontrado();
        ocupadas(2);

        ConflictoException ex = assertThrows(ConflictoException.class, () -> service.crear(pedido(2, 3)));

        assertEquals("SIN_DISPONIBILIDAD", ex.getCodigo());
        verify(retencionRepository, never()).save(any());
    }

    @Test
    void lasHabitacionesTienenQueAlcanzarParaLosHuespedes() {
        tipoEncontrado();
        assertThrows(SolicitudInvalidaException.class, () -> service.crear(pedido(2, 5)));
    }

    @Test
    void rechazaCheckInPasadoYVencimientoPasado() {
        tipoEncontrado();
        RetencionRequest ayer = new RetencionRequest(12L, 7L, hotel.getId(), doble.getId(),
                LocalDate.of(2026, 10, 31), D12, 1, 1, VENCE);
        RetencionRequest vencida = new RetencionRequest(12L, 7L, hotel.getId(), doble.getId(),
                D10, D12, 1, 1, AHORA);

        assertThrows(SolicitudInvalidaException.class, () -> service.crear(ayer));
        assertThrows(SolicitudInvalidaException.class, () -> service.crear(vencida));
    }

    @Test
    void unTipoDeOtroHotelOInactivoResponde404() {
        tipoEncontrado();
        RetencionRequest otroHotel = new RetencionRequest(12L, 7L, UUID.randomUUID(), doble.getId(),
                D10, D12, 1, 1, VENCE);
        assertThrows(HotelNotFoundException.class, () -> service.crear(otroHotel));

        doble.setActivo(false);
        assertThrows(HotelNotFoundException.class, () -> service.crear(pedido(1, 1)));
    }

    @Test
    void confirmarUnaVigenteNoRevisaLaDisponibilidad() {
        Retencion r = existente(EstadoRetencion.RETENIDA, VENCE);
        tipoEncontrado();

        RetencionResponse res = service.confirmar(r.getId(), "  Ana Pérez ");

        assertEquals(EstadoRetencion.CONFIRMADA, r.getEstado());
        assertEquals("Ana Pérez", r.getNombreTitular());
        assertEquals(EstadoRetencion.CONFIRMADA, res.estado());
        verify(retencionRepository, never()).findActivasQueSolapan(any(), any(), any(), any());
    }

    @Test
    void confirmarUnaVencidaConLugarLaConfirma() {
        Retencion r = existente(EstadoRetencion.RETENIDA, AHORA.minusSeconds(60));
        tipoEncontrado();
        ocupadas(1);

        service.confirmar(r.getId(), "Ana Pérez");

        assertEquals(EstadoRetencion.CONFIRMADA, r.getEstado());
    }

    @Test
    void confirmarUnaVencidaSinLugarResponde409() {
        Retencion r = existente(EstadoRetencion.RETENIDA, AHORA.minusSeconds(60));
        tipoEncontrado();
        ocupadas(2);

        ConflictoException ex = assertThrows(ConflictoException.class, () -> service.confirmar(r.getId(), "Ana Pérez"));

        assertEquals("SIN_DISPONIBILIDAD", ex.getCodigo());
        assertEquals(EstadoRetencion.RETENIDA, r.getEstado());
    }

    @Test
    void confirmarUnaLiberadaResponde409() {
        Retencion r = existente(EstadoRetencion.LIBERADA, VENCE);

        ConflictoException ex = assertThrows(ConflictoException.class, () -> service.confirmar(r.getId(), "Ana Pérez"));

        assertEquals("RETENCION_LIBERADA", ex.getCodigo());
    }

    @Test
    void confirmarDosVecesEsIdempotente() {
        Retencion r = existente(EstadoRetencion.CONFIRMADA, VENCE);
        r.setNombreTitular("Ana Pérez");
        tipoEncontrado();

        RetencionResponse res = service.confirmar(r.getId(), "Otro Nombre");

        assertEquals(EstadoRetencion.CONFIRMADA, res.estado());
        assertEquals("Ana Pérez", r.getNombreTitular());
    }

    @Test
    void liberarEsIdempotenteYAceptaConfirmadas() {
        Retencion r = existente(EstadoRetencion.CONFIRMADA, VENCE);

        service.liberar(r.getId());
        service.liberar(r.getId());

        assertEquals(EstadoRetencion.LIBERADA, r.getEstado());
    }

    @Test
    void confirmarSinNombreResponde400() {
        UUID id = UUID.randomUUID();

        assertThrows(SolicitudInvalidaException.class, () -> service.confirmar(id, null));
        assertThrows(SolicitudInvalidaException.class, () -> service.confirmar(id, "  "));
    }

    @Test
    void unaRetencionInexistenteResponde404() {
        UUID id = UUID.randomUUID();
        when(retencionRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThrows(RetencionNoEncontradaException.class, () -> service.liberar(id));
    }
}
