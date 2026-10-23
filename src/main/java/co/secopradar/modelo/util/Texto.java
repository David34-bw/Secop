package co.secopradar.modelo.util;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/** Normalización de texto compartida por el cruce y la búsqueda. */
public final class Texto {

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern NO_ALFANUM = Pattern.compile("[^a-z0-9]+");

    private Texto() {
    }

    /**
     * Texto comparable: minúsculas, sin tildes (ñ -> n), sin signos y con un
     * solo espacio entre palabras. Se aplica igual al proceso y al usuario.
     */
    public static String normalizar(String texto) {
        if (texto == null) return "";
        String sinTildes = MARCAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return NO_ALFANUM.matcher(sinTildes.toLowerCase()).replaceAll(" ").trim();
    }

    public static List<String> palabras(String texto) {
        String limpio = normalizar(texto);
        return limpio.isEmpty() ? List.of() : List.of(limpio.split(" "));
    }

    public static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    public static String recortar(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
