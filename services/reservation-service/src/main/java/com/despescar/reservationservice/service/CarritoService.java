package com.despescar.reservationservice.service;

import com.despescar.reservationservice.client.HotelClient;
import com.despescar.reservationservice.dto.carrito.AgregarEstadiaRequest;
import com.despescar.reservationservice.dto.carrito.TitularRequest;
import com.despescar.reservationservice.dto.hotel.RetencionHotelRequest;
import com.despescar.reservationservice.dto.hotel.RetencionHotelResponse;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.TramoPolitica;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.mapper.ReservationMapper;
import com.despescar.reservationservice.repository.BookingRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El carrito visto como ítems: estadías (con retención en hotel-service), quitar el vuelo,
 * titulares y abandono. Las llamadas a hotel-service van siempre antes de tocar asientos, para que
 * los bloqueos FOR UPDATE no crucen llamadas HTTP.
 */
@Service
@Slf4j
public class CarritoService {

    static final String MOTIVO_CARRITO_VACIO = "CARRITO_VACIO";
    static final String MOTIVO_ABANDONADA = "ABANDONADA";

    private final BookingRepository bookingRepository;
    private final CarritoSoporte soporte;
    private final HotelClient hotelClient;
    private final InventarioCarrito inventario;
    private final ReservationMapper mapper;
    private final Validator validator;
    private final TransactionTemplate transacciones;

    public CarritoService(BookingRepository bookingRepository, CarritoSoporte soporte, HotelClient hotelClient,
                          InventarioCarrito inventario, ReservationMapper mapper, Validator validator,
                          TransactionTemplate transacciones) {
        this.bookingRepository = bookingRepository;
        this.soporte = soporte;
        this.hotelClient = hotelClient;
        this.inventario = inventario;
        this.mapper = mapper;
        this.validator = validator;
        this.transacciones = transacciones;
    }

    @Transactional(readOnly = true)
    public Optional<ReservationResponse> obtenerCarrito(Long usuarioId) {
        return soporte.carritoActivo(usuarioId).map(mapper::toResponse);
    }

    /**
     * Retiene las habitaciones hasta la hora del carrito y copia precio, política y horario. No es
     * transaccional: la llamada a hotel-service va sin conexión ni transacción abiertas. El carrito se
     * crea (si no hay) antes, porque la retención lleva su id, y se agrega la estadía en una transacción
     * corta que revalida que el carrito siga abierto y sin vencer. Si algo falla después de retener, la
     * retención se libera (mejor esfuerzo).
     */
    public ReservationResponse agregarEstadia(AgregarEstadiaRequest pedido, Long usuarioId) {
        boolean carritoNuevo = soporte.carritoActivo(usuarioId).isEmpty();
        Reservation carrito = carritoNuevo ? soporte.crearCarrito(usuarioId) : soporte.carritoActivo(usuarioId).orElseThrow();
        Long carritoId = carrito.getId();
        RetencionHotelResponse retencion;
        try {
            retencion = hotelClient.crearRetencion(new RetencionHotelRequest(
                    carritoId, usuarioId, pedido.hotelId(), pedido.tipoHabitacionId(), pedido.checkIn(),
                    pedido.checkOut(), pedido.cantidadHabitaciones(), pedido.huespedes(), inventario.vencimiento(carrito)));
        } catch (RuntimeException ex) {
            if (carritoNuevo) {
                descartarCarritoVacio(carritoId);
            }
            throw ex;
        }
        try {
            return transacciones.execute(estado -> {
                Reservation actual = bookingRepository.findByIdForUpdate(carritoId).orElseThrow(() -> new BookingException(
                        "CARRITO_NO_ENCONTRADO", "El carrito ya no existe.", HttpStatus.NOT_FOUND));
                soporte.verificarModificable(actual);
                if (!CarritoCalculo.MONEDA.equalsIgnoreCase(retencion.getMoneda())) {
                    throw new BookingException("MONEDA_NO_SOPORTADA", "El carrito solo acepta precios en pesos.", HttpStatus.CONFLICT);
                }
                EstadiaHotel estadia = estadiaDesde(retencion, pedido);
                estadia.setReservation(actual);
                actual.getEstadias().add(estadia);
                actual.setEstado(CarritoCalculo.estadoAbierto(actual));
                return mapper.toResponse(bookingRepository.saveAndFlush(actual));
            });
        } catch (RuntimeException ex) {
            // La retención ya existe en hotel-service: no se deja colgada
            inventario.liberarRetencion(retencion.getRetencionId());
            throw ex;
        }
    }

