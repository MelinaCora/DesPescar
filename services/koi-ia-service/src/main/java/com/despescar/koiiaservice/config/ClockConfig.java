package com.despescar.koiiaservice.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** "Hoy" para KOI es el día en Argentina (fechas pasadas, prompt con la fecha). */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Bean
    public Clock clock() {
        return Clock.system(ZONA);
    }
}
