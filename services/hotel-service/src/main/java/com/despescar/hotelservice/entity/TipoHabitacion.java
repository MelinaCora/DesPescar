package com.despescar.hotelservice.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tipos_habitacion")
@Getter
@Setter
@NoArgsConstructor
public class TipoHabitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    @Column(nullable = false)
    private int capacidad;

    @Column(name = "precio_por_noche", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioPorNoche;

    @Column(name = "cantidad_unidades", nullable = false)
    private int cantidadUnidades;

    @ElementCollection
    @CollectionTable(name = "tipo_habitacion_imagenes", joinColumns = @JoinColumn(name = "tipo_habitacion_id"))
    @OrderColumn(name = "orden")
    @Column(name = "url", nullable = false, length = 1000)
    private List<String> imagenes = new ArrayList<>();

    @Column(nullable = false)
    private boolean activo = true;
}
