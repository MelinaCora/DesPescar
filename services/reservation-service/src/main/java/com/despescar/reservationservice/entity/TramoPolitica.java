package com.despescar.reservationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Copia de un tramo de la política del proveedor, guardada al reservar. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TramoPolitica {

    @Column(name = "horas_antes", nullable = false)
    private int horasAntes;

    @Column(name = "porcentaje_reembolso", nullable = false)
    private int porcentajeReembolso;
}
