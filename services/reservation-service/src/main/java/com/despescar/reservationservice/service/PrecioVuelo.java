package com.despescar.reservationservice.service;

import com.despescar.reservationservice.client.FlightClient;
import com.despescar.reservationservice.dto.flight.response.FareLookupResponse;
import com.despescar.reservationservice.dto.flight.response.FlightLookupResponse;
import com.despescar.reservationservice.exception.BookingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Precio del vuelo del carrito (D1): por pasajero, la suma por tramo del precio del vuelo y el
 * transparentFinalPrice de la tarifa elegida para ese tramo. Valida que la tarifa sea del vuelo,
 * que esté en pesos (D20), que el vuelo se pueda reservar y que tenga asientos.
 */
@Component
public class PrecioVuelo {

    private static final Set<String> NO_RESERVABLES = Set.of(
            "CANCELLED", "CANCELED", "DEPARTED", "ARRIVED", "LANDED", "COMPLETED");

    private final FlightClient flightClient;

    public PrecioVuelo(FlightClient flightClient) {
        this.flightClient = flightClient;
    }

    public record Cotizacion(BigDecimal precioPorPasajero, String tarifas, LocalDateTime salida) {
    }

    public Cotizacion cotizar(List<UUID> flightIds, List<UUID> fareIds, int pasajeros) {
        if (flightIds == null || flightIds.isEmpty() || fareIds == null || fareIds.size() != flightIds.size()) {
            throw new BookingException("TARIFAS_INVALIDAS", "Elegí una tarifa por cada tramo.", HttpStatus.BAD_REQUEST);
        }
        BigDecimal total = BigDecimal.ZERO;
        List<String> nombres = new ArrayList<>();
        LocalDateTime salida = null;
        for (int i = 0; i < flightIds.size(); i++) {
            FlightLookupResponse vuelo = flightClient.getFlightByNumber(flightIds.get(i));
            validarEstado(vuelo);
            if (vuelo.getAvailableSeats() != null && vuelo.getAvailableSeats() < pasajeros) {
                throw new BookingException("SIN_DISPONIBILIDAD", "El vuelo no tiene suficientes asientos.", HttpStatus.CONFLICT);
            }
            FareLookupResponse tarifa = tarifaDelVuelo(vuelo, fareIds.get(i));
            if (vuelo.getPrice() == null || tarifa.getPrice() == null || tarifa.getPrice().getTransparentFinalPrice() == null) {
                throw new BookingException("TARIFA_SIN_PRECIO", "Flight-Service no informó el precio del vuelo.", HttpStatus.BAD_GATEWAY);
            }
            if (!CarritoCalculo.MONEDA.equalsIgnoreCase(tarifa.getPrice().getCurrency())) {
                throw new BookingException("MONEDA_NO_SOPORTADA", "El carrito solo acepta tarifas en pesos.", HttpStatus.CONFLICT);
            }
            total = total.add(redondear(vuelo.getPrice())).add(redondear(tarifa.getPrice().getTransparentFinalPrice()));
            if (i == 0) {
                salida = vuelo.getDepartureTime();
            }
            if (tarifa.getName() != null && !nombres.contains(tarifa.getName())) {
                nombres.add(tarifa.getName());
            }
        }
        return new Cotizacion(total.setScale(2, RoundingMode.HALF_UP),
                nombres.isEmpty() ? null : String.join(" / ", nombres), salida);
    }

    private static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static void validarEstado(FlightLookupResponse vuelo) {
        if (vuelo.getStatus() == null || vuelo.getStatus().isBlank()) {
            throw new BookingException("ESTADO_VUELO_INVALIDO", "Estado de vuelo inválido.", HttpStatus.BAD_GATEWAY);
        }
        if (NO_RESERVABLES.contains(vuelo.getStatus().trim().toUpperCase(Locale.ROOT))) {
            throw new BookingException("VUELO_NO_RESERVABLE", "El vuelo no admite reservas.", HttpStatus.CONFLICT);
        }
    }

    private static FareLookupResponse tarifaDelVuelo(FlightLookupResponse vuelo, UUID fareId) {
        List<FareLookupResponse> tarifas = vuelo.getFares() == null ? List.of() : vuelo.getFares();
        return tarifas.stream()
                .filter(t -> fareId != null && fareId.equals(t.getId()))
                .findFirst()
                .orElseThrow(() -> new BookingException("TARIFA_INVALIDA",
                        "La tarifa elegida no corresponde al vuelo " + vuelo.getFlightNumber() + ".", HttpStatus.BAD_REQUEST));
    }
}
