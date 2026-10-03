package com.despescar.koiiaservice.service.ai;

import com.despescar.koiiaservice.client.FlightCatalogClient;
import com.despescar.koiiaservice.client.HotelCatalogClient;
import com.despescar.koiiaservice.client.PackageCatalogClient;
import com.despescar.koiiaservice.client.dto.FlightResponse;
import com.despescar.koiiaservice.client.dto.HotelResponse;
import com.despescar.koiiaservice.client.dto.TravelPackageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class KoiTravelTools {

    private final PackageCatalogClient packageCatalogClient;
    private final FlightCatalogClient flightCatalogClient;
    private final HotelCatalogClient hotelCatalogClient;

    // Método adaptado para devolver un String formateado que la IA pueda leer
    public String buscarVuelosIdaYVuelta(String origen, String destino, String fechaIda, String fechaVuelta) {
        try {
            List<FlightResponse> todosLosVuelos = flightCatalogClient.findAllFlights();
            DateTimeFormatter formateador = DateTimeFormatter.ofPattern("dd 'de' MMMM 'a las' HH:mm 'hs'", new Locale("es", "ES"));
            StringBuilder resultados = new StringBuilder();

            List<FlightResponse> vuelosIda = todosLosVuelos.stream()
                    .filter(f -> f.getDestinationAirport() != null && f.getOriginAirport() != null)
                    .filter(f -> f.getDestinationAirport().getCity().toLowerCase().contains(destino.toLowerCase()) &&
                            f.getOriginAirport().getCity().toLowerCase().contains(origen.toLowerCase()))
                    .limit(3)
                    .toList();

            resultados.append("🛫 **Vuelos de IDA (").append(origen).append(" ➜ ").append(destino).append("):**\n");
            if (!vuelosIda.isEmpty()) {
                for (FlightResponse vuelo : vuelosIda) {
                    LocalDateTime fecha = LocalDateTime.parse(vuelo.getDepartureTime().toString());
                    resultados.append("   - 📅 ").append(fecha.format(formateador))
                            .append(" | 💵 $").append(vuelo.getPrice()).append("\n");
                }
            } else {
                resultados.append("   - No hay vuelos de ida disponibles.\n");
            }

            if (fechaVuelta != null && !fechaVuelta.isBlank()) {
                List<FlightResponse> vuelosVuelta = todosLosVuelos.stream()
                        .filter(f -> f.getDestinationAirport() != null && f.getOriginAirport() != null)
                        .filter(f -> f.getDestinationAirport().getCity().toLowerCase().contains(origen.toLowerCase()) &&
                                f.getOriginAirport().getCity().toLowerCase().contains(destino.toLowerCase()))
                        .limit(3)
                        .toList();

                resultados.append("\n🛬 **Vuelos de VUELTA (").append(destino).append(" ➜ ").append(origen).append("):**\n");
                if (!vuelosVuelta.isEmpty()) {
                    for (FlightResponse vuelo : vuelosVuelta) {
                        LocalDateTime fecha = LocalDateTime.parse(vuelo.getDepartureTime().toString());
                        resultados.append("   - 📅 ").append(fecha.format(formateador))
                                .append(" | 💵 $").append(vuelo.getPrice()).append("\n");
                    }
                } else {
                    resultados.append("   - No hay vuelos de regreso disponibles.\n");
                }
            }

            return resultados.toString();

        } catch (Exception e) {
            return "Error al consultar la base de datos de vuelos.";
        }
    }

    public String buscarPaquetes(String destino, BigDecimal presupuestoMaximo) {
        try {
            List<TravelPackageResponse> paquetes = packageCatalogClient.searchPackages(destino, presupuestoMaximo).stream()
                    .filter(TravelPackageResponse::isActive)
                    .toList();

            if (paquetes.isEmpty()) {
                return "No hay paquetes a " + destino + " por debajo de $" + presupuestoMaximo;
            }

            StringBuilder resultados = new StringBuilder("Paquetes encontrados:\n");
            for (TravelPackageResponse p : paquetes) {
                resultados.append("- Paquete: ").append(p.getName())
                        .append(" | Precio: $").append(p.getBasePrice()).append("\n");
            }
            return resultados.toString();
        } catch (Exception e) {
            return "Error al consultar paquetes.";
        }
    }

    public String buscarHotelesPorCiudad(String ciudad) {
        try {
            List<HotelResponse> hoteles = hotelCatalogClient.findByCity(ciudad).stream()
                    .filter(h -> h.getHabitacionesDisponibles() > 0)
                    .limit(5)
                    .toList();

            if (hoteles.isEmpty()) {
                return "No hay hoteles disponibles en " + ciudad;
            }

            StringBuilder resultados = new StringBuilder("Hoteles encontrados:\n");
            for (HotelResponse h : hoteles) {
                resultados.append("- Hotel: ").append(h.getNombre())
                        .append(" | Habitaciones libres: ").append(h.getHabitacionesDisponibles()).append("\n");
            }
            return resultados.toString();
        } catch (Exception e) {
            return "Error al consultar hoteles.";
        }
    }
}