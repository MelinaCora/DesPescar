package com.despescar.reservationservice.service;

import com.despescar.reservationservice.client.FlightClient;
import com.despescar.reservationservice.client.PackageClient;
import com.despescar.reservationservice.dto.packagecatalog.response.PackageLookupResponse;
import com.despescar.reservationservice.dto.reservation.request.BookingInitRequest;
import com.despescar.reservationservice.dto.reservation.request.PaymentConfirmationRequest;
import com.despescar.reservationservice.dto.reservation.response.BookingInitResponse;
import com.despescar.reservationservice.dto.reservation.response.ConfirmacionPagoResponse;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.mapper.ReservationMapper;
import com.despescar.reservationservice.repository.BookingDetailRepository;
import com.despescar.reservationservice.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository detailRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ReservationMapper reservationMapper;

    // Clientes Feign
    private final FlightClient flightClient;
    private final PackageClient packageClient;
    private final CarritoSoporte soporte;
    private final PrecioVuelo precioVuelo;
    private final InventarioCarrito inventario;
    private final TransactionTemplate transaccion;

    /** Intentos cuando la reserva cambia entre la lectura y el bloqueo (vencimiento, abandono, edición). */
    static final int INTENTOS_CONFIRMACION = 3;

    /**
     * Suma la parte de vuelo al carrito activo del usuario o crea uno (D11). El precio por
     * pasajero se calcula acá con flight-service y queda congelado (D1). hotelId se ignora.
     * La cotización (HTTP) va sin transacción; después el carrito se relee bloqueado (reserva y
     * después asientos, el mismo orden que la confirmación del pago) y se revalida antes de guardar.
     */
    public BookingInitResponse initializeBooking(
            BookingInitRequest request,
            String authorizationHeader,
            Long authenticatedUserId) {

        if (request.getPaymentType() == PaymentType.SPLIT_PAYMENT) {
            throw new BookingException("PAGO_DIVIDIDO_NO_DISPONIBLE",
                    "El pago dividido todavía no está disponible.", HttpStatus.BAD_REQUEST);
        }
        soporte.exigirSinGrupoEnCurso(authenticatedUserId);
        Optional<Long> activo = transaccion.execute(status -> soporte.carritoActivo(authenticatedUserId).map(r -> {
            verificarSinVuelo(r);
            return r.getId();
        }));
        if (request.getPackageId() != null) {
            validarYObtenerPaquete(request.getPackageId(), authorizationHeader);
        }

        PrecioVuelo.Cotizacion cotizacion = precioVuelo.cotizar(
                request.getFlightIds(), request.getBaggageIds(), request.getCantidadPasajeros());

        Long carritoId = activo != null && activo.isPresent()
                ? activo.get()
                : soporte.crearCarrito(authenticatedUserId).getId();
        return transaccion.execute(status -> {
            Reservation carrito = bookingRepository.findByIdForUpdate(carritoId).orElseThrow(() -> new BookingException(
                    "CARRITO_NO_ENCONTRADO", "El carrito ya no existe.", HttpStatus.NOT_FOUND));
            if (!Objects.equals(carrito.getCreadorId(), authenticatedUserId)) {
                throw new BookingException("ACCESO_DENEGADO", "No tenés acceso a esta reserva.", HttpStatus.FORBIDDEN);
            }
            soporte.verificarModificable(carrito);
            verificarSinVuelo(carrito); // otro pedido pudo sumarle un vuelo mientras se cotizaba
            carrito.getFlightIds().clear();
            carrito.getFlightIds().addAll(request.getFlightIds());
            carrito.setBaggageIds(new ArrayList<>(request.getBaggageIds()));
            carrito.setCantidadPasajeros(request.getCantidadPasajeros());
            carrito.setPackageId(request.getPackageId());
            carrito.setPrecioVueloPorPasajero(cotizacion.precioPorPasajero());
            carrito.setTarifasVuelo(cotizacion.tarifas());
            carrito.setSalidaVuelo(cotizacion.salida());
            carrito.setEstado(CarritoCalculo.estadoAbierto(carrito));

            Reservation guardado = bookingRepository.save(carrito);
            // Los asientos que el usuario eligió en el mapa vencen con el carrito (D9)
            inventario.alinearBloqueos(guardado);

            return BookingInitResponse.builder()
                    .bookingId(guardado.getId())
                    .status(guardado.getEstado().name())
                    .paymentType(guardado.getTipoPago())
                    .build();
        });
    }

    private static void verificarSinVuelo(Reservation carrito) {
        if (CarritoCalculo.tieneVuelo(carrito)) {
            throw new BookingException("CARRITO_YA_TIENE_VUELO",
                    "Tu carrito ya tiene un vuelo. Quitalo para agregar otro.", HttpStatus.CONFLICT);
        }
    }

    /**
     * Se conserva para clientes viejos (el front no lo usa): solo el creador, solo un carrito listo
     * para pagar y en hora. Ya no exige pasajeros (un carrito de solo hotel también se paga) ni
     * cancela al leer un carrito vencido: de eso se ocupa el scheduler (D13).
     */
    @Transactional(readOnly = true)
    public String procesarPago(Long id, Long payerUserId) {
        Reservation reserva = soporte.reservaDelUsuario(id, payerUserId);
        if (reserva.getEstado() != ReservationStatus.PENDIENTE_PAGO) {
            throw new BookingException("MODIFICACION_PROHIBIDA",
                    "La reserva no está lista para pago (estado actual: " + reserva.getEstado() + ").", HttpStatus.BAD_REQUEST);
        }
        if (soporte.vencido(reserva)) {
            throw new BookingException("CARRITO_EXPIRADO", "El tiempo límite de 15 minutos terminó.", HttpStatus.GONE);
        }
        return "Pago pendiente de confirmacion del proveedor. La reserva no se confirmara hasta recibir un callback validado.";
    }

    /**
     * Confirmación del pago (contrato C3). Sin @Transactional a propósito: las estadías se confirman
     * primero por HTTP, sin transacción; después, en una transacción corta y con la fila de la reserva
     * bloqueada, se revalida que nada haya cambiado y se ocupan los asientos (los FOR UPDATE nunca
     * cruzan una llamada HTTP). Si no hay lugar, la reserva se cancela en esa misma transacción y las
     * retenciones se liberan después del commit. Si la reserva cambió durante la llamada al hotel
     * (vencimiento, abandono, edición) se sueltan las retenciones que tomó este intento y se vuelve a
     * evaluar. Los errores de comunicación con hotel-service se propagan (5xx) y payment-service
     * reintenta con el mismo tokenPago.
     */
    public ConfirmacionPagoResponse confirmarPago(Long id, PaymentConfirmationRequest pedido) {
        return confirmar(id, new Criterio(
                pedido.getTokenPago(),
                reserva -> controlDeUnPagador(reserva, pedido),
                reserva -> yaConfirmada(reserva, pedido.getTokenPago()),
                reserva -> coincideElMonto(reserva, pedido.getMonto())));
    }

    /**
     * Confirma una reserva que ya está pagada completa por su grupo (D-b11): el mismo camino que
     * confirmarPago (estadías por HTTP sin transacción → asientos en transacción corta → cancelar y
     * liberar si no hay lugar), sin controles de pagador ni de monto, que PagoParteService ya hizo
     * parte por parte y con la suma bajo el lock del grupo. Sin @Transactional. Idempotente: una
     * reserva dividida ya CONFIRMADA responde CONFIRMADA (solo su grupo puede confirmarla, D-b12).
     */
    public ConfirmacionPagoResponse confirmarReservaPagada(Long id, String tokenConfirmacion) {
        return confirmar(id, new Criterio(
                tokenConfirmacion,
                BookingService::controlDeGrupo,
                reserva -> ConfirmacionPagoResponse.confirmada(),
                reserva -> true));
    }

    /**
     * Lo que distingue al pago de un solo pagador del de un grupo. `previo` corre antes de mirar el
     * estado: lanza si el pedido no corresponde o devuelve la respuesta final (null para seguir).
     */
    private record Criterio(String token,
                            Function<Reservation, ConfirmacionPagoResponse> previo,
                            Function<Reservation, ConfirmacionPagoResponse> yaConfirmada,
                            Predicate<Reservation> montoCorrecto) {
    }

    private static ConfirmacionPagoResponse controlDeUnPagador(Reservation reserva, PaymentConfirmationRequest pedido) {
        if (!Objects.equals(reserva.getCreadorId(), pedido.getPagadorId())) {
            throw new BookingException("PAGADOR_INVALIDO", "El pagador no es el creador de la reserva.", HttpStatus.BAD_REQUEST);
        }
        if (reserva.getTipoPago() == PaymentType.SPLIT_PAYMENT) {
            // D-b12: en cualquier estado, también CONFIRMADA; ese pago se reembolsa
            return ConfirmacionPagoResponse.rechazada(ConfirmacionPagoResponse.PAGO_EN_GRUPO,
                    "La reserva se está pagando en grupo.");
        }
        return null;
    }

    private static ConfirmacionPagoResponse controlDeGrupo(Reservation reserva) {
        if (reserva.getTipoPago() != PaymentType.SPLIT_PAYMENT) {
            throw new BookingException("ESTADO_INVALIDO", "La reserva no se paga en grupo.", HttpStatus.CONFLICT);
        }
        return null;
    }

    private ConfirmacionPagoResponse confirmar(Long id, Criterio criterio) {
        for (int intento = 1; intento <= INTENTOS_CONFIRMACION; intento++) {
            Evaluacion evaluacion = transaccion.execute(status -> evaluar(id, criterio));
            if (evaluacion.respuesta() != null) {
                return evaluacion.respuesta();
            }
            Reservation leida = evaluacion.reserva();
            Set<UUID> previas = retenciones(leida);

            // 1. Estadías: llamadas remotas, sin transacción abierta ni asientos bloqueados. Las
            // retenciones de una EXPIRADA son del scheduler (las libera después de su commit): se toman nuevas.
            boolean conLugar = evaluacion.renovar()
                    ? inventario.retenerYConfirmarEstadias(leida)
                    : inventario.confirmarEstadias(leida);

            // 2. Base: transacción corta con la reserva bloqueada
            Cierre cierre;
            try {
                cierre = transaccion.execute(status -> cerrarEnBase(id, leida, evaluacion, criterio, conLugar));
            } catch (RuntimeException ex) {
                liberarNuevas(leida, previas);
                throw ex;
            }

            // 3. Lo remoto y los avisos, ya fuera de la transacción
            avisar(id, cierre.aviso());
            switch (cierre.tipo()) {
                case CONFIRMADA -> {
                    descontarCupos(leida);
                    log.info("Reserva {} confirmada con el pago {}", id, criterio.token());
                    return cierre.respuesta();
                }
                case SIN_LUGAR -> {
                    inventario.liberarRetenciones(leida);
                    return cierre.respuesta();
                }
                case RESUELTA -> {
                    liberarNuevas(leida, previas);
                    return cierre.respuesta();
                }
                default -> {
                    liberarNuevas(leida, previas);
                    log.warn("La reserva {} cambió mientras se confirmaba el pago {} (intento {})", id, criterio.token(), intento);
                }
            }
        }
        throw new BookingException("CONFIRMACION_CONCURRENTE",
                "La reserva cambió mientras se confirmaba el pago. Reintentá.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** Lectura y controles. Deja inicializados vuelos, pasajeros y estadías para usarlos después del commit. */
    private Evaluacion evaluar(Long id, Criterio criterio) {
        Reservation reserva = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));
        ConfirmacionPagoResponse previa = criterio.previo().apply(reserva);
        if (previa != null) {
            return Evaluacion.fin(previa);
        }
        if (reserva.getEstado() == ReservationStatus.CONFIRMADA) {
            return Evaluacion.fin(criterio.yaConfirmada().apply(reserva));
        }
        if (reserva.getEstado() == ReservationStatus.CANCELADA) {
            return Evaluacion.fin(ConfirmacionPagoResponse.cancelada(motivoDeCancelada(reserva), "La reserva ya estaba cancelada."));
        }
        if (!CarritoCalculo.datosCompletos(reserva)) {
            return Evaluacion.fin(ConfirmacionPagoResponse.rechazada(ConfirmacionPagoResponse.DATOS_INCOMPLETOS,
                    "Faltan datos de pasajeros o titulares."));
        }
        if (!criterio.montoCorrecto().test(reserva)) {
            return Evaluacion.fin(ConfirmacionPagoResponse.rechazada(ConfirmacionPagoResponse.MONTO_NO_COINCIDE,
                    "El monto pagado no coincide con el total del carrito."));
        }
        boolean expirada = reserva.getEstado() == ReservationStatus.EXPIRADA;
        boolean tardio = expirada || soporte.vencido(reserva);
        return new Evaluacion(null, reserva, tardio
                ? ConfirmacionPagoResponse.PAGO_TARDIO_SIN_DISPONIBILIDAD
                : ConfirmacionPagoResponse.SIN_DISPONIBILIDAD, expirada);
    }

    /**
     * Con la reserva bloqueada: si otro pago la confirmó, responde según el token; si cambió desde la
     * lectura, pide reintentar; si hay lugar ocupa los asientos y la confirma; si no, la cancela.
     */
    private Cierre cerrarEnBase(Long id, Reservation leida, Evaluacion evaluacion, Criterio criterio,
                                boolean conLugar) {
        Reservation reserva = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));
        if (reserva.getEstado() == ReservationStatus.CONFIRMADA) {
            return new Cierre(Cierre.Tipo.RESUELTA, criterio.yaConfirmada().apply(reserva), null);
        }
        if (cambio(leida, reserva, criterio)) {
            return new Cierre(Cierre.Tipo.CAMBIO, null, null);
        }
        if (conLugar) {
            copiarRetenciones(leida, reserva);
            if (inventario.confirmarAsientos(reserva)) {
                reserva.getDetalles().forEach(d -> d.setPaymentStatus(PaymentStatus.PAGADO));
                CarritoCalculo.estadiasActivas(reserva).forEach(e -> e.setEstadoPago(PaymentStatus.PAGADO));
                reserva.setEstado(ReservationStatus.CONFIRMADA);
                reserva.setMotivoCancelacion(null);
                reserva.setTokenPagoConfirmacion(criterio.token());
                bookingRepository.save(reserva);
                return new Cierre(Cierre.Tipo.CONFIRMADA, ConfirmacionPagoResponse.confirmada(),
                        reservationMapper.toResponse(reserva));
            }
        }
        String motivo = evaluacion.motivoSinLugar();
        if (reserva.getEstado() != ReservationStatus.EXPIRADA) {
            // Los de una EXPIRADA ya los soltó el scheduler; pueden estar en otro carrito del usuario
            inventario.liberarAsientos(reserva);
        }
        reserva.getDetalles().forEach(d -> d.setPaymentStatus(PaymentStatus.CANCELADO));
        reserva.setEstado(ReservationStatus.CANCELADA);
        reserva.setMotivoCancelacion(motivo);
        bookingRepository.save(reserva);
        log.warn("Reserva {} cancelada al confirmar el pago {}: {}", id, criterio.token(), motivo);
        return new Cierre(Cierre.Tipo.SIN_LUGAR, ConfirmacionPagoResponse.cancelada(motivo,
                ConfirmacionPagoResponse.PAGO_TARDIO_SIN_DISPONIBILIDAD.equals(motivo)
                        ? "El pago llegó después del vencimiento y ya no hay lugar para todo el carrito."
                        : "Ya no hay lugar para todo el carrito."), reservationMapper.toResponse(reserva));
    }

    /**
     * El mismo pago reenviado es idempotente; otro pago sobre una reserva confirmada es un duplicado.
     * Una reserva CONFIRMADA sin token guardado (anterior a la columna) también responde PAGO_DUPLICADO:
     * es seguro solo porque esas bases se recrean (D21) y todas las confirmaciones nuevas guardan el token.
     */
    private static ConfirmacionPagoResponse yaConfirmada(Reservation reserva, String tokenPago) {
        return Objects.equals(reserva.getTokenPagoConfirmacion(), tokenPago)
                ? ConfirmacionPagoResponse.confirmada()
                : ConfirmacionPagoResponse.duplicado();
    }

    /** Exacto, sin redondear lo que llega: 819999.995 no paga 820000.00. La escala no importa. */
    private static boolean coincideElMonto(Reservation reserva, BigDecimal monto) {
        return monto != null && CarritoCalculo.montoTotal(reserva).compareTo(monto) == 0;
    }

    /** Lo que se leyó antes de llamar al hotel ya no es lo que hay en la base. */
    private static boolean cambio(Reservation leida, Reservation actual, Criterio criterio) {
        return actual.getEstado() != leida.getEstado()
                || !idsEstadias(actual).equals(idsEstadias(leida))
                || !new ArrayList<>(actual.getFlightIds()).equals(new ArrayList<>(leida.getFlightIds()))
                || !CarritoCalculo.datosCompletos(actual)
                || !criterio.montoCorrecto().test(actual)
                || !titulares(actual).equals(titulares(leida));
    }

    /** El hotel se confirma con el nombre del titular: si cambió, hay que confirmar con el vigente. */
    private static Map<Long, String> titulares(Reservation reserva) {
        Map<Long, String> nombres = new HashMap<>();
        CarritoCalculo.estadiasActivas(reserva).forEach(e -> nombres.put(e.getId(), e.getTitularNombre()));
        return nombres;
    }

    private static Set<Long> idsEstadias(Reservation reserva) {
        return CarritoCalculo.estadiasActivas(reserva).stream().map(EstadiaHotel::getId).collect(Collectors.toSet());
    }

    private static Set<UUID> retenciones(Reservation reserva) {
        return CarritoCalculo.estadiasActivas(reserva).stream().map(EstadiaHotel::getRetencionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * Suelta solo las retenciones que creó este intento (rescate o pago tardío): las que ya tenía el
     * carrito siguen siendo suyas (las libera el abandono o el vencimiento, o son de la reserva confirmada).
     */
    private void liberarNuevas(Reservation leida, Set<UUID> previas) {
        retenciones(leida).stream().filter(r -> !previas.contains(r)).forEach(inventario::liberarRetencion);
    }

    /** Si confirmarEstadias tomó una retención nueva (rescate o pago tardío), la reserva releída la guarda. */
    private static void copiarRetenciones(Reservation desde, Reservation hacia) {
        if (desde == hacia) {
            return;
        }
        Map<Long, UUID> retenciones = new HashMap<>();
        desde.getEstadias().forEach(e -> retenciones.put(e.getId(), e.getRetencionId()));
        hacia.getEstadias().forEach(e -> {
            UUID nueva = retenciones.get(e.getId());
            if (nueva != null) {
                e.setRetencionId(nueva);
            }
        });
    }

    private static String motivoDeCancelada(Reservation reserva) {
        String motivo = reserva.getMotivoCancelacion();
        return ConfirmacionPagoResponse.PAGO_TARDIO_SIN_DISPONIBILIDAD.equals(motivo)
                || ConfirmacionPagoResponse.SIN_DISPONIBILIDAD.equals(motivo)
                || GrupoCierre.MOTIVO_CANCELADO.equals(motivo)
                || GrupoCierre.MOTIVO_VENCIDO.equals(motivo)
                || GrupoCierre.MOTIVO_SIN_CONFIRMAR.equals(motivo)
                || ConfirmacionPagoResponse.MONTO_NO_COINCIDE.equals(motivo)
                ? motivo : ConfirmacionPagoResponse.RESERVA_CANCELADA;
    }

    /**
     * Descuenta el cupo informativo de flight-service (availableSeats). Mejor esfuerzo: el mapa de
     * asientos de este servicio es lo que manda y la reserva ya está confirmada.
     */
    private void descontarCupos(Reservation reserva) {
        for (UUID flightId : reserva.getFlightIds()) {
            try {
                String numero = flightClient.getFlightByNumber(flightId).getFlightNumber();
                flightClient.adjustSeats(numero, -reserva.getCantidadPasajeros());
            } catch (RuntimeException ex) {
                log.warn("No se pudo descontar el cupo del vuelo {} de la reserva {}: {}", flightId, reserva.getId(), ex.getMessage());
            }
        }
    }

    private record Evaluacion(ConfirmacionPagoResponse respuesta, Reservation reserva, String motivoSinLugar,
                              boolean renovar) {
        static Evaluacion fin(ConfirmacionPagoResponse respuesta) {
            return new Evaluacion(respuesta, null, null, false);
        }
    }

    /** aviso: lo que se manda por WebSocket después del commit (null si la reserva no cambió). */
    private record Cierre(Tipo tipo, ConfirmacionPagoResponse respuesta, ReservationResponse aviso) {
        enum Tipo { CONFIRMADA, SIN_LUGAR, RESUELTA, CAMBIO }
    }

    @Transactional(readOnly = true)
    public ReservationResponse obtenerReserva(Long id, Long authenticatedUserId) {
        return reservationMapper.toResponse(soporte.reservaDelUsuario(id, authenticatedUserId));
    }

    @Transactional(readOnly = true)
    public ReservationResponse obtenerReservaInterna(Long id) {
        Reservation reserva = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingException("RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));
        return reservationMapper.toResponse(reserva);
    }

    private PackageLookupResponse validarYObtenerPaquete(Long packageId, String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new BookingException("AUTORIZACION_REQUERIDA", "Se requiere token para el paquete.", HttpStatus.UNAUTHORIZED);
        }
        PackageLookupResponse paquete = packageClient.getPackageById(packageId, authHeader);
        if (paquete.getId() == null || !paquete.isActive()) {
            throw new BookingException("PAQUETE_INACTIVO", "El paquete no está activo.", HttpStatus.CONFLICT);
        }
        return paquete;
    }

    /** Informativo: se manda después del commit y si falla no cambia el resultado. */
    private void avisar(Long id, ReservationResponse aviso) {
        if (aviso == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSend("/topic/reserva/" + id, aviso);
        } catch (RuntimeException ex) {
            log.warn("No se pudo avisar el cambio de la reserva {}: {}", id, ex.getMessage());
        }
    }
}
