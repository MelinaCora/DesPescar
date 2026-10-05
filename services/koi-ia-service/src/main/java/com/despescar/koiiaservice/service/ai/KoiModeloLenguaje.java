package com.despescar.koiiaservice.service.ai;

import com.despescar.koiiaservice.enums.MessageRole;
import java.util.List;

/**
 * Puerto hacia el modelo de lenguaje. El servicio de conversación depende de esta interfaz y
 * los tests la reemplazan por un doble: ningún test necesita Groq ni GROQ_API_KEY.
 */
public interface KoiModeloLenguaje {

    /** Un turno anterior de la conversación, tal como quedó guardado en el servidor. */
    record Turno(MessageRole rol, String texto) {
    }

    /**
     * Devuelve el texto crudo del modelo (se espera JSON, ver KoiPrompt). Puede lanzar cualquier
     * RuntimeException si el proveedor falla: el servicio la trata como "modelo caído".
     */
    String completar(String sistema, List<Turno> historial, String mensaje);
}
