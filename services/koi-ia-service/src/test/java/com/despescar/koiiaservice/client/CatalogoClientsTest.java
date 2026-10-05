package com.despescar.koiiaservice.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse;
import com.despescar.koiiaservice.client.dto.HotelDetalleResponse;
import com.despescar.koiiaservice.client.dto.HotelResumenResponse;
import com.despescar.koiiaservice.exception.KoiCatalogUnavailableException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CatalogoClientsTest {

    private static final LocalDate IDA = LocalDate.of(2026, 11, 19);
    private static final LocalDate VUELTA = LocalDate.of(2026, 11, 22);
    private static final UUID HOTEL = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    void buscaVuelosConFechasYPasajeros() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://vuelos");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogoVuelosClient client = new CatalogoVuelosClient(builder);
        server.expect(requestTo(
                        "http://vuelos/api/flights/search?origin=AEP&destination=BRC&departureDate=2026-11-19&returnDate=2026-11-22&passengers=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"metadata":{"totalResults":1},
                         "departureFlights":[{"id":"00000000-0000-0000-0000-000000000001","flightNumber":"FO1045",
                           "airline":{"name":"Flybondi","logoUrl":""},
                           "itinerary":{"departure":{"iata":"AEP","dateTime":"2026-11-19T08:00"},
                                        "arrival":{"iata":"BRC","dateTime":"2026-11-19T10:30"},"durationMinutes":120},
                           "fares":[{"id":"00000000-0000-0000-0000-0000000000f1","name":"Light",
                                     "price":{"currency":"ARS","transparentFinalPrice":130}}]}],
                         "returnFlights":[]}
                        """, MediaType.APPLICATION_JSON));

        BusquedaVuelosResponse respuesta = client.buscar("AEP", "BRC", IDA, VUELTA, 2);

        server.verify();
        assertEquals(1, respuesta.departureFlights().size());
        assertEquals("Flybondi", respuesta.departureFlights().get(0).airline().name());
        assertEquals(0, new BigDecimal("130").compareTo(
                respuesta.departureFlights().get(0).fares().get(0).price().transparentFinalPrice()));
    }

    @Test
    void buscaHotelesYPideElDetalleConLasFechas() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hoteles");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogoHotelesClient client = new CatalogoHotelesClient(builder);
        server.expect(requestTo("http://hoteles/hoteles?destino=Bariloche&checkIn=2026-11-19&checkOut=2026-11-22&huespedes=2"))
                .andRespond(withSuccess("""
                        [{"id":"00000000-0000-0000-0000-0000000000a1","nombre":"Llao Llao","ciudad":"San Carlos de Bariloche",
                          "pais":"Argentina","estrellas":5,"imagenPrincipal":"https://img/1","disponible":true,
                          "precioTotalDesde":960000}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://hoteles/hoteles/" + HOTEL + "?checkIn=2026-11-19&checkOut=2026-11-22&huespedes=2"))
                .andRespond(withSuccess("""
                        {"id":"00000000-0000-0000-0000-0000000000a1","nombre":"Llao Llao","ciudad":"San Carlos de Bariloche",
                         "estrellas":5,"imagenes":["https://img/1"],"noches":3,
                         "habitaciones":[{"id":"00000000-0000-0000-0000-0000000000b1","nombre":"Doble","capacidad":2,
                           "precioPorNoche":320000,"unidadesLibres":4,"habitacionesNecesarias":1,
                           "precioTotal":960000,"disponible":true}]}
                        """, MediaType.APPLICATION_JSON));

        List<HotelResumenResponse> resumenes = client.buscar("Bariloche", IDA, VUELTA, 2);
        HotelDetalleResponse detalle = client.detalle(HOTEL, IDA, VUELTA, 2);

        server.verify();
        assertEquals(Boolean.TRUE, resumenes.get(0).disponible());
        assertEquals(4, detalle.habitaciones().get(0).unidadesLibres());
        assertEquals(2, detalle.habitaciones().get(0).capacidad());
    }

    @Test
    void unErrorDelCatalogoSeTraduceAServicioNoDisponible() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://vuelos");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogoVuelosClient client = new CatalogoVuelosClient(builder);
        server.expect(requestTo("http://vuelos/api/airports")).andRespond(withServerError());

        assertThrows(KoiCatalogUnavailableException.class, client::aeropuertos);
    }
}
