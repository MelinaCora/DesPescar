package com.despescar.reservationservice.entity;

import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * El carrito y, una vez pagado, la reserva. La parte de vuelo (flightIds, baggageIds,
 * cantidadPasajeros, detalles) es opcional; las estadías van en {@link #estadias}.
 */
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

    /** 0 si el carrito no tiene vuelo. */
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
    @Builder.Default
    private List<UUID> flightIds = new ArrayList<>();

    @Column(name = "package_id")
    private Long packageId;

    /** Ids de las tarifas elegidas, en el mismo orden que flightIds. */
    @Column(name = "baggageIds")
    @Builder.Default
    private List<UUID> baggageIds = new ArrayList<>();

    /** En desuso desde E2: las estadías van en {@link #estadias}. */
    @Column(name = "hotel_id")
    private UUID hotelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_reserva", nullable = false)
    private ReservationStatus estado;

    @Column(name = "limite_tiempo", nullable = false)
    private LocalDateTime limiteTiempo;

    @Column(name = "creado_en", updatable = false, insertable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ReservationDetail> detalles = new ArrayList<>();

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<EstadiaHotel> estadias = new ArrayList<>();

    /** Precio por pasajero de todos los tramos, calculado con flight-service al entrar al carrito. */
    @Column(name = "precio_vuelo_por_pasajero", precision = 12, scale = 2)
    private BigDecimal precioVueloPorPasajero;

    /** Nombres de las tarifas elegidas ("Light / Standard"). */
    @Column(name = "tarifas_vuelo", length = 120)
    private String tarifasVuelo;

    /** Fecha y hora de salida del primer tramo. */
    @Column(name = "salida_vuelo")
    private LocalDateTime salidaVuelo;

    @Column(name = "motivo_cancelacion", length = 60)
    private String motivoCancelacion;

    /**
     * Igual a creadorId mientras el carrito esta INICIADA o PENDIENTE_PAGO y null en cualquier otro
     * estado. Su indice unico garantiza un solo carrito abierto por usuario (NULL no choca en MySQL).
     */
    @Column(name = "carrito_abierto_de", unique = true)
    private Long carritoAbiertoDe;

    /** Unico lugar donde se deriva carritoAbiertoDe: cualquier cambio de estado queda sincronizado. */
    @PrePersist
    @PreUpdate
    void sincronizarCarritoAbierto() {
        boolean abierto = estado == ReservationStatus.INICIADA || estado == ReservationStatus.PENDIENTE_PAGO;
        carritoAbiertoDe = abierto ? creadorId : null;
    }
}
