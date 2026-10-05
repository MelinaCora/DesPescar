package com.despescar.koiiaservice.dto.response;

import com.despescar.koiiaservice.enums.TipoOpcion;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * Una opción que propone KOI (spec 3.7). Los precios son orientativos: el carrito los recalcula
 * al agregar. excedeEn solo aparece cuando ninguna opción entra en el presupuesto.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KoiRecommendationResponse(
        String optionId,
        TipoOpcion tipo,
        KoiVueloOpcion vuelo,
        KoiHotelOpcion hotel,
        int viajeros,
        BigDecimal total,
        String moneda,
        BigDecimal excedeEn,
        String motivo) {
}
