package com.despescar.reservationservice.entity;

import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.enums.PaymentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creador_id", nullable = false)
    private Long creadorId;

   @Column(name = "cantidad_pasajeros", nullable = false)
    private Integer cantidadPasajeros;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pago", nullable = false)
    private PaymentType tipoPago;

   @ElementCollection
    @CollectionTable(
            name = "reservation_flights",
            joinColumns = @JoinColumn(name = "reservation_id")
    )
    @Column(name = "flight_id")
    private List<UUID> flightIds = new ArrayList<>();

    @Column(name = "package_id")
    private Long packageId;

    @Column(name = "baggageIds")
    private List<UUID> baggageIds = new ArrayList<>();

    @Column(name = "hotel_id")
    private UUID hotelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_reserva", nullable = false)
    private ReservationStatus estado;

    @Column(name = "limite_tiempo", nullable = false)
    private LocalDateTime limiteTiempo;

    @Column(name = "creado_en", updatable = false, insertable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ReservationDetail> detalles = new ArrayList<>();

}