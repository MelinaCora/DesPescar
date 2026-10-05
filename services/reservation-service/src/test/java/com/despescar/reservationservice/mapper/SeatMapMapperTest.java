package com.despescar.reservationservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.despescar.reservationservice.dto.reservation.response.FlightSeatMapResponse;
import com.despescar.reservationservice.entity.Seat;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SeatMapMapperTest {

    private final SeatMapMapper mapper = new SeatMapMapper();

    private static Seat asiento(String numero, String estado) {
        Seat s = new Seat();
        s.setSeatUuid(UUID.randomUUID());
        s.setFlightId(UUID.randomUUID());
        s.setNumberSeat(numero);
        s.setStatusSeat(estado);
        return s;
    }

    @Test
    void elegirAsientoNoSeCobra() {
        FlightSeatMapResponse mapa = mapper.toSeatMapResponse(List.of(asiento("1A", "DISPONIBLE")), 2);

        assertThat(mapa.getFareClasses()).isNotEmpty();
        assertThat(mapa.getFareClasses().values())
                .allSatisfy(clase -> assertThat(clase.getPrice()).isEqualByComparingTo(BigDecimal.ZERO));
        assertThat(mapa.getTotalSelectedLimit()).isEqualTo(2);
    }

    @Test
    void unAsientoPagadoSeInformaOcupado() {
        Seat pagado = asiento("1B", "OCUPADO");

        FlightSeatMapResponse mapa = mapper.toSeatMapResponse(List.of(asiento("1A", "DISPONIBLE"), pagado), 2);

        FlightSeatMapResponse.LayoutItemDTO item = mapa.getLayout().stream()
                .filter(e -> "row".equals(e.getType()))
                .flatMap(e -> e.getItems().stream())
                .filter(i -> Objects.equals(pagado.getSeatUuid(), i.getSeatUuid()))
                .findFirst().orElseThrow();
        assertThat(item.getStatus()).isEqualTo("OCUPADO");
        assertThat(item.getDisplayNumber()).isEqualTo("1B");
    }
}
