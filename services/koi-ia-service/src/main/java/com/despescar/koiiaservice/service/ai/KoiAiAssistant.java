package com.despescar.koiiaservice.service.ai;

import com.despescar.koiiaservice.dto.request.KoiConversationMessageRequest.MensajeHistorialDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
public class KoiAiAssistant {

    private final ChatClient chatClient;
    private final KoiTravelTools koiTravelTools;

    public KoiAiAssistant(ChatClient.Builder chatClientBuilder, KoiTravelTools koiTravelTools) {
        this.koiTravelTools = koiTravelTools;
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                Eres KOI AI, el asesor de viajes estrella de la agencia DesPescar.
                FECHA ACTUAL DEL SISTEMA: """ + LocalDate.now() + """
                
                REGLAS DE PERSONALIDAD Y FORMATO (¡MUY IMPORTANTES!):
                1. HABLA COMO UN HUMANO: Sé cálido, entusiasta, cercano y conversacional. Imagina que eres un agente de viajes real asesorando a un cliente por chat. Evita sonar como un robot o una enciclopedia.
                2. PROHIBIDO USAR TABLAS MARKDOWN (ej. | Columna | Columna |). NUNCA generes tablas.
                3. FORMATO VISUAL: Para listar opciones (como pagos, hoteles o servicios), usa viñetas cortas con emojis representativos y texto en negrita. Separa bien los párrafos para que la lectura sea ágil.
                4. Mantén tus respuestas concisas y al grano. No des discursos largos.
                5. DOMINIO ESTRICTO (OUT-OF-DOMAIN): Eres EXCLUSIVAMENTE un asesor de viajes de DesPescar. Si el usuario te pregunta o pide ayuda sobre CUALQUIER tema que no esté relacionado con turismo, vuelos, hoteles, paquetes o viajes (por ejemplo: recetas de cocina, programación, política, tareas escolares, etc.), ESTÁ ESTRICTAMENTE PROHIBIDO RESPONDER. Debes negarte educadamente, recordarle que tu especialidad son los viajes y redirigir la conversación hacia su próximo destino.
                
                REGLAS DE NEGOCIO Y BÚSQUEDA:
                5. NUNCA inventes precios ni disponibilidades.
                6. Pide la información faltante (origen, destino, presupuesto) charlando naturalmente.
                7. SI EL USUARIO YA TE DIO LOS DATOS PARA BUSCAR UN VUELO, DEBES RESPONDER ESTRICTAMENTE CON ESTAS ETIQUETAS Y NADA MÁS:
                <ACCION>BUSCAR_VUELO</ACCION>
                <ORIGEN>ciudad de origen</ORIGEN>
                <DESTINO>ciudad de destino</DESTINO>
                <FECHA_IDA>YYYY-MM-DD</FECHA_IDA>
                <FECHA_VUELTA>YYYY-MM-DD</FECHA_VUELTA>
                """)
                .build();
    }

    public String procesarConversacion(String mensajeUsuario, List<MensajeHistorialDto> history) {
        try {
            StringBuilder promptDinamico = new StringBuilder();
            if (history != null && !history.isEmpty()) {
                promptDinamico.append("[HISTORIAL]:\n");
                for (var h : history) {
                    if (h.getContent() == null || h.getContent().isBlank()) continue;
                    String emisor = ("user".equalsIgnoreCase(h.getRole())) ? "Usuario" : "KOI";
                    promptDinamico.append("- ").append(emisor).append(": ").append(h.getContent().trim()).append("\n");
                }
                promptDinamico.append("\n[MENSAJE ACTUAL]:\n").append(mensajeUsuario);
            } else {
                promptDinamico.append(mensajeUsuario);
            }

            String respuestaIa = chatClient.prompt().user(promptDinamico.toString()).call().content();

            if (respuestaIa != null && respuestaIa.contains("BUSCAR_VUELO") && respuestaIa.contains("<ORIGEN>")) {
                log.info("La IA solicitó búsqueda. Respuesta cruda: {}", respuestaIa);

                String origen = extraerEtiqueta(respuestaIa, "ORIGEN");
                String destino = extraerEtiqueta(respuestaIa, "DESTINO");
                String fechaIda = extraerEtiqueta(respuestaIa, "FECHA_IDA");
                String fechaVuelta = extraerEtiqueta(respuestaIa, "FECHA_VUELTA");

                String resultadoBD = koiTravelTools.buscarVuelosIdaYVuelta(origen, destino, fechaIda, fechaVuelta);

                log.info("Resultado de la base de datos: {}", resultadoBD);

                String promptConResultados = promptDinamico.toString() +
                        "\n\n==================================\n" +
                        "[NUEVA INSTRUCCIÓN DEL SISTEMA - CAMBIO DE ROL]:\n" +
                        "La búsqueda ya fue procesada en el backend. Resultados:\n" + resultadoBD +
                        "\n\nTAREA ACTUAL: Habla como humano. Saluda y ofrécele estos resultados exactos al usuario. " +
                        "YA NO DEBES USAR ETIQUETAS <ACCION>. (Si no hay fechas coincidentes, usa tu empatía para ofrecerle las alternativas listadas).";

                return chatClient.prompt().user(promptConResultados).call().content();
            }

            return respuestaIa;

        } catch (Exception e) {
            log.error("Error al invocar Groq en KOI AI: {}", e.getMessage(), e);
            return "¡Hola! Disculpa, tuve un pequeño inconveniente para consultar el sistema...";
        }
    }

    private String extraerEtiqueta(String texto, String etiqueta) {
        String tagInicio = "<" + etiqueta + ">";
        String tagFin = "</" + etiqueta + ">";
        if (texto.contains(tagInicio) && texto.contains(tagFin)) {
            return texto.substring(texto.indexOf(tagInicio) + tagInicio.length(), texto.indexOf(tagFin)).trim();
        }
        return "";
    }
}