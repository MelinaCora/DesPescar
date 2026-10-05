package com.despescar.koiiaservice.recomendador;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.despescar.koiiaservice.client.dto.AirportResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class AeropuertosPorLugarTest {

    private static AirportResponse aeropuerto(String code, String city) {
        AirportResponse a = new AirportResponse();
        a.setCode(code);
        a.setCity(city);
        a.setCountry("Argentina");
        return a;
    }

    private static final List<AirportResponse> AEROPUERTOS = List.of(
            aeropuerto("EZE", "Buenos Aires"), aeropuerto("AEP", "Buenos Aires"),
            aeropuerto("COR", "Córdoba"), aeropuerto("BRC", "San Carlos de Bariloche"));

    @Test
    void resuelveCiudadesConVariosAeropuertosSinTildesYPorCodigo() {
        assertEquals(List.of("AEP", "EZE"), AeropuertosPorLugar.resolver(AEROPUERTOS, "buenos aires"));
        assertEquals(List.of("COR"), AeropuertosPorLugar.resolver(AEROPUERTOS, "Cordoba"));
        assertEquals(List.of("BRC"), AeropuertosPorLugar.resolver(AEROPUERTOS, "Bariloche"));
        assertEquals(List.of("EZE"), AeropuertosPorLugar.resolver(AEROPUERTOS, "eze"));
    }

    @Test
    void unLugarDesconocidoNoDevuelveNada() {
        assertEquals(List.of(), AeropuertosPorLugar.resolver(AEROPUERTOS, "Rosario"));
        assertEquals(List.of(), AeropuertosPorLugar.resolver(AEROPUERTOS, null));
        assertEquals(List.of(), AeropuertosPorLugar.resolver(AEROPUERTOS, " "));
    }
}
