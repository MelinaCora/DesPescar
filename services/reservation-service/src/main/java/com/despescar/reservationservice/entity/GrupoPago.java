package com.despescar.reservationservice.entity;

import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.EstadoParte;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pago en grupo de una reserva (D-b1). Lo crea el organizador (el creador del carrito) y vive
 * hasta venceEn; el token del enlace es lo único que se comparte. Orden de locks: el grupo antes
 * que la reserva.
 */
@Entity
@Table(name = "grupos_pago")
@Getter
@Setter
@NoArgsConstructor
public class GrupoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(name = "organizador_id", nullable = false)
    private Long organizadorId;

    /** 32 bytes aleatorios en base64url (D-b6). Nunca se escribe en logs. */
    @Column(name = "token_enlace", nullable = false, unique = true, length = 43)
    private String tokenEnlace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoGrupo estado;

    /** Hora argentina, igual que limiteTiempo de la reserva. */
    @Column(name = "vence_en", nullable = false)
    private LocalDateTime venceEn;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "motivo_cierre", length = 60)
    private String motivoCierre;

    /** Hay partes pagadas que payment-service todavía no confirmó haber reembolsado (D-b13). */
    @Column(name = "reembolsos_pendientes", nullable = false)
    private boolean reembolsosPendientes;

    @OneToMany(mappedBy = "grupo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("numero ASC")
    private List<ParteGrupo> partes = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private Long version;

    public void agregarParte(ParteGrupo parte) {
        parte.setGrupo(this);
        partes.add(parte);
    }

    public boolean abierto() {
        return estado == EstadoGrupo.ABIERTO;
    }

    public Optional<ParteGrupo> parte(int numero) {
        return partes.stream().filter(p -> p.getNumero() == numero).findFirst();
    }

    public Optional<ParteGrupo> parteDe(Long usuarioId) {
        if (usuarioId == null) {
            return Optional.empty();
        }
        return partes.stream().filter(p -> usuarioId.equals(p.getUsuarioId())).findFirst();
    }

    public boolean tieneParte(Long usuarioId) {
        return parteDe(usuarioId).isPresent();
    }

    public boolean hayPagadas() {
        return partes.stream().anyMatch(p -> p.getEstado() == EstadoParte.PAGADA);
    }

    public boolean todasPagadas() {
        return !partes.isEmpty() && partes.stream().allMatch(p -> p.getEstado() == EstadoParte.PAGADA);
    }

    public long cantidadPagadas() {
        return partes.stream().filter(p -> p.getEstado() == EstadoParte.PAGADA).count();
    }

    /** Algún amigo (no el organizador) ya tomó una parte. */
    public boolean hayInvitados() {
        return partes.stream().anyMatch(p -> p.getUsuarioId() != null && !Objects.equals(p.getUsuarioId(), organizadorId));
    }

    public BigDecimal montoPagado() {
        return partes.stream()
                .filter(p -> p.getEstado() == EstadoParte.PAGADA)
                .map(ParteGrupo::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
