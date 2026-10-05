package com.despescar.koiiaservice.dto.response;

import com.despescar.koiiaservice.enums.MessageRole;
import java.util.List;

/** Un mensaje del historial; opciones vacía para los del usuario y los de KOI sin opciones. */
public record KoiMensajeResponse(MessageRole rol, String texto, List<KoiRecommendationResponse> opciones) {
}
