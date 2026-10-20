package co.secopradar.util;

import co.secopradar.dominio.RangoValor;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Formatos de pesos, fechas y estados (port de src/lib/format.js). */
public final class Formato {

    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Locale ES = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", ES);

    private Formato() {
    }

    public static String numero(long n) {
        DecimalFormatSymbols s = new DecimalFormatSymbols(ES);
        s.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", s).format(n);
    }

    public static String pesos(Long n) {
        return n == null || n == 0 ? "No informado" : "$" + numero(n);
    }

    /**
     * Días calendario en Colombia hasta el cierre: 0 = cierra hoy, 1 =
     * mañana, negativo = ya cerró. SECOP solo trae el día de cierre (se
     * guarda a las 23:59:59), así que contar horas daría "mañana" el mismo día.
     */
    public static Integer diasRestantes(OffsetDateTime fecha, Instant ahora) {
        if (fecha == null) return null;
        LocalDate cierre = fecha.atZoneSameInstant(BOGOTA).toLocalDate();
        LocalDate hoy = ahora.atZone(BOGOTA).toLocalDate();
        return (int) ChronoUnit.DAYS.between(hoy, cierre);
    }

    public static Integer diasRestantes(OffsetDateTime fecha) {
        return diasRestantes(fecha, Instant.now());
    }

    /** Estado visual: etiqueta y clase CSS. */
    public record Estado(String label, String clase) {
    }

    public static Estado estadoOportunidad(Integer dias) {
        if (dias != null && dias < 0) return new Estado("Cerrado", "cerrado");
        if (dias != null && dias <= 3) return new Estado("Cierra pronto", "urgente");
        return new Estado("Abierto", "abierto");
    }

    public static String textoCierre(Integer dias) {
        if (dias == null) return null;
        if (dias == 0) return "Cierra hoy";
        if (dias == 1) return "Cierra mañana";
        if (dias < 0) return "Cerró hace " + (-dias) + (dias == -1 ? " día" : " días");
        return "Cierra en " + dias + " días";
    }

    public static String labelDepartamento(String d) {
        return "No Definido".equals(d) ? "Sin departamento informado" : d;
    }

    public static String labelRango(RangoValor r) {
        if (r.min() == 0) return "Menos de " + pesos(r.max());
        if (r.max() == Long.MAX_VALUE) return "Más de " + pesos(r.min());
        return pesos(r.min()) + " - " + pesos(r.max());
    }

    public static String fechaLarga(OffsetDateTime f) {
        return f == null ? "" : f.atZoneSameInstant(BOGOTA).format(FECHA_LARGA);
    }

    public static String fechaHora(OffsetDateTime f) {
        return f == null ? "—" : f.atZoneSameInstant(BOGOTA).format(FECHA_HORA);
    }

    /** Inicio del día de hoy en Colombia. */
    public static OffsetDateTime inicioDeHoy() {
        return LocalDate.now(BOGOTA).atStartOfDay(BOGOTA).toOffsetDateTime();
    }
}
