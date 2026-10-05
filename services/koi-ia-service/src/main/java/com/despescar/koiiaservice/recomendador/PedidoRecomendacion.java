package com.despescar.koiiaservice.recomendador;

import com.despescar.koiiaservice.enums.TipoOpcion;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Qué armar. presupuesto puede ser null (solo vuelo). checkOut es la fecha de vuelta; null en un
 * solo vuelo sin vuelta. Para COMBO y HOTEL, checkIn y checkOut son obligatorios.
 */
public record PedidoRecomendacion(TipoOpcion tipo, BigDecimal presupuesto, int viajeros,
                                  LocalDate checkIn, LocalDate checkOut) {
}
