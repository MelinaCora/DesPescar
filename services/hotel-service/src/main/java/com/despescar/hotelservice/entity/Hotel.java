package com.despescar.hotelservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "hoteles")
@Getter
@Setter
@NoArgsConstructor
public class Hotel {

    public static final LocalTime HORA_CHECK_IN_POR_DEFECTO = LocalTime.of(14, 0);
    public static final String ZONA_POR_DEFECTO = "America/Argentina/Buenos_Aires";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String ciudad;

    @Column(nullable = false)
    private String pais;

    @Column(nullable = false)
    private String direccion;

    @Column(nullable = false)
    private int estrellas;

    @Column(length = 2000)
    private String descripcion;

    @Column(name = "all_inclusive", nullable = false)
    private boolean allInclusive;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "hora_check_in", nullable = false)
    private LocalTime horaCheckIn = HORA_CHECK_IN_POR_DEFECTO;

    @Column(name = "zona_horaria", nullable = false)
    private String zonaHoraria = ZONA_POR_DEFECTO;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "calificacion_promedio", nullable = false)
    private double calificacionPromedio;

    @Column(name = "cantidad_resenas", nullable = false)
    private int cantidadResenas;

    @ElementCollection
    @CollectionTable(name = "hotel_imagenes", joinColumns = @JoinColumn(name = "hotel_id"))
    @OrderColumn(name = "orden")
    @Column(name = "url", nullable = false, length = 1000)
    private List<String> imagenes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "hotel_servicios", joinColumns = @JoinColumn(name = "hotel_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "servicio", nullable = false)
    private Set<Servicio> servicios = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "hotel_politica_cancelacion", joinColumns = @JoinColumn(name = "hotel_id"))
    @OrderColumn(name = "orden")
    private List<TramoCancelacion> politicaCancelacion = new ArrayList<>();

    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("precioPorNoche ASC")
    private List<TipoHabitacion> habitaciones = new ArrayList<>();

    public void agregarHabitacion(TipoHabitacion habitacion) {
        habitacion.setHotel(this);
        habitaciones.add(habitacion);
    }
}
