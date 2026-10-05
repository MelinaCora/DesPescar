package com.despescar.reservationservice.entity;

import com.despescar.reservationservice.enums.EstadoItem;
import com.despescar.reservationservice.enums.PaymentStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una estadía del carrito: copia lo que devolvió hotel-service al retener (precio, política, horario). */
@Entity
@Table(name = "estadias_hotel")
@Getter
@Setter
@NoArgsConstructor
public class EstadiaHotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "hotel_id", nullable = false)
    private UUID hotelId;

    @Column(name = "hotel_nombre", nullable = false)
    private String hotelNombre;

    @Column(nullable = false)
    private String ciudad;

    @Column(name = "tipo_habitacion_id", nullable = false)
    private UUID tipoHabitacionId;

    @Column(name = "tipo_habitacion_nombre", nullable = false)
    private String tipoHabitacionNombre;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "cantidad_habitaciones", nullable = false)
    private int cantidadHabitaciones;

    @Column(nullable = false)
    private int huespedes;

    @Column(name = "retencion_id", nullable = false)
    private UUID retencionId;

    @Column(name = "precio_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioTotal;

    @Column(nullable = false, length = 3)
    private String moneda;

    @ElementCollection
    @CollectionTable(name = "estadia_politica_cancelacion", joinColumns = @JoinColumn(name = "estadia_id"))
    @OrderColumn(name = "orden")
    private List<TramoPolitica> politicaCancelacion = new ArrayList<>();

    @Column(name = "hora_check_in", nullable = false)
    private LocalTime horaCheckIn;

    @Column(name = "zona_horaria", nullable = false)
    private String zonaHoraria;

    @Column(name = "titular_nombre", length = 100)
    private String titularNombre;

    @Column(name = "titular_dni", length = 20)
    private String titularDni;

    @Column(name = "titular_telefono", length = 30)
    private String titularTelefono;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoItem estado = EstadoItem.ACTIVA;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago", nullable = false)
    private PaymentStatus estadoPago = PaymentStatus.PENDIENTE;

    @Column(name = "monto_reembolsado", precision = 12, scale = 2)
    private BigDecimal montoReembolsado;
}
