package com.despescar.reservationservice.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name="pasajeros")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Passenger {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(
            name = "nombre_completo",
            nullable = false,
            length = 100
    )
    private String nombreCompleto;


    @Column(
            name = "dni_pasaporte",
            nullable = false,
            length = 50
    )
    private String dniPasaporte;


}