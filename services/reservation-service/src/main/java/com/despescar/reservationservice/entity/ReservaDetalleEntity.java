package com.despescar.reservationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "detalle_pasajeros_reserva")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaDetalleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_grupo_id", nullable = false)
    private ReservaEntity reserva;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "pagador_id", nullable = false)
    private Long pagadorId;

    @Column(name = "numero_asiento", nullable = false, length = 10)
    private String numeroAsiento;

    @Column(name = "precio_asiento", nullable = false)
    private Double precio;

    @Column(name = "estado_pago", nullable = false)
    private String estadoPago;

    @Column(name = "nombre_pasajero", nullable = true, length = 100)
    private String nombrePasajero;

    @Column(name = "dni_pasaporte", nullable = true, length = 50)
    private String dniPasaporte;

}
