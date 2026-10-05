package com.despescar.hotelservice.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj inyectable para que "hoy" y "ahora" se puedan fijar en los tests. Se mide en hora de
 * Argentina, sin depender de la zona del servidor.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Bean
    public Clock clock() {
        return Clock.system(ZONA);
    }
}
