package com.despescar.koiiaservice.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.koiiaservice.enums.MessageRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

class GroqModeloLenguajeTest {

    @Test
    void mandaElSistemaElHistorialComoTurnosYElMensaje() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.messages(anyList())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(call);
        when(call.content()).thenReturn("{\"viajeros\":2}");

        String respuesta = new GroqModeloLenguaje(builder).completar("SISTEMA",
                List.of(new KoiModeloLenguaje.Turno(MessageRole.KOI, "¡Hola! Soy KOI"),
                        new KoiModeloLenguaje.Turno(MessageRole.USER, "  "),
                        new KoiModeloLenguaje.Turno(MessageRole.USER, "Quiero ir a Bariloche")),
                "somos 2");

        assertEquals("{\"viajeros\":2}", respuesta);
        verify(spec).system("SISTEMA");
        verify(spec).user("somos 2");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> mensajes = ArgumentCaptor.forClass(List.class);
        verify(spec).messages(mensajes.capture());
        assertEquals(2, mensajes.getValue().size());
        assertInstanceOf(AssistantMessage.class, mensajes.getValue().get(0));
        assertInstanceOf(UserMessage.class, mensajes.getValue().get(1));
        assertEquals("Quiero ir a Bariloche", mensajes.getValue().get(1).getText());
    }
}
