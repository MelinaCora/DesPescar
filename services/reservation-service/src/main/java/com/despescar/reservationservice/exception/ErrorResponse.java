package com.despescar.reservationservice.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ErrorResponse {

    private String codigo;
    private String mensaje;
    private LocalDateTime timestamp;

}
