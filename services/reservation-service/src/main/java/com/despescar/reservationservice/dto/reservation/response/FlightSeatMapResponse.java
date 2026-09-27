package com.despescar.reservationservice.dto.reservation.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class FlightSeatMapResponse {
    private String aircraftName;
    private Integer totalSelectedLimit;
    private Map<String, FareClassDTO> fareClasses;
    private List<LayoutElementDTO> layout;

    @Data
    @Builder
    public static class FareClassDTO {
        private String name;
        private BigDecimal price;
        private String colorKey;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LayoutElementDTO {
        private String type;

        private Integer rowNumber;
        private List<LayoutItemDTO> items;

        private String amenityType;
        private AmenityMetadataDTO metadata;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LayoutItemDTO {
        private String type;

        private UUID seatUuid;
        private String displayNumber;
        private String fareClass;
        private String fareClassName;
        private String status;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AmenityMetadataDTO {
        private String title;
        private String subtitle;
        private String priceTag;
        private String color;
        private List<String> icons;
    }
}