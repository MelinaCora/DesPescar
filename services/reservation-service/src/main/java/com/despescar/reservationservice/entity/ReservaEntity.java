package com.despescar.reservationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "reservas_grupo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creador_id", nullable = false)
    private Long creadorId;

    @Column(name = "vuelo_codigo", nullable = false, length = 20)
    private String vueloCodigo;

    @Column(name = "estado_reserva", nullable = false)
    private String estado;

    @Column(name = "limite_tiempo", nullable = false)
    private LocalDateTime limiteTiempo;

    @Column(name = "creado_en", updatable = false, insertable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ReservaDetalleEntity> detalles;


}
