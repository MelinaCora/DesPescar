package com.despescar.reservationservice.service;

import com.despescar.reservationservice.config.ClockConfig;
import com.despescar.reservationservice.dto.grupo.GrupoResponse;
import com.despescar.reservationservice.dto.grupo.GrupoResumenResponse;
import com.despescar.reservationservice.entity.GrupoPago;
import com.despescar.reservationservice.entity.ParteGrupo;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.mapper.GrupoMapper;
import com.despescar.reservationservice.repository.BookingRepository;
import com.despescar.reservationservice.repository.GrupoPagoRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pago en grupo de una reserva (D-b1..D-b8). Las llamadas a hotel-service van siempre fuera de
 * transacción; las escrituras, en transacciones cortas con el grupo (y después la reserva)
 * bloqueados. Nunca se escribe el token del enlace en el log.
 */
@Service
@Slf4j
public class GrupoPagoService {

    public static final Duration PLAZO = Duration.ofHours(24);

    private final BookingRepository bookingRepository;
    private final GrupoPagoRepository grupoRepository;
    private final CarritoSoporte soporte;
    private final InventarioCarrito inventario;
    private final GrupoMapper mapper;
    private final TokenEnlace tokens;
    private final TransactionTemplate transaccion;

    public GrupoPagoService(BookingRepository bookingRepository, GrupoPagoRepository grupoRepository,
                            CarritoSoporte soporte, InventarioCarrito inventario, GrupoMapper mapper,
                            TokenEnlace tokens, TransactionTemplate transaccion) {
        this.bookingRepository = bookingRepository;
        this.grupoRepository = grupoRepository;
        this.soporte = soporte;
        this.inventario = inventario;
        this.mapper = mapper;
        this.tokens = tokens;
        this.transaccion = transaccion;
    }

    // ---------- empezar (D-b4, D-b5) ----------

    /** Lo leído antes de llamar al hotel; la transacción final verifica que nada haya cambiado. */
    private record Preparacion(Reservation reserva, BigDecimal total, int cantidadItems,
                               LocalDateTime limiteAnterior, LocalDateTime venceEn, int cantidadPartes) {
    }

    /**
     * Sin @Transactional a propósito: lectura y controles (transacción corta) → retenciones al plazo
     * nuevo por HTTP → transacción corta que relee con lock y crea el grupo. Si la última falla, las
     * retenciones vuelven a su vencimiento, salvo que otro pedido ya haya creado el grupo (en ese caso
     * las retenciones son de ese grupo y no se tocan).
     */
    public GrupoResponse iniciar(Long reservaId, int cantidadPartes, Long usuarioId) {
        Preparacion prep = transaccion.execute(estado -> preparar(reservaId, cantidadPartes, usuarioId));
        Instant anterior = instante(prep.limiteAnterior());
        inventario.cambiarVencimientoRetenciones(prep.reserva(), instante(prep.venceEn()), anterior);
        try {
            return transaccion.execute(estado -> crear(reservaId, usuarioId, prep));
        } catch (RuntimeException ex) {
            if (grupoRepository.findByReservation_Id(reservaId).isEmpty()) {
                inventario.volverAlVencimiento(prep.reserva(), anterior);
            }
            throw ex;
        }
    }

    private Preparacion preparar(Long reservaId, int cantidadPartes, Long usuarioId) {
        Reservation reserva = soporte.reservaDelUsuario(reservaId, usuarioId);
        exigirListoParaDividir(reserva);
        if (grupoRepository.findByReservation_Id(reservaId).isPresent()) {
            throw grupoYaExiste();
        }
        BigDecimal total = CarritoCalculo.montoTotal(reserva); // también deja cargadas estadías y pasajeros
        RepartoGrupo.validarCantidad(cantidadPartes, total);
        return new Preparacion(reserva, total, CarritoCalculo.cantidadItems(reserva), reserva.getLimiteTiempo(),
                soporte.ahora().plus(PLAZO), cantidadPartes);
    }

