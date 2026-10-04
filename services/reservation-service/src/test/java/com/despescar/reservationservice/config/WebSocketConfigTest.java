package com.despescar.reservationservice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

class WebSocketConfigTest {

    private static final String SECRET = "test-secret-key-for-websocket-security-1234567890";

    private ChannelInterceptor interceptor;
    private final MessageChannel channel = (message, timeout) -> true;

    @BeforeEach
    void setUp() {
        ChannelRegistration registration = new ChannelRegistration();
        new WebSocketConfig(new JwtService(SECRET)).configureClientInboundChannel(registration);
        interceptor = invokeGetInterceptors(registration).get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<ChannelInterceptor> invokeGetInterceptors(ChannelRegistration registration) {
        try {
            var method = ChannelRegistration.class.getDeclaredMethod("getInterceptors");
            method.setAccessible(true);
            return (List<ChannelInterceptor>) method.invoke(registration);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private String jwt(Long userId, String role, long ttlMs) {
        return Jwts.builder()
                .subject("alguien@mail.com")
                .claim("userId", userId)
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private Message<?> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void tokenValidoAsignaElUsuarioDelToken() {
        Message<?> message = connect("Bearer " + jwt(42L, "USER", 60_000));

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertEquals("42", accessor.getUser().getName());
    }

    @Test
    void sinTokenSeRechazaLaConexion() {
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(connect(null), channel));
    }

    @Test
    void tokenFalsoOVencidoSeRechaza() {
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(connect("Bearer cualquier-texto"), channel));
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(connect("Bearer " + jwt(42L, "USER", -60_000)), channel));
    }

    @Test
    void tokenSinUserIdSeRechaza() {
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(connect("Bearer " + jwt(null, "USER", 60_000)), channel));
    }

    @Test
    void enviarSinUsuarioAutenticadoSeRechaza() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(message, channel));
    }
}
