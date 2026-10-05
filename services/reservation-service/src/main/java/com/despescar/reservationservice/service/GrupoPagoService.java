package com.despescar.reservationservice.service;

import com.despescar.reservationservice.config.ClockConfig;
import com.despescar.reservationservice.dto.grupo.EditarPartesRequest;
import com.despescar.reservationservice.dto.grupo.GrupoResponse;
import com.despescar.reservationservice.dto.grupo.GrupoResumenResponse;
import com.despescar.reservationservice.entity.GrupoPago;
import com.despescar.reservationservice.entity.ParteGrupo;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.EstadoParte;
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
import java.util.Comparator;
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
    private final GrupoCierre cierre;

    public GrupoPagoService(BookingRepository bookingRepository, GrupoPagoRepository grupoRepository,
                            CarritoSoporte soporte, InventarioCarrito inventario, GrupoMapper mapper,
                            TokenEnlace tokens, TransactionTemplate transaccion, GrupoCierre cierre) {
        this.bookingRepository = bookingRepository;
        this.grupoRepository = grupoRepository;
        this.soporte = soporte;
        this.inventario = inventario;
        this.mapper = mapper;
        this.tokens = tokens;
        this.transaccion = transaccion;
        this.cierre = cierre;
    }

    // ---------- empezar (D-b4, D-b5) ----------

    /** Lo leído antes de llamar al hotel; la transacción final verifica que nada haya cambiado. */
    private record Preparacion(Reservation reserva, BigDecimal total, int cantidadItems,
                               LocalDateTime limiteAnterior, LocalDateTime venceEn, int cantidadPartes) {
    }

    /**
     * Sin @Transactional a propósito: lectura y controles (transacción corta) → retenciones al plazo
     * nuevo por HTTP → transacción corta que relee con lock y crea el grupo. Si la última falla, las
     * retenciones vuelven a su vencimiento solo si siguen siendo de un carrito abierto: no se tocan si
     * otro pedido ya creó el grupo (son de ese grupo) ni si la reserva ya no está abierta (un pago del
     * carrito entero pudo confirmarla recién). Si el vencimiento anterior ya pasó tampoco se liberan
     * acá: lo hace BookingScheduler al marcar la reserva EXPIRADA con la fila bloqueada.
     */
    public GrupoResponse iniciar(Long reservaId, int cantidadPartes, Long usuarioId) {
        Preparacion prep = transaccion.execute(estado -> preparar(reservaId, cantidadPartes, usuarioId));
        Instant anterior = instante(prep.limiteAnterior());
        inventario.cambiarVencimientoRetenciones(prep.reserva(), instante(prep.venceEn()), anterior);
        try {
            return transaccion.execute(estado -> crear(reservaId, usuarioId, prep));
        } catch (RuntimeException ex) {
            if (hayQueCompensar(reservaId, prep)) {
                inventario.volverAlVencimiento(prep.reserva(), anterior);
            }
            throw ex;
        }
    }

    private boolean hayQueCompensar(Long reservaId, Preparacion prep) {
        if (grupoRepository.findByReservation_Id(reservaId).isPresent()) {
            return false;
        }
        ReservationStatus actual = transaccion.execute(estado -> bookingRepository.findById(reservaId)
                .map(Reservation::getEstado).orElse(null));
        if (actual == null || !CarritoSoporte.ABIERTOS.contains(actual)) {
            log.warn("La reserva {} quedó {} mientras se preparaba el pago en grupo: sus retenciones no se tocan.",
                    reservaId, actual);
            return false;
        }
        if (!soporte.ahora().isBefore(prep.limiteAnterior())) {
            log.warn("La reserva {} venció mientras se preparaba el pago en grupo: sus retenciones las libera el vencimiento.",
                    reservaId);
            return false;
        }
        return true;
    }

    private Preparacion preparar(Long reservaId, int cantidadPartes, Long usuarioId) {
        Reservation reserva = soporte.reservaDelUsuario(reservaId, usuarioId);
        if (grupoRepository.findByReservation_Id(reservaId).isPresent()) {
            throw grupoYaExiste();
        }
        exigirListoParaDividir(reserva);
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

    // ---------- sumarse (D-b7) ----------

    /** Toma la parte LIBRE de número más bajo con el grupo bloqueado. Idempotente para quien ya tiene parte. */
    @Transactional
    public GrupoResponse unirse(String token, String apodo, Long usuarioId) {
        if (!TokenEnlace.formatoValido(token)) {
            throw grupoNoEncontrado();
        }
        GrupoPago grupo = grupoRepository.findByTokenForUpdate(token).orElseThrow(GrupoPagoService::grupoNoEncontrado);
        if (grupo.tieneParte(usuarioId)) {
            return mapper.toResponse(grupo, usuarioId);
        }
        if (!grupo.abierto() || vencido(grupo)) {
            throw enlaceVencido();
        }
        ParteGrupo libre = grupo.getPartes().stream()
                .filter(p -> p.getEstado() == EstadoParte.LIBRE)
                .min(Comparator.comparingInt(ParteGrupo::getNumero))
                .orElseThrow(() -> new BookingException("GRUPO_COMPLETO",
                        "Ya se tomaron todas las partes de este pago. Pedile al organizador que libere una.",
                        HttpStatus.CONFLICT));
        libre.tomar(usuarioId, normalizarApodo(apodo));
        grupo.setActualizadoEn(soporte.ahora());
        GrupoPago guardado = grupoRepository.saveAndFlush(grupo);
        log.info("El usuario {} tomó la parte {} del pago en grupo {}", usuarioId, libre.getNumero(), grupo.getId());
        return mapper.toResponse(guardado, usuarioId);
    }

    /** Sin espacios de más; vacío → sin apodo. El formato ya lo validó el DTO. */
    static String normalizarApodo(String apodo) {
        if (apodo == null) {
            return null;
        }
        String limpio = apodo.trim().replaceAll("\\s+", " ");
        return limpio.isEmpty() ? null : limpio;
    }

    // ---------- organizador (D-b3, D-b8, D-b16) ----------

    @Transactional
    public GrupoResponse editarPartes(Long reservaId, EditarPartesRequest pedido, Long usuarioId) {
        boolean porCantidad = pedido.cantidadPartes() != null;
        if (porCantidad == (pedido.montos() != null)) {
            throw new BookingException("VALIDACION",
                    "Indicá la cantidad de partes o los montos, no los dos.", HttpStatus.BAD_REQUEST);
        }
        soporte.reservaDelUsuario(reservaId, usuarioId);
        GrupoPago grupo = grupoRepository.findByReservaIdForUpdate(reservaId).orElseThrow(GrupoPagoService::grupoNoEncontrado);
        exigirAbierto(grupo);
        if (vencido(grupo)) {
            throw new BookingException("GRUPO_VENCIDO", "El plazo para pagar ya terminó.", HttpStatus.GONE);
        }
        if (grupo.hayPagadas()) {
            throw new BookingException("GRUPO_CON_PAGOS",
                    "Alguien ya pagó su parte: los montos no se pueden cambiar.", HttpStatus.CONFLICT);
        }
        BigDecimal total = CarritoCalculo.montoTotal(grupo.getReservation());
        List<BigDecimal> montos = porCantidad
                ? RepartoGrupo.iguales(total, pedido.cantidadPartes())
                : RepartoGrupo.validarMontos(pedido.montos(), total);
        if (montos.size() != grupo.getPartes().size() && grupo.hayInvitados()) {
            throw new BookingException("GRUPO_CON_INVITADOS",
                    "Ya se sumaron amigos: podés cambiar los montos, pero no la cantidad de partes.", HttpStatus.CONFLICT);
        }
        redimensionar(grupo, montos);
        grupo.setActualizadoEn(soporte.ahora());
        return mapper.toResponse(grupoRepository.saveAndFlush(grupo), usuarioId);
    }

    /**
     * Ajusta las partes a `montos`: quita las de número mayor o agrega LIBRE al final. Nunca hace las dos
     * cosas a la vez, así el insert y el delete no chocan en el índice (grupo, numero).
     */
    private static void redimensionar(GrupoPago grupo, List<BigDecimal> montos) {
        grupo.getPartes().removeIf(p -> p.getNumero() > montos.size());
        for (int i = 0; i < montos.size(); i++) {
            int numero = i + 1;
            BigDecimal monto = montos.get(i);
            grupo.parte(numero).ifPresentOrElse(p -> p.setMonto(monto),
                    () -> grupo.agregarParte(ParteGrupo.libre(numero, monto)));
        }
    }

    @Transactional
    public GrupoResponse liberarParte(Long reservaId, int numero, Long usuarioId) {
        soporte.reservaDelUsuario(reservaId, usuarioId);
        GrupoPago grupo = grupoRepository.findByReservaIdForUpdate(reservaId).orElseThrow(GrupoPagoService::grupoNoEncontrado);
        if (numero == 1) {
            throw new BookingException("ORGANIZADOR_NO_SE_QUITA",
                    "La parte 1 es la del organizador y no se puede liberar.", HttpStatus.BAD_REQUEST);
        }
        exigirAbierto(grupo);
        ParteGrupo parte = grupo.parte(numero).orElseThrow(() -> new BookingException("PARTE_NO_ENCONTRADA",
                "Esa parte no existe.", HttpStatus.NOT_FOUND));
        if (parte.getEstado() == EstadoParte.PAGADA) {
            throw new BookingException("PARTE_PAGADA", "Esa parte ya está pagada.", HttpStatus.CONFLICT);
        }
        if (parte.getEstado() == EstadoParte.TOMADA) {
            parte.liberar();
            grupo.setActualizadoEn(soporte.ahora());
            grupo = grupoRepository.saveAndFlush(grupo);
            log.info("El organizador liberó la parte {} del pago en grupo {}", numero, grupo.getId());
        }
        return mapper.toResponse(grupo, usuarioId);
    }

    /**
     * Cancela un grupo ABIERTO (D-b16). Sin @Transactional: GrupoCierre libera las retenciones por HTTP
     * después de su commit. Un grupo ya cancelado o vencido se devuelve como está.
     */
    public GrupoResponse cancelar(Long reservaId, Long usuarioId) {
        GrupoPago leido = transaccion.execute(estado -> {
            soporte.reservaDelUsuario(reservaId, usuarioId);
            return grupoDeReserva(reservaId);
        });
        if (leido.getEstado() == EstadoGrupo.COMPLETO) {
            throw new BookingException("GRUPO_CONFIRMANDO",
                    "Ya pagaron todos y estamos confirmando la reserva.", HttpStatus.CONFLICT);
        }
        if (leido.getEstado() == EstadoGrupo.CONFIRMADO) {
            throw new BookingException("USAR_CANCELACION_POR_ITEM",
                    "La reserva ya está confirmada: se cancela por ítem desde Mis reservas.", HttpStatus.CONFLICT);
        }
        if (leido.abierto()) {
            cierre.cerrar(leido.getId(), EstadoGrupo.CANCELADO, GrupoCierre.MOTIVO_CANCELADO, false);
        }
        return transaccion.execute(estado -> mapper.toResponse(
                grupoRepository.findById(leido.getId()).orElseThrow(GrupoPagoService::grupoNoEncontrado), usuarioId));
    }

    private static void exigirAbierto(GrupoPago grupo) {
        if (!grupo.abierto()) {
            throw new BookingException("GRUPO_CERRADO", "Este pago en grupo ya terminó.", HttpStatus.CONFLICT);
        }
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
