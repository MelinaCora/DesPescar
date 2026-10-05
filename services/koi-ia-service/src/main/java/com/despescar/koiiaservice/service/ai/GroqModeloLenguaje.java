package com.despescar.koiiaservice.service.ai;

import com.despescar.koiiaservice.enums.MessageRole;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/** Groq vía Spring AI (starter de OpenAI con la base-url de Groq, ver application.properties). */
@Component
public class GroqModeloLenguaje implements KoiModeloLenguaje {

    private final ChatClient chatClient;

    public GroqModeloLenguaje(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String completar(String sistema, List<Turno> historial, String mensaje) {
        return chatClient.prompt()
                .system(sistema)
                .messages(mensajes(historial))
                .user(mensaje)
                .call()
                .content();
    }

    static List<Message> mensajes(List<Turno> historial) {
        return historial.stream()
                .filter(t -> t.texto() != null && !t.texto().isBlank())
                .map(t -> t.rol() == MessageRole.USER
                        ? (Message) new UserMessage(t.texto())
                        : new AssistantMessage(t.texto()))
                .toList();
    }
}
