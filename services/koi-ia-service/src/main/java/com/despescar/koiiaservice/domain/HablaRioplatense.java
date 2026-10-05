package com.despescar.koiiaservice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entiende de forma determinística lo que la gente dice en criollo: "2 palos", "300 lucas",
 * "somos dos", "un finde", "ofreceme algo". Lo que se encuentra acá pisa lo que haya dicho el
 * modelo, para no depender de que la IA conozca la jerga.
 */
public final class HablaRioplatense {

    /** Un fin de semana: viernes de ida y dos noches. */
    public record Finde(LocalDate ida, int noches) {
    }

    private static final BigDecimal MILLON = new BigDecimal("1000000");
    private static final BigDecimal MIL = new BigDecimal("1000");
    private static final BigDecimal MEDIO = new BigDecimal("0.5");

    private static final String NUMERO = "(\\d+(?:[.,]\\d+)*)";
    private static final String PALABRA = "(un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|medio)";
    private static final Map<String, Integer> PALABRAS = Map.ofEntries(
            Map.entry("un", 1), Map.entry("una", 1), Map.entry("uno", 1), Map.entry("dos", 2), Map.entry("tres", 3),
            Map.entry("cuatro", 4), Map.entry("cinco", 5), Map.entry("seis", 6), Map.entry("siete", 7),
            Map.entry("ocho", 8), Map.entry("nueve", 9), Map.entry("diez", 10));

    /** "2 palos", "dos palos y medio", "1,5 millones", "2M", "palo y medio", "medio palo", "un millon". */
    private static final Pattern MILLONES = Pattern.compile(
            "(?:" + NUMERO + "|" + PALABRA + ")?\\s*(palos?|millon(?:es)?|m)\\b(\\s+y\\s+medio)?");
    /** "300 lucas", "300 mil", "800k", "1.500 lucas". */
    private static final Pattern MILES = Pattern.compile(NUMERO + "\\s*(lucas?|mil|k)\\b");
    /** Un monto escrito completo: "$ 1.200.000", "2000000 pesos". */
    private static final Pattern COMPLETO = Pattern.compile("(?<![\\d.,])(\\d{1,3}(?:\\.\\d{3}){2,}|\\d{6,})(?![\\d.,])");

    /** "somos un montón" no dice cuántos: acá no entra "un". */
    private static final Pattern SOMOS = Pattern.compile(
            "\\bsomos\\s+(\\d{1,2}|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\\b");
    private static final Pattern EN_PAREJA = Pattern.compile(
            "\\bcon\\s+mi\\s+(novia|novio|pareja|mujer|marido|esposa|esposo|senora|señora)\\b");
    private static final Pattern CON_LOS_VIEJOS = Pattern.compile("\\bcon\\s+mis\\s+(viejos|padres)\\b");
    private static final Pattern SOLO = Pattern.compile("\\b(viajo|voy|salgo)\\s+(solo|sola)\\b");

    private static final Pattern FINDE = Pattern.compile("\\bfinde\\b|\\bfin\\s+de\\s+semana\\b");
    private static final Pattern FECHA_EXPLICITA = Pattern.compile("\\b\\d{1,2}\\s*(de\\s+[a-z]+|/\\s*\\d)");

    private static final Pattern EXPLORAR = Pattern.compile(
            "\\bsorprend|\\ba\\s+donde\\s+sea\\b|\\bdonde\\s+sea\\b|\\bcualquier\\s+(lado|lugar|destino)\\b"
                    + "|\\balg[uú]n\\s+(viaje|destino|lugar|lado)\\b|\\balgo\\s+(para|con|de)\\b"
                    + "|\\b(ofrece|propone|mostra|tira|pasa)(me)?\\s+(algo|opciones|alg[uú]n|ideas?|viajes?|vuelos|destinos)"
                    + "|\\bque\\s+me\\s+(recomend|propon|ofrec|suger)|\\b(opciones|ideas)\\s+para\\b"
                    + "|\\bque\\s+(hay|puedo|me\\s+da|me\\s+alcanza)\\b|\\brecomend\\w*\\s+(algo|alg[uú]n)\\b");

    private HablaRioplatense() {
    }

    /** El presupuesto si el texto lo dice en criollo o en número completo; vacío si no. */
    public static Optional<BigDecimal> presupuesto(String texto) {
        String t = TextoBusqueda.normalizar(texto);
        if (t.isEmpty()) {
            return Optional.empty();
        }
        Matcher m = MILLONES.matcher(t);
        while (m.find()) {
            if ("m".equals(m.group(3)) && m.group(1) == null) {
                continue; // una "m" suelta solo es plata detrás de un número ("2M")
            }
            BigDecimal cantidad;
            if (m.group(1) != null) {
                cantidad = decimal(m.group(1));
            } else if (m.group(2) != null) {
                cantidad = "medio".equals(m.group(2)) ? MEDIO : BigDecimal.valueOf(PALABRAS.get(m.group(2)));
            } else {
                cantidad = BigDecimal.ONE; // "palo y medio", "millon y medio"
            }
            if (m.group(4) != null) {
                cantidad = cantidad.add(MEDIO);
            }
            return Optional.of(pesos(cantidad.multiply(MILLON)));
        }
        m = MILES.matcher(t);
        if (m.find()) {
            return Optional.of(pesos(decimal(m.group(1)).multiply(MIL)));
        }
        m = COMPLETO.matcher(t);
        if (m.find()) {
            return Optional.of(pesos(new BigDecimal(m.group(1).replace(".", ""))));
        }
        return Optional.empty();
    }

    /** Cuántos viajan cuando el texto no deja dudas ("somos 2", "con mi novia", "viajo solo"). */
    public static Optional<Integer> viajeros(String texto) {
        String t = TextoBusqueda.normalizar(texto);
        Matcher m = SOMOS.matcher(t);
        if (m.find()) {
            String valor = m.group(1);
            return Optional.of(PALABRAS.containsKey(valor) ? PALABRAS.get(valor) : Integer.parseInt(valor));
        }
        if (EN_PAREJA.matcher(t).find()) {
            return Optional.of(2);
        }
        if (CON_LOS_VIEJOS.matcher(t).find()) {
            return Optional.of(3);
        }
        if (SOLO.matcher(t).find()) {
            return Optional.of(1);
        }
        return Optional.empty();
    }

    /** "Un finde": el próximo viernes (nunca hoy) y dos noches, salvo que nombre una fecha. */
    public static Optional<Finde> finde(String texto, LocalDate hoy) {
        String t = TextoBusqueda.normalizar(texto);
        if (!FINDE.matcher(t).find() || FECHA_EXPLICITA.matcher(t).find()) {
            return Optional.empty();
        }
        return Optional.of(new Finde(hoy.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)), 2));
    }

    /** Pide opciones sin destino: "ofreceme algo", "a donde sea", "sorprendeme", "qué me recomendás". */
    public static boolean quiereExplorar(String texto) {
        return EXPLORAR.matcher(TextoBusqueda.normalizar(texto)).find();
    }

    /** "1,5" y "1.5" son decimales; "1.500" es mil quinientos. */
    private static BigDecimal decimal(String numero) {
        String n = numero;
        if (n.matches("\\d{1,3}(\\.\\d{3})+")) {
            n = n.replace(".", "");
        } else {
            n = n.replace(",", ".");
        }
        return new BigDecimal(n);
    }

    private static BigDecimal pesos(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP);
    }
}
