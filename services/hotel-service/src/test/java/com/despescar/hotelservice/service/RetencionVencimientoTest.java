package com.despescar.hotelservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.hotelservice.dto.internal.RetencionResponse;
import com.despescar.hotelservice.entity.EstadoRetencion;
import com.despescar.hotelservice.entity.Hotel;
import com.despescar.hotelservice.entity.Retencion;
import com.despescar.hotelservice.entity.TipoHabitacion;
import com.despescar.hotelservice.entity.TramoCancelacion;
import com.despescar.hotelservice.exception.ConflictoException;
import com.despescar.hotelservice.exception.RetencionNoEncontradaException;
import com.despescar.hotelservice.exception.SolicitudInvalidaException;
import com.despescar.hotelservice.repository.RetencionRepository;
import com.despescar.hotelservice.repository.TipoHabitacionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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
class RetencionVencimientoTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant AHORA = Instant.parse("2026-11-01T15:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZONA);
    private static final LocalDate D10 = LocalDate.of(2026, 11, 10);
    private static final LocalDate D12 = LocalDate.of(2026, 11, 12);
    private static final Instant EN_24_HORAS = AHORA.plus(Duration.ofHours(24));

    @Mock
    private TipoHabitacionRepository tipoRepository;
    @Mock
    private RetencionRepository retencionRepository;

    private RetencionService service;
    private TipoHabitacion doble;

    @BeforeEach
    void setUp() {
        service = new RetencionService(tipoRepository, retencionRepository, RELOJ);
        Hotel hotel = new Hotel();
        hotel.setId(UUID.randomUUID());
        hotel.setNombre("Sheraton Córdoba");
        hotel.setCiudad("Córdoba");
        hotel.setPais("Argentina");
        hotel.setDireccion("Calle 1");
        hotel.setEstrellas(5);
        hotel.setPoliticaCancelacion(new ArrayList<>(List.of(new TramoCancelacion(48, 100))));
        doble = new TipoHabitacion();
        doble.setId(UUID.randomUUID());
        doble.setNombre("Doble");
        doble.setCapacidad(2);
        doble.setPrecioPorNoche(new BigDecimal("100000.00"));
        doble.setCantidadUnidades(3);
        hotel.agregarHabitacion(doble);
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

    @Test
    void extiendeUnaRetencionVigenteSinRevisarLugar() {
        Retencion r = existente(EstadoRetencion.RETENIDA, AHORA.plusSeconds(600));
        tipoEncontrado();

        RetencionResponse respuesta = service.cambiarVencimiento(r.getId(), EN_24_HORAS);

        assertEquals(EN_24_HORAS, r.getExpiraEn());
        assertEquals(EN_24_HORAS, respuesta.expiraEn());
        assertEquals(EstadoRetencion.RETENIDA, respuesta.estado());
        verify(retencionRepository, never()).findActivasQueSolapan(anyCollection(), eq(D10), eq(D12), eq(AHORA));
    }

    @Test
    void tambienAcortaElVencimientoParaCompensar() {
        Retencion r = existente(EstadoRetencion.RETENIDA, EN_24_HORAS);
        tipoEncontrado();

        service.cambiarVencimiento(r.getId(), AHORA.plusSeconds(300));

        assertEquals(AHORA.plusSeconds(300), r.getExpiraEn());
    }

    @Test
    void unaVencidaConLugarSeVuelveATomar() {
        Retencion r = existente(EstadoRetencion.RETENIDA, AHORA.minusSeconds(60));
        tipoEncontrado();
        ocupadas(1);

        service.cambiarVencimiento(r.getId(), EN_24_HORAS);

        assertEquals(EN_24_HORAS, r.getExpiraEn());
    }

    @Test
    void unaVencidaSinLugarResponde409SinDisponibilidad() {
        Retencion r = existente(EstadoRetencion.RETENIDA, AHORA.minusSeconds(60));
        tipoEncontrado();
        ocupadas(2);

        ConflictoException ex = assertThrows(ConflictoException.class,
                () -> service.cambiarVencimiento(r.getId(), EN_24_HORAS));

        assertEquals("SIN_DISPONIBILIDAD", ex.getCodigo());
        assertEquals(AHORA.minusSeconds(60), r.getExpiraEn());
    }

    @Test
    void unaLiberadaResponde409() {
        Retencion r = existente(EstadoRetencion.LIBERADA, AHORA.plusSeconds(600));

        ConflictoException ex = assertThrows(ConflictoException.class,
                () -> service.cambiarVencimiento(r.getId(), EN_24_HORAS));

        assertEquals("RETENCION_LIBERADA", ex.getCodigo());
    }

    @Test
    void unaConfirmadaResponde409() {
        Retencion r = existente(EstadoRetencion.CONFIRMADA, AHORA.plusSeconds(600));

        ConflictoException ex = assertThrows(ConflictoException.class,
                () -> service.cambiarVencimiento(r.getId(), EN_24_HORAS));

        assertEquals("RETENCION_CONFIRMADA", ex.getCodigo());
    }

    @Test
    void unVencimientoPasadoOMasAllaDe25HorasEsInvalido() {
        UUID id = UUID.randomUUID();

        assertThrows(SolicitudInvalidaException.class, () -> service.cambiarVencimiento(id, AHORA));
        assertThrows(SolicitudInvalidaException.class,
                () -> service.cambiarVencimiento(id, AHORA.plus(Duration.ofHours(25)).plusSeconds(1)));
        assertThrows(SolicitudInvalidaException.class, () -> service.cambiarVencimiento(id, null));
        verify(retencionRepository, never()).findByIdForUpdate(id);
    }

    @Test
    void justoEn25HorasSeAcepta() {
        Retencion r = existente(EstadoRetencion.RETENIDA, AHORA.plusSeconds(600));
        tipoEncontrado();

        service.cambiarVencimiento(r.getId(), AHORA.plus(Duration.ofHours(25)));

        assertEquals(AHORA.plus(Duration.ofHours(25)), r.getExpiraEn());
    }

    @Test
    void unaInexistenteResponde404() {
        UUID id = UUID.randomUUID();
        when(retencionRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThrows(RetencionNoEncontradaException.class, () -> service.cambiarVencimiento(id, EN_24_HORAS));
    }
}