    /** Quita una estadía activa y libera su retención. Si el carrito queda vacío, se cierra (D11). */
    @Transactional
    public Optional<ReservationResponse> quitarEstadia(Long estadiaId, Long usuarioId) {
        Reservation carrito = carritoAbierto(usuarioId);
        EstadiaHotel estadia = CarritoCalculo.estadiasActivas(carrito).stream()
                .filter(e -> estadiaId.equals(e.getId()))
                .findFirst()
                .orElseThrow(() -> new BookingException("ESTADIA_NO_ENCONTRADA",
                        "La estadía no está en tu carrito.", HttpStatus.NOT_FOUND));
        carrito.getEstadias().remove(estadia);
        Optional<ReservationResponse> respuesta = cerrarSiVacio(carrito);
        // Recién con el commit: si se deshace, la estadía sigue en el carrito y no hay que soltar su retención
        despuesDelCommit(() -> inventario.liberarRetencion(estadia.getRetencionId()));
        return respuesta;
    }

    /** Quita la parte de vuelo y libera los asientos de los pasajeros cargados (D29). */
    @Transactional
    public Optional<ReservationResponse> quitarVuelo(Long usuarioId) {
        Reservation carrito = carritoAbierto(usuarioId);
        if (!CarritoCalculo.tieneVuelo(carrito)) {
            throw new BookingException("SIN_VUELO", "El carrito no tiene vuelo.", HttpStatus.NOT_FOUND);
        }
        inventario.liberarAsientos(carrito);
        carrito.getDetalles().clear();
        carrito.getFlightIds().clear();
        carrito.setBaggageIds(new ArrayList<>());
        carrito.setCantidadPasajeros(0);
        carrito.setPrecioVueloPorPasajero(null);
        carrito.setTarifasVuelo(null);
        carrito.setSalidaVuelo(null);
        carrito.setPackageId(null);
        return cerrarSiVacio(carrito);
    }

    /** Un titular por cada estadía activa; con los datos completos el carrito pasa a PENDIENTE_PAGO. */
    @Transactional
    public ReservationResponse cargarTitulares(Long reservaId, List<TitularRequest> titulares, Long usuarioId) {
        // Bloqueada: los titulares solo cambian filas de estadías (sin tocar la versión de la reserva),
        // así que sin el bloqueo una copia vieja podría pisar una reserva recién confirmada
        Reservation reserva = soporte.reservaDelUsuarioBloqueada(reservaId, usuarioId);
        validar(titulares);
        soporte.verificarModificable(reserva);

        List<EstadiaHotel> activas = CarritoCalculo.estadiasActivas(reserva);
        Map<Long, TitularRequest> porEstadia = new HashMap<>();
        titulares.forEach(t -> porEstadia.put(t.estadiaId(), t));
        boolean coinciden = !activas.isEmpty()
                && porEstadia.size() == titulares.size()
                && porEstadia.size() == activas.size()
                && activas.stream().allMatch(e -> porEstadia.containsKey(e.getId()));
        if (!coinciden) {
            throw new BookingException("TITULARES_INCOMPLETOS",
                    "Cargá un titular por cada estadía del carrito.", HttpStatus.BAD_REQUEST);
        }
        for (EstadiaHotel estadia : activas) {
            TitularRequest t = porEstadia.get(estadia.getId());
            estadia.setTitularNombre(t.nombre().trim());
            estadia.setTitularDni(t.dni().trim());
            estadia.setTitularTelefono(t.telefono().trim());
        }
        reserva.setEstado(CarritoCalculo.estadoAbierto(reserva));
        return mapper.toResponse(bookingRepository.save(reserva));
    }

    /**
     * Abandona un carrito sin pagar (D12). Sobre una reserva pagada responde 409: se cancela por ítem
     * (E4). En la transacción, con la reserva bloqueada, se sueltan los asientos y se marca CANCELADA;
     * las retenciones se liberan después del commit (HTTP, mejor esfuerzo), sin conexión ni locks
     * abiertos. Una confirmación de pago en curso o confirma antes (y esto responde 409) o ve la
     * CANCELADA y reembolsa: nunca queda una reserva pagada con sus retenciones liberadas.
     */
    @Transactional
    public void abandonar(Long reservaId, Long usuarioId) {
        Reservation reserva = soporte.reservaDelUsuarioBloqueada(reservaId, usuarioId);
        if (reserva.getEstado() == ReservationStatus.CONFIRMADA) {
            throw new BookingException("USAR_CANCELACION_POR_ITEM",
                    "La reserva ya está pagada: se cancela por ítem desde Mis reservas.", HttpStatus.CONFLICT);
        }
        if (reserva.getEstado() == ReservationStatus.CANCELADA || reserva.getEstado() == ReservationStatus.EXPIRADA) {
            throw new BookingException("RESERVA_CERRADA", "La reserva ya está cerrada.", HttpStatus.CONFLICT);
        }
        inventario.liberarAsientos(reserva);
        reserva.getDetalles().forEach(d -> d.setPaymentStatus(PaymentStatus.CANCELADO));
        reserva.setEstado(ReservationStatus.CANCELADA);
        reserva.setMotivoCancelacion(MOTIVO_ABANDONADA);
        bookingRepository.save(reserva);
        // Los ids se toman ahora: después del commit la entidad ya no tiene sesión
        List<UUID> retenciones = CarritoCalculo.estadiasActivas(reserva).stream().map(EstadiaHotel::getRetencionId).toList();
        despuesDelCommit(() -> retenciones.forEach(inventario::liberarRetencion));
        log.info("El usuario {} abandonó el carrito {}", usuarioId, reservaId);
    }

