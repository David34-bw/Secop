package co.secopradar.modelo.servicio;

import co.secopradar.modelo.util.Texto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reglas para que el nombre de una persona tenga sentido: solo letras (con
 * tildes y ñ), espacios, apóstrofo y guion; palabras con vocales; sin
 * letras repetidas de más, sin teclazos ("asdf") y sin palabras de relleno
 * ("prueba", "usuario").
 */
public final class ValidadorNombre {

    public static final int MIN = 2;
    public static final int MAX = 60;
    private static final int MAX_PALABRAS = 5;

    private static final Locale ES = Locale.forLanguageTag("es-CO");
    private static final Pattern PERMITIDOS = Pattern.compile("^[\\p{L}' -]+$");
    private static final Pattern TIENE_DIGITO = Pattern.compile("\\d");
    private static final Pattern VOCAL = Pattern.compile("[aeiouy]");
    private static final Pattern TRES_IGUALES = Pattern.compile("(\\p{L})\\1\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern CINCO_CONSONANTES = Pattern.compile("[bcdfghjklmnpqrstvwxz]{5}");

    /** Partículas que pueden ir en minúscula y ser cortas: "María de la Cruz", "Juan y Pérez". */
    private static final Set<String> PARTICULAS = Set.of("de", "del", "la", "las", "los", "y", "e", "da", "do", "dos", "van", "von", "di", "le");

    /** Palabras que no son un nombre (se comparan sin tildes). */
    private static final Set<String> RELLENO = Set.of(
            "prueba", "pruebas", "test", "tester", "usuario", "user", "admin", "administrador", "nombre", "apellido",
            "ninguno", "ninguna", "nadie", "anonimo", "anonima", "null", "none", "nada", "xxx", "abc", "hola",
            "asdf", "qwerty", "demo", "ejemplo", "fulano", "mengano", "perengano", "nn");

    /** Secuencias de teclado que delatan un teclazo. */
    private static final List<String> TECLAZOS = List.of("qwer", "wert", "erty", "asdf", "sdfg", "dfgh", "fghj", "ghjk", "hjkl",
            "zxcv", "xcvb", "cvbn", "vbnm", "poiu", "lkjh", "mnbv", "abcd", "bcde");

    private ValidadorNombre() {
    }

    /** Quita espacios de más: "  maría   josé " -> "maría josé". */
    public static String limpiar(String nombre) {
        return nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
    }

    /** Devuelve el mensaje de error, o null si el nombre es válido. */
    public static String validar(String crudo) {
        String nombre = limpiar(crudo);
        if (nombre.isEmpty()) return "Escribe tu nombre";
        if (nombre.length() < MIN) return "El nombre es muy corto";
        if (nombre.length() > MAX) return "El nombre es muy largo (máximo " + MAX + " caracteres)";
        if (TIENE_DIGITO.matcher(nombre).find()) return "El nombre no puede tener números";
        if (!PERMITIDOS.matcher(nombre).matches()) {
            return "Usa solo letras, espacios, guion o apóstrofo (sin símbolos como @, ., _ o emojis)";
        }

        String[] palabras = nombre.split(" ");
        if (palabras.length > MAX_PALABRAS) return "Escribe tu nombre y apellidos (máximo " + MAX_PALABRAS + " palabras)";

        boolean hayPalabraReal = false;
        for (String palabra : palabras) {
            String p = Texto.normalizar(palabra).replace(" ", "");
            if (p.isEmpty()) return "Cada parte del nombre debe tener letras";
            if (palabra.startsWith("-") || palabra.startsWith("'") || palabra.endsWith("-") || palabra.endsWith("'")) {
                return "El guion o el apóstrofo deben ir entre letras (ej: María-José, D'Alessandro)";
            }
            if (PARTICULAS.contains(p)) continue;
            if (p.length() < 2) return "Escribe el nombre completo, sin iniciales sueltas";
            if (RELLENO.contains(p)) return "Escribe tu nombre real";
            if (!VOCAL.matcher(p).find()) return "«" + palabra + "» no parece un nombre (no tiene vocales)";
            if (TRES_IGUALES.matcher(palabra).find()) return "«" + palabra + "» tiene letras repetidas de más";
            if (CINCO_CONSONANTES.matcher(p).find()) return "«" + palabra + "» no parece un nombre";
            for (String t : TECLAZOS) {
                if (p.contains(t)) return "«" + palabra + "» no parece un nombre";
            }
            hayPalabraReal = true;
        }
        if (!hayPalabraReal) return "Escribe tu nombre";
        if (RELLENO.contains(Texto.normalizar(nombre))) return "Escribe tu nombre real";
        return null;
    }

    /**
     * Mayúscula inicial en cada palabra y partículas en minúscula:
     * "MARÍA DE LA cruz" -> "María de la Cruz", "maría-josé" -> "María-José".
     */
    public static String formatear(String crudo) {
        String nombre = limpiar(crudo).toLowerCase(ES);
        List<String> salida = new ArrayList<>();
        String[] palabras = nombre.split(" ");
        for (int i = 0; i < palabras.length; i++) {
            String p = palabras[i];
            if (i > 0 && PARTICULAS.contains(Texto.normalizar(p))) {
                salida.add(p);
            } else {
                salida.add(capitalizarPartes(p));
            }
        }
        return String.join(" ", salida);
    }

    private static String capitalizarPartes(String palabra) {
        StringBuilder sb = new StringBuilder();
        boolean siguienteMayuscula = true;
        for (char c : palabra.toCharArray()) {
            sb.append(siguienteMayuscula ? Character.toUpperCase(c) : c);
            siguienteMayuscula = c == '-' || c == '\'';
        }
        return sb.toString();
    }
}
