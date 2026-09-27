package com.despescar.reservationservice.mapper;

import com.despescar.reservationservice.dto.reservation.response.FlightSeatMapResponse;
import com.despescar.reservationservice.entity.Seat;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SeatMapMapper {

    // Diccionario estático en memoria: Centralizado y eficiente
    private static final Map<String, FlightSeatMapResponse.FareClassDTO> FARE_CLASSES = Map.of(
            "economy", FlightSeatMapResponse.FareClassDTO.builder().name("Estándar").price(BigDecimal.valueOf(5581)).colorKey("slate").build(),
            "preferred", FlightSeatMapResponse.FareClassDTO.builder().name("Salida Rápida").price(BigDecimal.valueOf(9302)).colorKey("emerald").build(),
            "exit_row", FlightSeatMapResponse.FareClassDTO.builder().name("Salida de Emergencia").price(BigDecimal.valueOf(9302)).colorKey("blue").build(),
            "vip", FlightSeatMapResponse.FareClassDTO.builder().name("Primera Fila").price(BigDecimal.valueOf(12403)).colorKey("gold").build()
    );

    public FlightSeatMapResponse toSeatMapResponse(List<Seat> seats, int limiteSeleccion) {
        Map<Integer, List<Seat>> seatsByRow = seats.stream()
                .collect(Collectors.groupingBy(seat -> extractRowNumber(seat.getNumberSeat())));

        Map<String, Seat> seatMap = seats.stream()
                .collect(Collectors.toMap(Seat::getNumberSeat, Function.identity()));

        List<FlightSeatMapResponse.LayoutElementDTO> layout = new ArrayList<>();
        layout.add(createServices());

        List<Integer> rowNumbers = new ArrayList<>(seatsByRow.keySet());
        Collections.sort(rowNumbers);

        for (Integer rowNum : rowNumbers) {
            if (rowNum == 13 || rowNum == 14) {
                layout.add(createEmergencyExit());
            }

            layout.add(FlightSeatMapResponse.LayoutElementDTO.builder()
                    .type("row")
                    .rowNumber(rowNum)
                    .items(buildRowItems(seatMap, rowNum))
                    .build());
        }

        layout.add(createServices());

        return FlightSeatMapResponse.builder()
                .aircraftName("Airbus A320")
                .totalSelectedLimit(limiteSeleccion)
                .fareClasses(FARE_CLASSES)
                .layout(layout)
                .build();
    }

    private List<FlightSeatMapResponse.LayoutItemDTO> buildRowItems(Map<String, Seat> seatMap, int rowNum) {
        List<FlightSeatMapResponse.LayoutItemDTO> items = new ArrayList<>();
        String[] leftSide = {"A", "B", "C"};
        String[] rightSide = {"D", "E", "F"};

        for (String letter : leftSide) {
            items.add(findOrCreateSeat(seatMap, rowNum, letter));
        }

        items.add(FlightSeatMapResponse.LayoutItemDTO.builder().type("aisle").build());

        for (String letter : rightSide) {
            items.add(findOrCreateSeat(seatMap, rowNum, letter));
        }

        return items;
    }

    private FlightSeatMapResponse.LayoutItemDTO findOrCreateSeat(Map<String, Seat> seatMap, int rowNum, String letter) {
        String targetSeat = rowNum + letter;
        Seat s = seatMap.get(targetSeat);

        if (s != null) {
            String fareKey;
            if (rowNum <= 2) {
                fareKey = "vip";
            } else if (rowNum >= 3 && rowNum <= 6) {
                fareKey = "preferred";
            } else if (rowNum == 13 || rowNum == 14) {
                fareKey = "exit_row";
            } else {
                fareKey = "economy";
            }

            return FlightSeatMapResponse.LayoutItemDTO.builder()
                    .type("seat")
                    .seatUuid(s.getSeatUuid())
                    .displayNumber(s.getNumberSeat())
                    .status(s.getStatusSeat())
                    .fareClass(fareKey) // BIEN: Inyecta "economy", "vip", etc. para enlazar con fareClasses del JSON
                    .build();
        } else {
            return FlightSeatMapResponse.LayoutItemDTO.builder().type("empty").build();
        }
    }

    private int extractRowNumber(String seatNumber) {
        if (seatNumber == null) return 0;
        String numStr = seatNumber.replaceAll("[^0-9]", "");
        return numStr.isEmpty() ? 0 : Integer.parseInt(numStr);
    }

    private FlightSeatMapResponse.LayoutElementDTO createServices() {
        return FlightSeatMapResponse.LayoutElementDTO.builder()
                .type("amenity")
                .amenityType("services")
                .metadata(FlightSeatMapResponse.AmenityMetadataDTO.builder()
                        .title("Baños y Cafetería")
                        .icons(List.of("wc", "coffee"))
                        .build())
                .build();
    }

    private FlightSeatMapResponse.LayoutElementDTO createEmergencyExit() {
        return FlightSeatMapResponse.LayoutElementDTO.builder()
                .type("amenity")
                .amenityType("emergency")
                .metadata(FlightSeatMapResponse.AmenityMetadataDTO.builder()
                        .title("Salida de Emergencias")
                        .build())
                .build();
    }
}
