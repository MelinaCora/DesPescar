package com.despescar.koiiaservice.recomendador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.despescar.koiiaservice.dto.response.KoiRecommendationResponse;
import com.despescar.koiiaservice.enums.TipoOpcion;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecomendadorTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 11, 19);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 11, 22); // 3 noches

    private static UUID id(String clave) {
        return UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8));
    }

    private static VueloCandidato ida(String numero, String tarifa) {
        return new VueloCandidato(id(numero), id(numero + "-tarifa"), "Flybondi", numero,
                LocalDateTime.of(2026, 11, 19, 8, 0), LocalDateTime.of(2026, 11, 19, 10, 30), new BigDecimal(tarifa));
    }

    private static VueloCandidato vuelta(String numero, String tarifa, LocalDateTime salida) {
        return new VueloCandidato(id(numero), id(numero + "-tarifa"), "Flybondi", numero,
                salida, salida.plusMinutes(150), new BigDecimal(tarifa));
    }

    private static VueloCandidato vuelta(String numero, String tarifa) {
        return vuelta(numero, tarifa, LocalDateTime.of(2026, 11, 22, 13, 0));
    }

    private static HabitacionCandidata hab(String nombre, int capacidad, String precioNoche, int libres) {
        return new HabitacionCandidata(id(nombre), nombre, capacidad, new BigDecimal(precioNoche), libres);
    }

    private static HotelCandidato hotel(String nombre, HabitacionCandidata... habitaciones) {
        return new HotelCandidato(id(nombre), nombre, "San Carlos de Bariloche", 4, "https://img/" + nombre,
                List.of(habitaciones));
    }

    // Con 2 viajeros y una doble: A cuesta 3000 (3 noches x 1000), B 6000, C 9000
    private static final List<HotelCandidato> TRES_HOTELES = List.of(
            hotel("A", hab("A-doble", 2, "1000", 5)),
            hotel("B", hab("B-doble", 2, "2000", 5)),
            hotel("C", hab("C-doble", 2, "3000", 5)));

    private static PedidoRecomendacion combo(String presupuesto, int viajeros) {
        return new PedidoRecomendacion(TipoOpcion.COMBO, presupuesto == null ? null : new BigDecimal(presupuesto),
                viajeros, CHECK_IN, CHECK_OUT);
    }

    private static List<BigDecimal> totales(List<KoiRecommendationResponse> opciones) {
        return opciones.stream().map(KoiRecommendationResponse::total).toList();
    }

    private static List<BigDecimal> montos(String... valores) {
        return java.util.Arrays.stream(valores).map(BigDecimal::new).toList();
    }

    @Test
    void filtraPorPresupuesto() {
        // vuelo: (100 + 100) x 2 = 400
        List<KoiRecommendationResponse> opciones = Recomendador.recomendar(combo("6500", 2),
                List.of(ida("FO1", "100")), List.of(vuelta("FO2", "100")), TRES_HOTELES);

        assertEquals(montos("3400.00", "6400.00"), totales(opciones));
        opciones.forEach(o -> {
            assertNull(o.excedeEn());
            assertEquals(TipoOpcion.COMBO, o.tipo());
            assertEquals("ARS", o.moneda());
            assertEquals(new BigDecimal("400.00"), o.vuelo().precio());
        });
        assertTrue(opciones.get(0).motivo().contains("Te quedan $ 3.100"));
    }

    @Test
    void calculaHabitacionesSegunViajerosYDescartaLasQueNoAlcanzan() {
        PedidoRecomendacion pedido = new PedidoRecomendacion(TipoOpcion.HOTEL, new BigDecimal("100000"), 5,
                CHECK_IN, CHECK_OUT);
        List<HotelCandidato> hoteles = List.of(
                hotel("Grande", hab("Doble", 2, "1000", 3), hab("Familiar", 4, "1800", 1)),
                hotel("Chico", hab("Chico-doble", 2, "500", 2)));

        List<KoiRecommendationResponse> opciones = Recomendador.recomendar(pedido, List.of(), List.of(), hoteles);

        assertEquals(1, opciones.size());
        KoiRecommendationResponse o = opciones.get(0);
        assertEquals("Doble", o.hotel().tipoHabitacionNombre());
        assertEquals(3, o.hotel().cantidadHabitaciones());
        assertEquals(5, o.hotel().huespedes());
        assertEquals(3, o.hotel().noches());
        assertEquals(new BigDecimal("9000.00"), o.hotel().precio());
        assertNull(o.vuelo());
    }

    @Test
    void priorizaHotelesDistintos() {
        // A con la ida barata (3400) y con la cara (3500) son las dos más baratas, pero se
        // prefiere mostrar B y C antes que repetir A.
        List<KoiRecommendationResponse> opciones = Recomendador.recomendar(combo("100000", 2),
                List.of(ida("FO1", "100"), ida("FO3", "150")), List.of(vuelta("FO2", "100")), TRES_HOTELES);

        assertEquals(montos("3400.00", "6400.00", "9400.00"), totales(opciones));
        assertEquals(List.of("A", "B", "C"), opciones.stream().map(o -> o.hotel().hotelNombre()).toList());
    }

    @Test
    void siFaltanHotelesDistintosCompletaPorPrecioYQuedaOrdenado() {
        List<HotelCandidato> dos = TRES_HOTELES.subList(0, 2);
        List<KoiRecommendationResponse> opciones = Recomendador.recomendar(combo("100000", 2),
                List.of(ida("FO1", "100"), ida("FO3", "150")), List.of(vuelta("FO2", "100")), dos);

        assertEquals(montos("3400.00", "3500.00", "6400.00"), totales(opciones));
    }

    @Test
    void sinOpcionesDentroDelPresupuestoDevuelveLasDosMasCercanasConExcedente() {
        List<KoiRecommendationResponse> opciones = Recomendador.recomendar(combo("3000", 2),
                List.of(ida("FO1", "100")), List.of(vuelta("FO2", "100")), TRES_HOTELES);

        assertEquals(montos("3400.00", "6400.00"), totales(opciones));
        assertEquals(new BigDecimal("400.00"), opciones.get(0).excedeEn());
        assertEquals(new BigDecimal("3400.00"), opciones.get(1).excedeEn());
        assertTrue(opciones.get(0).motivo().contains("Se pasa por $ 400"));
    }

    @Test
    void soloVueloSinPresupuestoNiVueltaDevuelveLasTresIdasMasBaratas() {
        PedidoRecomendacion pedido = new PedidoRecomendacion(TipoOpcion.VUELO, null, 2, CHECK_IN, null);
        List<KoiRecommendationResponse> opciones = Recomendador.recomendar(pedido,
                List.of(ida("FO1", "100"), ida("FO2", "150"), ida("FO3", "120"), ida("FO4", "200")),
                List.of(), List.of());

        assertEquals(montos("200.00", "240.00", "300.00"), totales(opciones));
        KoiRecommendationResponse o = opciones.get(0);
        assertEquals(TipoOpcion.VUELO, o.tipo());
        assertNull(o.hotel());
        assertNull(o.vuelo().returnFlightId());
        assertEquals("FO1", o.vuelo().numeroIda());
        assertEquals(id("FO1-tarifa"), o.vuelo().departureFareId());
    }

    @Test
    void unaVueltaQueSaleAntesDeLlegarLaIdaNoSeCombina() {
        PedidoRecomendacion pedido = new PedidoRecomendacion(TipoOpcion.VUELO, null, 1, CHECK_IN, CHECK_OUT);
        List<KoiRecommendationResponse> opciones = Recomendador.recomendar(pedido,
                List.of(ida("FO1", "100")),
                List.of(vuelta("FO9", "10", LocalDateTime.of(2026, 11, 19, 9, 0)), vuelta("FO2", "100")),
                List.of());

        assertEquals(1, opciones.size());
        assertEquals("FO2", opciones.get(0).vuelo().numeroVuelta());
        assertEquals(new BigDecimal("200.00"), opciones.get(0).total());
    }

    @Test
    void soloHotelDevuelveEstadiasSinVuelo() {
        PedidoRecomendacion pedido = new PedidoRecomendacion(TipoOpcion.HOTEL, new BigDecimal("7000"), 2,
                CHECK_IN, CHECK_OUT);
        List<KoiRecommendationResponse> opciones = Recomendador.recomendar(pedido, List.of(), List.of(), TRES_HOTELES);

        assertEquals(montos("3000.00", "6000.00"), totales(opciones));
        assertEquals(TipoOpcion.HOTEL, opciones.get(0).tipo());
        assertEquals(CHECK_IN, opciones.get(0).hotel().checkIn());
        assertEquals(CHECK_OUT, opciones.get(0).hotel().checkOut());
        assertNull(opciones.get(0).vuelo());
    }

    @Test
    void unComboSinVueltasNoArmaOpciones() {
        assertTrue(Recomendador.recomendar(combo("100000", 2), List.of(ida("FO1", "100")), List.of(),
                TRES_HOTELES).isEmpty());
    }

    @Test
    void elIdDeLaOpcionEsEstable() {
        List<KoiRecommendationResponse> una = Recomendador.recomendar(combo("6500", 2),
                List.of(ida("FO1", "100")), List.of(vuelta("FO2", "100")), TRES_HOTELES);
        List<KoiRecommendationResponse> otra = Recomendador.recomendar(combo("6500", 2),
                List.of(ida("FO1", "100")), List.of(vuelta("FO2", "100")), TRES_HOTELES);

        assertEquals(una.get(0).optionId(), otra.get(0).optionId());
        assertTrue(!una.get(0).optionId().equals(una.get(1).optionId()));
    }

    @Test
    void formateaPesos() {
        assertEquals("$ 1.500.000", Motivos.pesos(new BigDecimal("1500000")));
        assertEquals("$ 401", Motivos.pesos(new BigDecimal("400.50")));
    }
}
