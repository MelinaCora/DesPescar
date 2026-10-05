package com.despescar.koiiaservice.recomendador;

import java.util.List;
import java.util.UUID;

public record HotelCandidato(UUID hotelId, String nombre, String ciudad, int estrellas, String imagen,
                             List<HabitacionCandidata> habitaciones) {
}
