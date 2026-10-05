package com.despescar.hotelservice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    void elRelojMideHoyEnHoraArgentina() {
        assertEquals(ZoneId.of("America/Argentina/Buenos_Aires"), new ClockConfig().clock().getZone());
        assertEquals(ZoneId.of("America/Argentina/Buenos_Aires"), ClockConfig.ZONA);
    }
}
