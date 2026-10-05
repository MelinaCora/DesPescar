package com.despescar.reservationservice.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj inyectable: "ahora" se mide en hora de Argentina y se puede fijar en los tests. */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Bean
    public Clock clock() {
        return Clock.system(ZONA);
    }
}
