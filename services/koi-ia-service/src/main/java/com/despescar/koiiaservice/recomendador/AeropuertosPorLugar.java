package com.despescar.koiiaservice.recomendador;

import com.despescar.koiiaservice.client.dto.AirportResponse;
import com.despescar.koiiaservice.domain.TextoBusqueda;
import java.util.List;

/**
 * Traduce lo que dijo el usuario ("Buenos Aires", "bariloche", "EZE") a códigos IATA del
 * catálogo. La búsqueda de vuelos trabaja por código y una ciudad puede tener varios aeropuertos.
 */
public final class AeropuertosPorLugar {

    /** Largo minimo del texto para aceptar coincidencias parciales. */
    private static final int MIN_PARCIAL = 3;

    private AeropuertosPorLugar() {
    }

    public static List<String> resolver(List<AirportResponse> aeropuertos, String lugar) {
        String buscado = TextoBusqueda.normalizar(lugar);
        if (buscado.isEmpty()) {
            return List.of();
        }
        List<String> porCodigo = aeropuertos.stream()
                .map(AirportResponse::getCode)
                .filter(code -> code != null && code.equalsIgnoreCase(buscado))
                .toList();
        if (!porCodigo.isEmpty()) {
            return porCodigo;
        }
        List<String> porCiudadExacta = aeropuertos.stream()
                .filter(a -> TextoBusqueda.normalizar(a.getCity()).equals(buscado))
                .map(AirportResponse::getCode)
                .sorted()
                .toList();
        if (!porCiudadExacta.isEmpty() || buscado.length() < MIN_PARCIAL) {
            return porCiudadExacta;
        }
        return aeropuertos.stream()
                .filter(a -> {
                    String ciudad = TextoBusqueda.normalizar(a.getCity());
                    return !ciudad.isEmpty() && (ciudad.contains(buscado) || buscado.contains(ciudad));
                })
                .map(AirportResponse::getCode)
                .sorted()
                .toList();
    }
}
