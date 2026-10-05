package com.despescar.hotelservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** "Si se cancela con al menos horasAntes de anticipación, se reembolsa porcentajeReembolso". */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TramoCancelacion {

    @Column(name = "horas_antes", nullable = false)
    private int horasAntes;

    @Column(name = "porcentaje_reembolso", nullable = false)
    private int porcentajeReembolso;
}
