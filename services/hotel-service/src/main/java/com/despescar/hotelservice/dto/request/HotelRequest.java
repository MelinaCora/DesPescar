package com.despescar.hotelservice.dto.request;

import com.despescar.hotelservice.dto.TramoDto;
import com.despescar.hotelservice.entity.Servicio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record HotelRequest(
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Size(max = 100) String ciudad,
        @NotBlank @Size(max = 100) String pais,
        @NotBlank @Size(max = 200) String direccion,
        @Min(1) @Max(5) int estrellas,
        @Size(max = 2000) String descripcion,
        boolean allInclusive,
        @NotNull List<@Pattern(regexp = "^https://\\S+$", message = "Las imágenes tienen que ser URLs https") String> imagenes,
        @NotNull Set<Servicio> servicios,
        @NotEmpty List<@Valid TramoDto> politicaCancelacion,
        LocalTime horaCheckIn,
        String zonaHoraria,
        Long adminUserId,
        @NotEmpty List<@Valid HabitacionRequest> habitaciones) {
}
