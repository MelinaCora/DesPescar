package com.despescar.koiiaservice.service.ai;

import com.despescar.koiiaservice.dto.request.KoiConversationMessageRequest.MensajeHistorialDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoiAiAssistantTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private KoiTravelTools koiTravelTools;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    private KoiAiAssistant assistant;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callSpec);
        assistant = new KoiAiAssistant(chatClientBuilder, koiTravelTools);
    }

    @Test
    void respuestaNormalSeDevuelveTalCualSinConsultarVuelos() {
        when(callSpec.content()).thenReturn("¡Hola! ¿A dónde querés viajar?");

        String respuesta = assistant.procesarConversacion("hola", null);

        assertEquals("¡Hola! ¿A dónde querés viajar?", respuesta);
        verify(koiTravelTools, never()).buscarVuelosIdaYVuelta(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void elHistorialSeIncluyeEnElPromptYSeIgnoranLosMensajesVacios() {
        when(callSpec.content()).thenReturn("ok");
        List<MensajeHistorialDto> history = List.of(historial("user", "quiero ir a Bariloche"), historial("assistant", "  "));

        assistant.procesarConversacion("con 2 personas", history);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(prompt.capture());
        assertTrue(prompt.getValue().contains("[HISTORIAL]"));
        assertTrue(prompt.getValue().contains("- Usuario: quiero ir a Bariloche"));
        assertTrue(prompt.getValue().contains("[MENSAJE ACTUAL]:\ncon 2 personas"));
        assertTrue(!prompt.getValue().contains("- KOI:"));
    }

    @Test
    void cuandoLaIaPideBuscarVueloConsultaLasHerramientasYPideUnaSegundaRespuesta() {
        String accion = "<ACCION>BUSCAR_VUELO</ACCION><ORIGEN>Buenos Aires</ORIGEN><DESTINO>Cordoba</DESTINO>"
                + "<FECHA_IDA>2026-10-18</FECHA_IDA><FECHA_VUELTA>2026-10-25</FECHA_VUELTA>";
        when(callSpec.content()).thenReturn(accion, "Encontré estos vuelos");
        when(koiTravelTools.buscarVuelosIdaYVuelta("Buenos Aires", "Cordoba", "2026-10-18", "2026-10-25"))
                .thenReturn("VUELO AR1234");

        String respuesta = assistant.procesarConversacion("quiero volar a Cordoba", null);

        assertEquals("Encontré estos vuelos", respuesta);
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(requestSpec, times(2)).user(prompts.capture());
        assertTrue(prompts.getAllValues().get(1).contains("VUELO AR1234"));
    }

    @Test
    void siFallaLaIaDevuelveUnMensajeAmableEnLugarDeLaExcepcion() {
        when(callSpec.content()).thenThrow(new IllegalStateException("401 de Groq"));

        String respuesta = assistant.procesarConversacion("hola", null);

        assertTrue(respuesta.contains("inconveniente"));
    }

    private static MensajeHistorialDto historial(String role, String content) {
        MensajeHistorialDto dto = new MensajeHistorialDto();
        dto.setRole(role);
        dto.setContent(content);
        return dto;
    }
}