    private GrupoResponse crear(Long reservaId, Long usuarioId, Preparacion prep) {
        Reservation reserva = bookingRepository.findByIdForUpdate(reservaId)
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));
        if (grupoRepository.findByReservation_Id(reservaId).isPresent()) {
            throw grupoYaExiste();
        }
        exigirListoParaDividir(reserva);
        if (CarritoCalculo.montoTotal(reserva).compareTo(prep.total()) != 0
                || CarritoCalculo.cantidadItems(reserva) != prep.cantidadItems()) {
            throw new BookingException("CARRITO_CAMBIO",
                    "El carrito cambió mientras preparábamos el pago en grupo. Revisalo y probá de nuevo.", HttpStatus.CONFLICT);
        }
        reserva.setTipoPago(PaymentType.SPLIT_PAYMENT);
        reserva.setEstado(ReservationStatus.ESPERANDO_PAGADORES);
        reserva.setLimiteTiempo(prep.venceEn());
        inventario.alinearBloqueos(reserva);
        bookingRepository.save(reserva);

        LocalDateTime ahora = soporte.ahora();
        GrupoPago grupo = new GrupoPago();
        grupo.setReservation(reserva);
        grupo.setOrganizadorId(usuarioId);
        grupo.setTokenEnlace(tokens.nuevo());
        grupo.setEstado(EstadoGrupo.ABIERTO);
        grupo.setVenceEn(prep.venceEn());
        grupo.setCreadoEn(ahora);
        grupo.setActualizadoEn(ahora);
        List<BigDecimal> montos = RepartoGrupo.iguales(prep.total(), prep.cantidadPartes());
        for (int i = 0; i < montos.size(); i++) {
            ParteGrupo parte = ParteGrupo.libre(i + 1, montos.get(i));
            if (i == 0) {
                parte.tomar(usuarioId, null);
            }
            grupo.agregarParte(parte);
        }
        try {
            grupo = grupoRepository.saveAndFlush(grupo);
        } catch (DataIntegrityViolationException ex) {
            throw grupoYaExiste();
        }
        log.info("La reserva {} pasa a pago en grupo {} con {} partes; vence {}", reservaId, grupo.getId(),
                montos.size(), prep.venceEn());
        return mapper.toResponse(grupo, usuarioId);
    }

    private void exigirListoParaDividir(Reservation reserva) {
        if (reserva.getEstado() != ReservationStatus.PENDIENTE_PAGO) {
            throw new BookingException("ESTADO_INVALIDO",
                    "Completá los datos del carrito antes de dividir el pago.", HttpStatus.CONFLICT);
        }
        if (soporte.vencido(reserva)) {
            throw new BookingException("CARRITO_EXPIRADO", "El tiempo límite de 15 minutos terminó.", HttpStatus.GONE);
        }
    }

    private static BookingException grupoYaExiste() {
        return new BookingException("GRUPO_YA_EXISTE", "Este carrito ya se está pagando en grupo.", HttpStatus.CONFLICT);
    }

    // ---------- lecturas (D-b6, D-b18, D-b19) ----------

    @Transactional(readOnly = true)
    public GrupoResponse verComoOrganizador(Long reservaId, Long usuarioId) {
        soporte.reservaDelUsuario(reservaId, usuarioId);
        return mapper.toResponse(grupoDeReserva(reservaId), usuarioId);
    }

    /** Para quien tiene parte (organizador incluido). Sin parte: 404, igual que sin grupo. */
    @Transactional(readOnly = true)
    public GrupoResponse participacion(Long reservaId, Long usuarioId) {
        GrupoPago grupo = grupoRepository.findByReservation_Id(reservaId)
                .filter(g -> g.tieneParte(usuarioId))
                .orElseThrow(GrupoPagoService::grupoNoEncontrado);
        return mapper.toResponse(grupo, usuarioId);
    }

    /** El enlace: a quien no tiene parte solo se le muestra mientras el grupo esté abierto y en plazo. */
    @Transactional(readOnly = true)
    public GrupoResponse consultar(String token, Long usuarioId) {
        GrupoPago grupo = grupoPorToken(token);
        if (!grupo.tieneParte(usuarioId) && (!grupo.abierto() || vencido(grupo))) {
            throw enlaceVencido();
        }
        return mapper.toResponse(grupo, usuarioId);
    }

    @Transactional(readOnly = true)
    public List<GrupoResumenResponse> misGrupos(Long usuarioId) {
        return grupoRepository.gruposConParteDe(usuarioId, List.of(EstadoGrupo.ABIERTO, EstadoGrupo.COMPLETO))
                .stream().map(g -> mapper.resumen(g, usuarioId)).toList();
    }

    // ---------- soporte ----------

    public boolean vencido(GrupoPago grupo) {
        return !soporte.ahora().isBefore(grupo.getVenceEn());
    }

    GrupoPago grupoDeReserva(Long reservaId) {
        return grupoRepository.findByReservation_Id(reservaId).orElseThrow(GrupoPagoService::grupoNoEncontrado);
    }

    GrupoPago grupoPorToken(String token) {
        if (!TokenEnlace.formatoValido(token)) {
            throw grupoNoEncontrado();
        }
        return grupoRepository.findByTokenEnlace(token).orElseThrow(GrupoPagoService::grupoNoEncontrado);
    }

    static BookingException grupoNoEncontrado() {
        return new BookingException("GRUPO_NO_ENCONTRADO", "No encontramos ese pago en grupo.", HttpStatus.NOT_FOUND);
    }

    static BookingException enlaceVencido() {
        return new BookingException("ENLACE_VENCIDO",
                "Este enlace ya no está activo: el pago en grupo terminó o venció.", HttpStatus.GONE);
    }

    private Instant instante(LocalDateTime horaArgentina) {
        return horaArgentina.atZone(ClockConfig.ZONA).toInstant();
    }
}
