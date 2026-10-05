package com.despescar.reservationservice.dto.reservation.response;

import com.despescar.reservationservice.enums.EstadoItem;
import com.despescar.reservationservice.enums.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Carrito o reserva (contrato C4). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    private Long idCarrito;

    private Long creadorId;

    private Long packageId;

    private String vueloCodigo;

    /** En desuso desde E2. */
    private UUID hotelId;

    private ReservationStatus estadoGeneral;

    private Long segundosRestantes;

    private BigDecimal montoTotal;

    private String moneda;

    private Integer cantidadItems;

    private Boolean datosCompletos;

    private VueloCarritoDTO vuelo;

    private List<EstadiaDTO> estadias;

    private List<AsientoDetalleDTO> asientos;

    private LocalDateTime creadoEn;

    /** Solo en reservas canceladas. */
    private String motivoCancelacion;

    /** Solo si la canceló su dueño: cuándo, cuánto se devuelve y si el reembolso todavía no salió. */
    private LocalDateTime canceladaEn;

    private BigDecimal montoReembolsado;

    private Boolean reembolsoPendiente;

    /** La reserva se pagó entre varios (el reembolso vuelve a cada pagador en proporción). */
    private Boolean pagoEnGrupo;

    @Data
    @Builder
    public static class AsientoDetalleDTO {
        private String asientoIda;
        private String asientoVuelta;

        private Long pagadorId;
        private BigDecimal precioCobrado;
        private String estadoPago;

        private String nombrePasajero;
        private String dniPasaporte;

        private String tarifaNombre;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VueloCarritoDTO {
        private List<UUID> flightIds;
        private List<UUID> fareIds;
        private Integer cantidadPasajeros;
        private BigDecimal precioPorPasajero;
        private BigDecimal subtotal;
        private LocalDateTime salida;
        private String tarifas;
        private Boolean pasajerosCargados;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EstadiaDTO {
        private Long id;
        private UUID hotelId;
        private String hotelNombre;
        private String ciudad;
        private UUID tipoHabitacionId;
        private String tipoHabitacionNombre;
        private LocalDate checkIn;
        private LocalDate checkOut;
        private Long noches;
        private Integer cantidadHabitaciones;
        private Integer huespedes;
        private BigDecimal precioTotal;
        private String moneda;
        private LocalTime horaCheckIn;
        private String zonaHoraria;
        private List<TramoDTO> politicaCancelacion;
        private String titularNombre;
        private String titularDni;
        private String titularTelefono;
        private EstadoItem estado;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TramoDTO {
        private int horasAntes;
        private int porcentajeReembolso;
    }
}
