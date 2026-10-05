package com.despescar.koiiaservice.service;

import com.despescar.koiiaservice.domain.DatosFaltantes;
import com.despescar.koiiaservice.domain.DatosViaje;
import com.despescar.koiiaservice.domain.KoiExtraccion;
import com.despescar.koiiaservice.domain.KoiExtraccionParser;
import com.despescar.koiiaservice.domain.KoiPreguntas;
import com.despescar.koiiaservice.domain.KoiPrompt;
import com.despescar.koiiaservice.dto.request.KoiConversationMessageRequest;
import com.despescar.koiiaservice.dto.response.KoiConversationResponse;
import com.despescar.koiiaservice.dto.response.KoiMensajeResponse;
import com.despescar.koiiaservice.dto.response.KoiRecommendationResponse;
import com.despescar.koiiaservice.dto.response.KoiSessionResponse;
import com.despescar.koiiaservice.entity.KoiConversationMessage;
import com.despescar.koiiaservice.entity.KoiConversationSession;
import com.despescar.koiiaservice.enums.ConversationStage;
import com.despescar.koiiaservice.enums.MessageRole;
import com.despescar.koiiaservice.enums.MissingInfoField;
import com.despescar.koiiaservice.enums.TipoOpcion;
import com.despescar.koiiaservice.enums.UserIntent;
import com.despescar.koiiaservice.exception.KoiSessionForbiddenException;
import com.despescar.koiiaservice.exception.KoiSessionNotFoundException;
import com.despescar.koiiaservice.recomendador.HotelCandidato;
import com.despescar.koiiaservice.recomendador.Motivos;
import com.despescar.koiiaservice.recomendador.PedidoRecomendacion;
import com.despescar.koiiaservice.recomendador.Recomendador;
import com.despescar.koiiaservice.recomendador.VueloCandidato;
import com.despescar.koiiaservice.repository.KoiConversationMessageRepository;
import com.despescar.koiiaservice.repository.KoiConversationSessionRepository;
import com.despescar.koiiaservice.service.ai.KoiModeloLenguaje;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversación de KOI (spec 3.7). El modelo solo conversa y extrae datos; qué falta, qué se
 * pregunta y qué opciones se muestran lo decide este servicio con funciones puras.
 */
@Slf4j
@Service
public class KoiConversationService {

