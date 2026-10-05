package com.despescar.reservationservice.entity;

import com.despescar.reservationservice.enums.EstadoParte;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una parte de un pago en grupo. La 1 es siempre del organizador. Un usuario tiene como mucho una
 * parte por grupo (índice único; las LIBRE tienen usuario NULL y no chocan entre sí).
 */
@Entity
@Table(name = "partes_grupo", uniqueConstraints = {
        @UniqueConstraint(name = "uk_parte_grupo_numero", columnNames = {"grupo_id", "numero"}),
        @UniqueConstraint(name = "uk_parte_grupo_usuario", columnNames = {"grupo_id", "usuario_id"})})
@Getter
@Setter
@NoArgsConstructor
public class ParteGrupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_id", nullable = false)
    private GrupoPago grupo;

    @Column(nullable = false)
    private int numero;

    /** null mientras está LIBRE. */
    @Column(name = "usuario_id")
    private Long usuarioId;

    /** Cómo se muestra al grupo; lo elige quien toma la parte. Nunca un email ni un id. */
    @Column(length = 30)
    private String apodo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoParte estado;

    /** tokenPago del cobro que pagó esta parte (idempotencia y PAGO_DUPLICADO). */
    @Column(name = "token_pago", length = 120)
    private String tokenPago;

    @Column(name = "pagada_en")
    private LocalDateTime pagadaEn;

    public static ParteGrupo libre(int numero, BigDecimal monto) {
        ParteGrupo p = new ParteGrupo();
        p.setNumero(numero);
        p.setMonto(monto.setScale(2, RoundingMode.HALF_UP));
        p.setEstado(EstadoParte.LIBRE);
        return p;
    }

    public void tomar(Long usuario, String apodoVisible) {
        this.usuarioId = usuario;
        this.apodo = apodoVisible;
        this.estado = EstadoParte.TOMADA;
    }

    public void liberar() {
        this.usuarioId = null;
        this.apodo = null;
        this.estado = EstadoParte.LIBRE;
    }

    public void pagar(String token, LocalDateTime cuando) {
        this.tokenPago = token;
        this.pagadaEn = cuando;
        this.estado = EstadoParte.PAGADA;
    }
}
