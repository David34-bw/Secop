package co.secopradar.modelo.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pasa a tipo oración los textos de SECOP que vienen todo en mayúsculas
 * ("PRESTACIÓN DEL SERVICIO DE ASEO" -> "Prestación del servicio de
 * aseo"). Los textos que ya traen minúsculas se dejan como vienen.
 *
 * Se conservan en mayúsculas: las siglas conocidas, palabras de 2 a 5
 * letras sin vocales (SST, PQRS), números romanos y palabras con dígitos o
 * abreviaturas con puntos (S.A., E.S.P.).
 */
public final class TextoOracion {

    private static final Locale ES = Locale.forLanguageTag("es-CO");

    private static final Map<String, String> FORMA_ESPECIAL = Map.of("WI-FI", "Wi-Fi", "WIFI", "WiFi");

    public static final Set<String> SIGLAS = Set.of(
            // Pedidas explícitamente
            "SENA", "ICBF", "IPS", "ESE", "TIC", "TICS", "SIG", "CCTV", "UPS", "WI-FI", "WIFI",
            // Entidades y programas frecuentes en SECOP
            "DANE", "DIAN", "INPEC", "INVIMA", "IDEAM", "IGAC", "ICFES", "SECOP", "ESAP", "ANM", "ANI",
            "ANLA", "CAR", "UAE", "UNGRD", "FINDETER", "FONADE", "PAE", "SOAT", "EPS", "ARL", "AFP",
            "SETP", "POT", "PGIRS", "MIPYME", "MIPYMES", "ONU", "DDHH", "DIH", "SAS", "NIT", "RUT", "IVA",
            "SMMLV", "NTC", "ISO", "SST", "SGSST", "PTAR", "PTAP", "IED", "ESP",
            // Tecnología
            "TI", "IT", "PC", "CPU", "LED", "LAN", "WAN", "VPN", "SAAS", "SOC", "EDR", "XDR", "PAM", "OJS",
            "BPO", "ERP", "CRM", "GPS", "GNSS", "RFID", "HVAC", "NAS", "SSD", "DVR", "NVR", "IP",
            "PQRS", "PQRSD");

    private static final Pattern VOCAL = Pattern.compile("[AEIOUÁÉÍÓÚÜ]");
    private static final Pattern ROMANO = Pattern.compile("^(?=[IVXL])L?X{0,3}(IX|IV|V?I{0,3})$");
    private static final Pattern MAYUS_2_5 = Pattern.compile("^\\p{Lu}{2,5}$");
    private static final Pattern TIENE_LETRA = Pattern.compile("\\p{L}");
    private static final Pattern TIENE_DIGITO = Pattern.compile("\\d");
    private static final Pattern ABREVIATURA = Pattern.compile("\\p{L}\\.\\p{L}");
    private static final Pattern BORDES = Pattern.compile("^([^\\p{L}\\p{N}]*)(.*?)([^\\p{L}\\p{N}]*)$", Pattern.DOTALL);
    private static final Pattern SEPARADORES = Pattern.compile("(?<=[-/])|(?=[-/])");
    private static final Pattern ESPACIOS = Pattern.compile("(?<=\\s)(?=\\S)|(?<=\\S)(?=\\s)");
    private static final Pattern INICIO_ORACION = Pattern.compile("(^|[.!?]\\s+)([^\\p{L}]*)(\\p{Ll})");
    private static final Pattern INICIO = Pattern.compile("^([^\\p{L}]*)(\\p{Ll})");

    /** Palabras que van en minúscula dentro de un nombre propio (salvo al inicio). */
    private static final Set<String> CONECTORES = Set.of("de", "del", "la", "las", "los", "y", "e", "en", "para", "por", "a", "el");

    private TextoOracion() {
    }

    private static boolean conservaMayusculas(String palabra) {
        if (SIGLAS.contains(palabra)) return true;
        if (MAYUS_2_5.matcher(palabra).matches() && !VOCAL.matcher(palabra).find()) return true;
        return ROMANO.matcher(palabra).matches() && palabra.length() <= 4;
    }

    private static String capitalizar(String p) {
        if (p.isEmpty()) return p;
        int primero = p.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(primero))) + p.substring(Character.charCount(primero));
    }

    private static String parte(String p, boolean titulo) {
        if (p.isEmpty() || !TIENE_LETRA.matcher(p).find()) return p;
        if (TIENE_DIGITO.matcher(p).find()) return p;
        if (ABREVIATURA.matcher(p).find()) return p;
        if (FORMA_ESPECIAL.containsKey(p)) return FORMA_ESPECIAL.get(p);
        if (conservaMayusculas(p)) return p;
        String minuscula = p.toLowerCase(ES);
        return titulo && !CONECTORES.contains(minuscula) ? capitalizar(minuscula) : minuscula;
    }

    private static String palabra(String token, boolean titulo) {
        Matcher m = BORDES.matcher(token);
        if (!m.matches()) return token;
        String antes = m.group(1), nucleo = m.group(2), despues = m.group(3);
        if (FORMA_ESPECIAL.containsKey(nucleo)) return antes + FORMA_ESPECIAL.get(nucleo) + despues;
        if (SIGLAS.contains(nucleo)) return token;
        // "SEMESTRE-III", "BIENES/SERVICIOS": cada parte por separado.
        StringBuilder convertido = new StringBuilder();
        for (String p : SEPARADORES.split(nucleo)) {
            convertido.append(p.equals("-") || p.equals("/") ? p : parte(p, titulo));
        }
        return antes + convertido + despues;
    }

    public static boolean esTodoMayusculas(String texto) {
        if (texto == null) return false;
        boolean hayMayuscula = false;
        for (int i = 0; i < texto.length(); ) {
            int c = texto.codePointAt(i);
            if (Character.isLowerCase(c)) return false;
            if (Character.isUpperCase(c)) hayMayuscula = true;
            i += Character.charCount(c);
        }
        return hayMayuscula;
    }

    private static String convertir(String s, boolean titulo) {
        List<String> partes = new ArrayList<>(List.of(ESPACIOS.split(s)));
        StringBuilder sb = new StringBuilder();
        for (String t : partes) {
            sb.append(t.isBlank() ? t : palabra(t, titulo));
        }
        return sb.toString();
    }

    public static String aOracion(String texto) {
        if (texto == null || !esTodoMayusculas(texto)) return texto;
        String resultado = convertir(texto, false);
        // Mayúscula al inicio y después de punto, "?" o "!" seguidos de espacio.
        Matcher m = INICIO_ORACION.matcher(resultado);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + m.group(2) + m.group(3).toUpperCase(ES)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Para nombres de entidades: mayúscula inicial en cada palabra, conectores en minúscula. */
    public static String aTitulo(String texto) {
        if (texto == null || !esTodoMayusculas(texto)) return texto;
        String resultado = convertir(texto, true);
        Matcher m = INICIO.matcher(resultado);
        if (m.find()) {
            return m.group(1) + m.group(2).toUpperCase(ES) + resultado.substring(m.end());
        }
        return resultado;
    }
}