    public static final String SALUDO = "¡Hola! Soy KOI ✨ Contame a dónde querés viajar, desde dónde salís, "
            + "cuántos viajan y con qué presupuesto, y te armo opciones de vuelo + hotel.";
    public static final String MODELO_CAIDO = "Uy, tuve un problema para procesar tu mensaje.";
    public static final String NO_ENTENDI = "Perdón, no te entendí bien.";
    public static final String FUERA_DE_TEMA = "Solo te puedo ayudar a armar viajes con DesPescar.";
    public static final String CATALOGO_CAIDO = "No pude consultar los vuelos y hoteles en este momento. "
            + "Escribime de nuevo en un ratito y lo vuelvo a intentar.";
    public static final String SEGUIMOS = "Si querés cambiar algo (fechas, presupuesto, viajeros o destino), "
            + "decímelo y busco de nuevo.";
    public static final String SIN_OPCIONES = "No encontré vuelos ni hoteles disponibles para esos datos. "
            + "Probá con otras fechas o con otro destino.";
    public static final String AVISO_FECHA_PASADA = "Esa fecha de salida ya pasó.";
    public static final String AVISO_VUELTA_INVALIDA = "La vuelta tiene que ser después de la ida y como mucho "
            + DatosViaje.MAX_NOCHES + " noches más tarde.";
    public static final String AVISO_NOCHES_INVALIDAS = "Puedo armar estadías de 1 a " + DatosViaje.MAX_NOCHES
            + " noches.";
    public static final String AVISO_VIAJEROS = "Puedo armar viajes de 1 a " + DatosViaje.MAX_VIAJEROS
            + " personas.";
    public static final String AVISO_PRESUPUESTO = "El presupuesto tiene que ser un monto mayor a cero.";
    private static final String REINTENTAR = "Probá de nuevo en un ratito.";
    private static final String OTRA_FORMA = "¿Me lo decís de otra forma?";
    private static final String PRECIOS_ORIENTATIVOS =
            "Los precios son orientativos: se confirman al agregarlas al carrito.";
    static final int TURNOS_DE_HISTORIAL = 10;
    private static final int MAX_TEXTO_CIUDAD = 120;
    private static final int MAX_ULTIMO_MENSAJE = 2000;
    private static final int MAX_RESPUESTA = 4000;
    private static final int MAX_COMENTARIO = 300;
    /** Plata o ids en el texto libre del modelo: el modelo nunca produce precios ni IDs. */
    private static final Pattern PLATA_O_ID = Pattern.compile(
            "(\\$|\\bars\\b|\\busd\\b|\\bpesos?\\b)\\s*\\d|\\d\\s*(\\$|\\bars\\b|\\busd\\b|\\bpesos?\\b)"
                    + "|\\bid\\b\\W{0,3}\\d",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MENCIONA_PRECIO = Pattern.compile(
            "\\$|\\bars\\b|\\busd\\b|\\bpesos?\\b|\\bprecios?\\b|\\bcuest\\w*|\\bcost\\w*|\\btotal\\b|\\bsale\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final KoiConversationSessionRepository sessionRepository;
    private final KoiConversationMessageRepository messageRepository;
    private final KoiModeloLenguaje modelo;
    private final KoiCatalogo catalogo;
    private final KoiOpcionesJson opcionesJson;
    private final Clock clock;

    public KoiConversationService(KoiConversationSessionRepository sessionRepository,
                                  KoiConversationMessageRepository messageRepository,
                                  KoiModeloLenguaje modelo, KoiCatalogo catalogo,
                                  KoiOpcionesJson opcionesJson, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.modelo = modelo;
        this.catalogo = catalogo;
        this.opcionesJson = opcionesJson;
        this.clock = clock;
    }

    /** Lo que se le contesta al usuario en este turno. */
    private record Turno(String texto, List<KoiRecommendationResponse> opciones) {
    }

    /** Datos del viaje sin valores inválidos y los avisos de lo que se descartó. */
    private record Limpieza(DatosViaje datos, List<String> avisos) {
    }

    @Transactional
    public KoiConversationResponse startSession(String userIdentifier) {
        KoiConversationSession session = new KoiConversationSession();
        session.setUserIdentifier(normalizeUserIdentifier(userIdentifier));
        session.setStage(ConversationStage.COLLECTING_INFO);
        session.setIntent(UserIntent.UNKNOWN);
        session.setLastAssistantMessage(SALUDO);
        session = sessionRepository.save(session);
        guardarMensaje(session, MessageRole.KOI, SALUDO, null);
        return KoiConversationResponse.builder()
                .sessionId(session.getId())
                .reply(SALUDO)
                .needsMoreInfo(true)
                .nextQuestion(null)
                .missingFields(List.of())
                .intent(session.getIntent())
                .stage(session.getStage())
                .recommendations(List.of())
                .build();
    }

    @Transactional(readOnly = true)
    public KoiSessionResponse getSession(UUID sessionId, String userIdentifier) {
        KoiConversationSession session = loadSession(sessionId);
        ensureSessionOwner(session, userIdentifier);
        return toSessionResponse(session);
    }

    /** Historial completo de la sesión, para que el chat siga después de /login o de recargar. */
    @Transactional(readOnly = true)
    public List<KoiMensajeResponse> historial(UUID sessionId, String userIdentifier) {
        KoiConversationSession session = loadSession(sessionId);
        ensureSessionOwner(session, userIdentifier);
        return messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId).stream()
                .map(m -> new KoiMensajeResponse(m.getRole(), m.getContent(), opcionesJson.leer(m.getOpcionesJson())))
                .toList();
    }

    @Transactional
    public KoiConversationResponse handleMessage(UUID sessionId, KoiConversationMessageRequest request,
                                                 String userIdentifier) {
        KoiConversationSession session = loadSession(sessionId);
        ensureSessionOwner(session, userIdentifier);

        String mensaje = request.getMessage().trim();
        List<KoiModeloLenguaje.Turno> historial = historialReciente(sessionId);
        guardarMensaje(session, MessageRole.USER, mensaje, null);

        LocalDate hoy = LocalDate.now(clock);
        Optional<KoiExtraccion> extraccion;
        String problemaDelModelo = null;
        try {
            extraccion = KoiExtraccionParser.parsear(modelo.completar(KoiPrompt.sistema(hoy), historial, mensaje));
            if (extraccion.isEmpty()) {
                problemaDelModelo = NO_ENTENDI;
            }
        } catch (RuntimeException ex) {
            log.warn("KOI: el modelo de lenguaje no respondió: {}", ex.getMessage());
            extraccion = Optional.empty();
            problemaDelModelo = MODELO_CAIDO;
        }

        DatosViaje antes = datosDe(session);
        Limpieza limpieza = limpiar(extraccion.map(e -> antes.combinar(e.aDatos())).orElse(antes), hoy);
        DatosViaje datos = limpieza.datos();
        guardarDatos(session, datos);
        List<MissingInfoField> faltan = DatosFaltantes.calcular(datos, hoy);
        String comentario = extraccion.map(KoiExtraccion::comentario)
                .map(KoiConversationService::sanearComentario).orElse(null);
        String avisos = limpieza.avisos().isEmpty() ? null : String.join(" ", limpieza.avisos());

        Turno turno;
        if (problemaDelModelo != null) {
            turno = preguntar(session, faltan, datos,
                    unir(problemaDelModelo, avisos),
                    NO_ENTENDI.equals(problemaDelModelo) ? OTRA_FORMA : REINTENTAR);
        } else if (extraccion.get().esFueraDeTema()) {
            turno = preguntar(session, faltan, datos,
                    unir(comentario != null ? comentario : FUERA_DE_TEMA, avisos), null);
        } else if (!faltan.isEmpty() || !listoParaRecomendar(datos)) {
            turno = preguntar(session, faltan, datos, unir(comentario, avisos), null);
        } else if (!datos.equals(antes) || session.getStage() != ConversationStage.RECOMMENDING) {
            turno = recomendar(session, datos, comentario);
        } else {
            turno = new Turno(unir(comentario, SEGUIMOS), List.of());
        }

        turno = new Turno(acotarRespuesta(turno.texto()), turno.opciones());
        session.setLastAssistantMessage(recortar(turno.texto(), MAX_ULTIMO_MENSAJE));
        session = sessionRepository.save(session);
        guardarMensaje(session, MessageRole.KOI, turno.texto(), opcionesJson.escribir(turno.opciones()));

        return KoiConversationResponse.builder()
                .sessionId(session.getId())
                .reply(turno.texto())
                .needsMoreInfo(!faltan.isEmpty())
                .nextQuestion(faltan.isEmpty() ? null : faltan.get(0))
                .missingFields(faltan)
                .intent(session.getIntent())
                .stage(session.getStage())
                .recommendations(turno.opciones())
                .build();
    }

    /**
     * Agrega la primera pregunta pendiente. Si no falta nada (por ejemplo, el modelo falló con
     * todos los datos completos) usa el cierre indicado y no cambia el stage.
     */
    private Turno preguntar(KoiConversationSession session, List<MissingInfoField> faltan, DatosViaje datos,
                            String base, String siNoFaltaNada) {
        if (faltan.isEmpty()) {
            return new Turno(unir(base, siNoFaltaNada), List.of());
        }
        MissingInfoField siguiente = faltan.get(0);
        session.setStage(ConversationStage.COLLECTING_INFO);
        session.setAwaitingField(siguiente);
        return new Turno(unir(base, KoiPreguntas.texto(siguiente, datos)), List.of());
    }

    private Turno recomendar(KoiConversationSession session, DatosViaje datos, String comentario) {
        session.setAwaitingField(null);
        TipoOpcion tipo = switch (datos.intencionEfectiva()) {
            case SOLO_VUELO -> TipoOpcion.VUELO;
            case SOLO_HOTEL -> TipoOpcion.HOTEL;
            default -> TipoOpcion.COMBO;
        };
        LocalDate vuelta = datos.vueltaEfectiva();
        int viajeros = datos.viajeros();
        String origen = recortar(datos.origen(), MAX_TEXTO_CIUDAD);
        String destino = recortar(datos.destino(), MAX_TEXTO_CIUDAD);
        List<KoiRecommendationResponse> opciones;
        try {
            List<VueloCandidato> idas = List.of();
            List<VueloCandidato> vueltas = List.of();
            if (tipo != TipoOpcion.HOTEL) {
                KoiCatalogo.VuelosCandidatos vuelos =
                        catalogo.vuelos(origen, destino, datos.fechaIda(), vuelta, viajeros);
                idas = vuelos.idas();
                vueltas = vuelos.vueltas();
            }
            List<HotelCandidato> hoteles = tipo == TipoOpcion.VUELO ? List.of()
                    : catalogo.hoteles(destino, datos.fechaIda(), vuelta, viajeros);
            opciones = Recomendador.recomendar(
                    new PedidoRecomendacion(tipo, datos.presupuesto(), viajeros, datos.fechaIda(), vuelta),
                    idas, vueltas, hoteles);
        } catch (RuntimeException ex) {
            log.warn("KOI: no pude consultar el catálogo o armar las opciones: {}", ex.toString());
            session.setStage(ConversationStage.READY_TO_RECOMMEND);
            return new Turno(unir(comentario, CATALOGO_CAIDO), List.of());
        }

        session.setStage(ConversationStage.RECOMMENDING);
        if (opciones.isEmpty()) {
            return new Turno(unir(comentario, SIN_OPCIONES), List.of());
        }
        String comentarioSinPrecios = comentario != null && MENCIONA_PRECIO.matcher(comentario).find()
                ? null : comentario;
        return new Turno(unir(comentarioSinPrecios, resumen(opciones, datos.presupuesto())), opciones);
    }

    /** Texto libre del modelo: sin precios ni ids y de largo acotado; null si no sirve. */
    static String sanearComentario(String comentario) {
        if (comentario == null || comentario.isBlank()) {
            return null;
        }
        String limpio = comentario.trim();
        if (PLATA_O_ID.matcher(limpio).find()) {
            return null;
        }
        return recortar(limpio, MAX_COMENTARIO);
    }

    private static String resumen(List<KoiRecommendationResponse> opciones, BigDecimal presupuesto) {
        int n = opciones.size();
        String cuantas = n == 1 ? "1 opción" : n + " opciones";
        boolean seExceden = opciones.stream().anyMatch(o -> o.excedeEn() != null);
        if (presupuesto == null) {
            return "Te armé " + cuantas + ". " + PRECIOS_ORIENTATIVOS;
        }
        if (seExceden) {
            return "No encontré opciones dentro de tu presupuesto de " + Motivos.pesos(presupuesto)
                    + ". Te muestro " + (n == 1 ? "la más cercana" : "las " + n + " más cercanas") + ". "
                    + PRECIOS_ORIENTATIVOS;
        }
        return "Te armé " + cuantas + " dentro de tu presupuesto de " + Motivos.pesos(presupuesto) + ". "
                + PRECIOS_ORIENTATIVOS;
    }

    /** Defensa extra: el recomendador nunca recibe fechas nulas aunque DatosFaltantes cambie. */
    private static boolean listoParaRecomendar(DatosViaje d) {
        if (d.fechaIda() == null || d.viajeros() == null || d.destino() == null) {
            return false;
        }
        UserIntent intencion = d.intencionEfectiva();
        if (intencion != UserIntent.SOLO_HOTEL && d.origen() == null) {
            return false;
        }
        return intencion == UserIntent.SOLO_VUELO || d.vueltaEfectiva() != null;
    }

    /**
     * Descarta lo que el parser de K2 deja pasar sin validar: fechas pasadas, rangos invertidos o
     * de más de 30 noches, viajeros fuera de rango y presupuestos no positivos. El dato queda en
     * null para que DatosFaltantes lo vuelva a pedir, con un aviso que explica por qué.
     */
    private static Limpieza limpiar(DatosViaje d, LocalDate hoy) {
        List<String> avisos = new ArrayList<>();
        BigDecimal presupuesto = d.presupuesto();
        if (presupuesto != null && presupuesto.signum() <= 0) {
            presupuesto = null;
            avisos.add(AVISO_PRESUPUESTO);
        }
        Integer viajeros = d.viajeros();
        if (viajeros != null && (viajeros < 1 || viajeros > DatosViaje.MAX_VIAJEROS)) {
            viajeros = null;
            avisos.add(AVISO_VIAJEROS);
        }
        LocalDate ida = d.fechaIda();
        YearMonth mes = d.mesIda();
        if ((ida != null && ida.isBefore(hoy)) || (mes != null && mes.isBefore(YearMonth.from(hoy)))) {
            ida = null;
            mes = null;
            avisos.add(AVISO_FECHA_PASADA);
        }
        Integer noches = d.noches();
        if (noches != null && (noches < 1 || noches > DatosViaje.MAX_NOCHES)) {
            noches = null;
            avisos.add(AVISO_NOCHES_INVALIDAS);
        }
        LocalDate vuelta = d.fechaVuelta();
        if (vuelta != null && (vuelta.isBefore(hoy) || (ida != null && (!vuelta.isAfter(ida)
                || ChronoUnit.DAYS.between(ida, vuelta) > DatosViaje.MAX_NOCHES)))) {
            vuelta = null;
            avisos.add(AVISO_VUELTA_INVALIDA);
        }
        return new Limpieza(new DatosViaje(d.intencion(), presupuesto, viajeros, d.origen(), d.destino(), ida, mes,
                vuelta, noches), avisos);
    }

    private static String unir(String... partes) {
        List<String> presentes = new ArrayList<>();
        for (String parte : partes) {
            if (parte != null && !parte.isBlank()) {
                presentes.add(parte.trim());
            }
        }
        return String.join(" ", presentes);
    }

    /** La columna `content` admite 4000 caracteres; una respuesta más larga rompería el guardado. */
    static String acotarRespuesta(String texto) {
        return recortar(texto, MAX_RESPUESTA);
    }

    private static String recortar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        return limpio.length() <= max ? limpio : limpio.substring(0, max);
    }