    private Reservation carritoAbierto(Long usuarioId) {
        Reservation carrito = soporte.carritoAbiertoBloqueado(usuarioId).orElseThrow(() -> new BookingException(
                "CARRITO_NO_ENCONTRADO", "No tenés un carrito activo.", HttpStatus.NOT_FOUND));
        soporte.verificarModificable(carrito); // vencido: 410 CARRITO_EXPIRADO
        return carrito;
    }

    /** El carrito se creó para esta retención y la retención falló: no queda un carrito vacío abierto. */
    private void descartarCarritoVacio(Long carritoId) {
        try {
            transacciones.executeWithoutResult(estado -> bookingRepository.findById(carritoId)
                    .filter(c -> CarritoCalculo.cantidadItems(c) == 0)
                    .ifPresent(c -> {
                        c.setEstado(ReservationStatus.CANCELADA);
                        c.setMotivoCancelacion(MOTIVO_CARRITO_VACIO);
                        bookingRepository.save(c);
                    }));
        } catch (RuntimeException ex) {
            log.warn("No se pudo cerrar el carrito vacío {}: {}", carritoId, ex.getMessage());
        }
    }

    private static void despuesDelCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }

    private Optional<ReservationResponse> cerrarSiVacio(Reservation carrito) {
        if (CarritoCalculo.cantidadItems(carrito) == 0) {
            carrito.setEstado(ReservationStatus.CANCELADA);
            carrito.setMotivoCancelacion(MOTIVO_CARRITO_VACIO);
            bookingRepository.save(carrito);
            return Optional.empty();
        }
        carrito.setEstado(CarritoCalculo.estadoAbierto(carrito));
        return Optional.of(mapper.toResponse(bookingRepository.save(carrito)));
    }

    private void validar(List<TitularRequest> titulares) {
        if (titulares == null || titulares.isEmpty()) {
            throw new BookingException("TITULARES_INCOMPLETOS",
                    "Cargá un titular por cada estadía del carrito.", HttpStatus.BAD_REQUEST);
        }
        for (TitularRequest titular : titulares) {
            if (titular == null) {
                throw new BookingException("VALIDACION", "Hay un titular vacío.", HttpStatus.BAD_REQUEST);
            }
            Set<ConstraintViolation<TitularRequest>> errores = validator.validate(titular);
            if (!errores.isEmpty()) {
                ConstraintViolation<TitularRequest> error = errores.iterator().next();
                throw new BookingException("VALIDACION",
                        "Dato inválido en " + error.getPropertyPath() + ": " + error.getMessage(), HttpStatus.BAD_REQUEST);
            }
        }
    }

    private static EstadiaHotel estadiaDesde(RetencionHotelResponse r, AgregarEstadiaRequest pedido) {
        EstadiaHotel e = new EstadiaHotel();
        e.setHotelId(r.getHotelId() != null ? r.getHotelId() : pedido.hotelId());
        e.setHotelNombre(r.getHotelNombre());
        e.setCiudad(r.getCiudad());
        e.setTipoHabitacionId(r.getTipoHabitacionId() != null ? r.getTipoHabitacionId() : pedido.tipoHabitacionId());
        e.setTipoHabitacionNombre(r.getTipoHabitacionNombre());
        e.setCheckIn(r.getCheckIn());
        e.setCheckOut(r.getCheckOut());
        e.setCantidadHabitaciones(r.getCantidad());
        e.setHuespedes(r.getHuespedes());
        e.setRetencionId(r.getRetencionId());
        e.setPrecioTotal(r.getPrecioTotal().setScale(2, RoundingMode.HALF_UP));
        e.setMoneda(CarritoCalculo.MONEDA);
        List<TramoPolitica> politica = new ArrayList<>();
        r.getPoliticaCancelacion().forEach(t -> politica.add(new TramoPolitica(t.horasAntes(), t.porcentajeReembolso())));
        e.setPoliticaCancelacion(politica);
        e.setHoraCheckIn(r.getHoraCheckIn());
        e.setZonaHoraria(r.getZonaHoraria());
        return e;
    }
}
