package com.despescar.reservationservice.service;

import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.BookingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lo que comparten el vuelo, las estadías y los titulares: encontrar el carrito activo, crearlo,
 * verificar dueño y que todavía se pueda modificar. "Ahora" sale del Clock (hora de Argentina, D23).
 */
@Component
public class CarritoSoporte {

    public static final Duration DURACION = Duration.ofMinutes(15);
    public static final List<ReservationStatus> ABIERTOS = List.of(ReservationStatus.INICIADA, ReservationStatus.PENDIENTE_PAGO);

    private final BookingRepository bookingRepository;
    private final Clock clock;
    private final PlatformTransactionManager gestor;
    private final InventarioCarrito inventario;

    public CarritoSoporte(BookingRepository bookingRepository, Clock clock, PlatformTransactionManager gestor,
                          InventarioCarrito inventario) {
        this.bookingRepository = bookingRepository;
        this.clock = clock;
        this.gestor = gestor;
        this.inventario = inventario;
    }

    public LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }

    public boolean vencido(Reservation reserva) {
        return reserva.getLimiteTiempo() == null || !ahora().isBefore(reserva.getLimiteTiempo());
    }

    /** El carrito abierto y sin vencer del usuario. Uno vencido lo cierra el scheduler (D13). */
    public Optional<Reservation> carritoActivo(Long usuarioId) {
        return bookingRepository.findFirstByCreadorIdAndEstadoInOrderByIdDesc(usuarioId, ABIERTOS)
                .filter(r -> !vencido(r));
    }

    /** El carrito abierto del usuario aunque ya haya vencido (lo cierra el scheduler). */
    public Optional<Reservation> carritoAbierto(Long usuarioId) {
        return bookingRepository.findFirstByCreadorIdAndEstadoInOrderByIdDesc(usuarioId, ABIERTOS);
    }

    /**
     * El carrito abierto del usuario, leído con la fila bloqueada hasta el fin de la transacción.
     * Primero se busca solo el id: la entidad se carga recién bloqueada, así no queda una copia
     * anterior al bloqueo en el contexto de persistencia. Todo cambio del carrito empieza por acá
     * (reserva y después asientos, el mismo orden que la confirmación del pago).
     */
    public Optional<Reservation> carritoAbiertoBloqueado(Long usuarioId) {
        return bookingRepository.findIdsCarritoAbierto(usuarioId, ABIERTOS).stream().findFirst()
                .flatMap(bookingRepository::findByIdForUpdate)
                .filter(r -> Objects.equals(usuarioId, r.getCreadorId()));
    }

    /**
     * La reserva del usuario que se está pagando en grupo (D-b17), venza cuando venza: la cierra
     * GrupoPagoScheduler. El filtro por estado es a propósito redundante con la consulta.
     */
    public Optional<Reservation> grupoEnCurso(Long usuarioId) {
        return bookingRepository
                .findFirstByCreadorIdAndEstadoInOrderByIdDesc(usuarioId, List.of(ReservationStatus.ESPERANDO_PAGADORES))
                .filter(r -> r.getEstado() == ReservationStatus.ESPERANDO_PAGADORES);
    }

    /** Con un pago en grupo en curso no se arma otro carrito ni se le agregan ítems. */
    public void exigirSinGrupoEnCurso(Long usuarioId) {
        if (grupoEnCurso(usuarioId).isPresent()) {
            throw new BookingException("PAGO_EN_GRUPO_EN_CURSO",
                    "Tenés un pago en grupo en curso. Esperá a que termine o cancelalo desde tu carrito.",
                    HttpStatus.CONFLICT);
        }
    }

    /**
     * Crea y guarda un carrito vacío: dura 15 minutos desde ahora. Se hace en su propia transacción:
     * un usuario tiene un solo carrito abierto (índice único), así que si otro pedido lo creó primero
     * (doble clic) se reutiliza ese. Un carrito abierto pero vencido, que el scheduler todavía no
     * cerró, se cierra acá para no impedir el nuevo: se lo lee con la fila bloqueada, se sueltan sus
     * asientos (los ocupados no se tocan, y se bloquean en orden después de la reserva, como el
     * scheduler) para que el carrito nuevo pueda volver a elegirlos, y sus retenciones vencen solas. Sus
     * pasajeros quedan PENDIENTE, como en el scheduler: un pago tardío todavía coincide con el total (D6).
     */
    public Reservation crearCarrito(Long usuarioId) {
        exigirSinGrupoEnCurso(usuarioId);
        TransactionTemplate nueva = new TransactionTemplate(gestor);
        nueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return nueva.execute(estado -> {
                carritoAbiertoBloqueado(usuarioId).filter(this::vencido).ifPresent(vencido -> {
                    vencido.setEstado(ReservationStatus.EXPIRADA);
                    inventario.liberarAsientos(vencido);
                    bookingRepository.saveAndFlush(vencido);
                });
                return bookingRepository.saveAndFlush(Reservation.builder()
                        .creadorId(usuarioId)
                        .cantidadPasajeros(0)
                        .tipoPago(PaymentType.SINGLE_PAYMENT)
                        .estado(ReservationStatus.INICIADA)
                        .limiteTiempo(ahora().plus(DURACION))
                        .build());
            });
        } catch (DataIntegrityViolationException ex) {
            return carritoActivo(usuarioId).orElseThrow(() -> new BookingException("CARRITO_EN_USO",
                    "Hay otro pedido sobre tu carrito. Reintentá en unos segundos.", HttpStatus.CONFLICT));
        }
    }

    public Reservation reservaDelUsuario(Long id, Long usuarioId) {
        return delUsuario(bookingRepository.findById(id), usuarioId);
    }

    /** Igual que {@link #reservaDelUsuario}, con la fila bloqueada hasta el fin de la transacción. */
    public Reservation reservaDelUsuarioBloqueada(Long id, Long usuarioId) {
        return delUsuario(bookingRepository.findByIdForUpdate(id), usuarioId);
    }

    private static Reservation delUsuario(Optional<Reservation> encontrada, Long usuarioId) {
        Reservation reserva = encontrada
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));
        if (!reserva.getCreadorId().equals(usuarioId)) {
            throw new BookingException("ACCESO_DENEGADO", "No tenés acceso a esta reserva.", HttpStatus.FORBIDDEN);
        }
        return reserva;
    }

    /** Solo se modifica un carrito abierto y dentro de su tiempo. */
    public void verificarModificable(Reservation reserva) {
        if (!ABIERTOS.contains(reserva.getEstado())) {
            throw new BookingException("ESTADO_INVALIDO",
                    "El carrito ya no se puede modificar (estado actual: " + reserva.getEstado() + ").", HttpStatus.CONFLICT);
        }
        if (vencido(reserva)) {
            throw new BookingException("CARRITO_EXPIRADO", "El tiempo límite de 15 minutos terminó.", HttpStatus.GONE);
        }
    }
}