    private static DatosViaje datosDe(KoiConversationSession s) {
        return new DatosViaje(s.getIntent(), s.getBudget(), s.getTravelers(), s.getOrigin(), s.getDestination(),
                s.getDepartureDate(), mes(s.getTravelMonth()), s.getReturnDate(), s.getNights());
    }

    private static YearMonth mes(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(valor.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static void guardarDatos(KoiConversationSession s, DatosViaje d) {
        s.setIntent(d.intencion() == null ? UserIntent.UNKNOWN : d.intencion());
        s.setBudget(d.presupuesto());
        s.setTravelers(d.viajeros());
        s.setOrigin(recortar(d.origen(), MAX_TEXTO_CIUDAD));
        s.setDestination(recortar(d.destino(), MAX_TEXTO_CIUDAD));
        s.setDepartureDate(d.fechaIda());
        s.setTravelMonth(d.mesIda() == null ? null : d.mesIda().toString());
        s.setReturnDate(d.fechaVuelta());
        s.setNights(d.noches());
    }

    /** Los últimos turnos guardados en el servidor, del más viejo al más nuevo. */
    private List<KoiModeloLenguaje.Turno> historialReciente(UUID sessionId) {
        List<KoiConversationMessage> nuevosPrimero =
                messageRepository.findTop10BySessionIdOrderByCreatedAtDescIdDesc(sessionId);
        List<KoiModeloLenguaje.Turno> turnos = new ArrayList<>();
        for (int i = nuevosPrimero.size() - 1; i >= 0; i--) {
            KoiConversationMessage m = nuevosPrimero.get(i);
            turnos.add(new KoiModeloLenguaje.Turno(m.getRole(), m.getContent()));
        }
        return turnos;
    }

    private void guardarMensaje(KoiConversationSession session, MessageRole rol, String texto, String opciones) {
        KoiConversationMessage m = new KoiConversationMessage();
        m.setSession(session);
        m.setRole(rol);
        m.setContent(texto);
        m.setOpcionesJson(opciones);
        messageRepository.save(m);
    }

    private KoiConversationSession loadSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new KoiSessionNotFoundException(sessionId));
    }

    private void ensureSessionOwner(KoiConversationSession session, String userIdentifier) {
        String normalizedUser = normalizeUserIdentifier(userIdentifier);
        if (session.getUserIdentifier() != null && !session.getUserIdentifier().equals(normalizedUser)) {
            throw new KoiSessionForbiddenException();
        }
    }

    private static String normalizeUserIdentifier(String userIdentifier) {
        return userIdentifier == null || userIdentifier.isBlank() ? null
                : userIdentifier.trim().toLowerCase(Locale.ROOT);
    }

    private static KoiSessionResponse toSessionResponse(KoiConversationSession session) {
        return KoiSessionResponse.builder()
                .sessionId(session.getId())
                .stage(session.getStage())
                .intent(session.getIntent())
                .awaitingField(session.getAwaitingField())
                .userIdentifier(session.getUserIdentifier())
                .budget(session.getBudget())
                .travelers(session.getTravelers())
                .origin(session.getOrigin())
                .destination(session.getDestination())
                .departureDate(session.getDepartureDate())
                .travelMonth(session.getTravelMonth())
                .returnDate(session.getReturnDate())
                .nights(session.getNights())
                .lastAssistantMessage(session.getLastAssistantMessage())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}
