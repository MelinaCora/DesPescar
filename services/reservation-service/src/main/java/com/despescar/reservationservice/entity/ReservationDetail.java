package com.despescar.reservationservice.entity;

import com.despescar.reservationservice.enums.ReservationPaymentState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "detalle_pasajeros_reserva")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDetail {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reserva_grupo_id",
            nullable = false
    )
    private Reservation reserva;



    /**
     * Usuario de DesPescar que ocupa este asiento
     */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;



    /**
     * Usuario que realiza el pago
     */
    @Column(name = "pagador_id", nullable = false)
    private Long pagadorId;



    @Column(
            name = "numero_asiento",
            nullable = false,
            length = 10
    )
    private String numeroAsiento;



    @Column(
            name = "precio_asiento",
            nullable = false
    )
    private Double precio;



    @Enumerated(EnumType.STRING)
    @Column(
            name = "estado_pago",
            nullable = false
    )
    private ReservationPaymentState estadoPago;



    /**
     * Pasajero que viajará en este asiento
     */
    @OneToOne(
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @JoinColumn(
            name = "pasajero_id"
    )
    private Passenger pasajero;



    /**
     * Equipaje extra asociado al pasajero
     */
    @OneToMany(
            mappedBy = "detalleReserva",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<ExtraBaggage> equipajes = new ArrayList<>();




    public void agregarEquipaje(ExtraBaggage equipaje) {

        equipajes.add(equipaje);

        equipaje.setDetalleReserva(this);
    }
    public void eliminarEquipaje(ExtraBaggage equipaje) {

        equipajes.remove(equipaje);

        equipaje.setDetalleReserva(null);
    }

    @Column(name = "nombre_pasajero", length = 100)
    private String nombrePasajero;


    @Column(name = "dni_pasaporte", length = 50)
    private String dniPasaporte;
}