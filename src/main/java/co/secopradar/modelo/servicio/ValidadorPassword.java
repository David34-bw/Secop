package co.secopradar.modelo.servicio;

import co.secopradar.modelo.util.Texto;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reglas de una contraseña segura. Devuelve cada regla con si se cumple,
 * para que la vista muestre la lista mientras la persona escribe.
 */
public final class ValidadorPassword {

    public static final int MIN = 10;
    /** BCrypt solo usa los primeros 72 bytes. */
    public static final int MAX_BYTES = 72;

    public enum Nivel { MUY_DEBIL, DEBIL, MEDIA, FUERTE }

    /** Una regla y si la contraseña la cumple. */
    public record Regla(String descripcion, boolean cumple) {
    }

    public record Resultado(List<Regla> reglas, Nivel nivel) {
        public boolean valida() {
            return reglas.stream().allMatch(Regla::cumple);
        }

        /** Primera regla que falta, para mostrar como error. */
        public String primerError() {
            return reglas.stream().filter(r -> !r.cumple()).map(Regla::descripcion).findFirst().orElse(null);
        }

        /** Entre 0 y 1, para la barra de fuerza. */
        public double progreso() {
            return reglas.isEmpty() ? 0 : reglas.stream().filter(Regla::cumple).count() / (double) reglas.size();
        }
    }

    /** Contraseñas y palabras demasiado comunes (se comparan sin números ni símbolos). */
    private static final Set<String> COMUNES = Set.of(
            "password", "contrasena", "clave", "qwerty", "qwertyuiop", "asdfgh", "asdfghjkl", "zxcvbn", "iloveyou",
            "teamo", "admin", "administrador", "welcome", "bienvenido", "letmein", "secop", "secopradar", "radar",
            "colombia", "bogota", "medellin", "football", "futbol", "monkey", "dragon", "princess", "sunshine",
            "abc", "abcdef", "usuario", "user", "login", "master", "hola", "holamundo");

    private static final String[] FILAS_TECLADO = {"qwertyuiop", "asdfghjklñ", "zxcvbnm", "1234567890"};

    private ValidadorPassword() {
    }

    /**
     * Evalúa la contraseña. email y nombre (pueden ser null) sirven para
     * rechazar contraseñas que los contienen.
     */
    public static Resultado evaluar(String password, String email, String nombre) {
        String p = password == null ? "" : password;
        List<Regla> reglas = new ArrayList<>();
        reglas.add(new Regla("Al menos " + MIN + " caracteres", p.length() >= MIN));
        reglas.add(new Regla("Una letra mayúscula", p.chars().anyMatch(Character::isUpperCase)));
        reglas.add(new Regla("Una letra minúscula", p.chars().anyMatch(Character::isLowerCase)));
        reglas.add(new Regla("Un número", p.chars().anyMatch(Character::isDigit)));
        reglas.add(new Regla("Un símbolo (por ejemplo ! # $ % & * ? @)",
                p.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))));
        reglas.add(new Regla("Sin espacios", !p.isEmpty() && p.chars().noneMatch(Character::isWhitespace)));
        reglas.add(new Regla("Sin 3 caracteres iguales seguidos (aaa, 111)", !p.isEmpty() && !tresIguales(p)));
        reglas.add(new Regla("Sin secuencias como 1234, abcd o qwer", !p.isEmpty() && !tieneSecuencia(p.toLowerCase())));
        reglas.add(new Regla("Que no sea una contraseña común", !p.isEmpty() && !esComun(p)));
        reglas.add(new Regla("Que no contenga tu correo ni tu nombre", !p.isEmpty() && !contieneDatosPersonales(p, email, nombre)));
        if (p.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            reglas.add(new Regla("Máximo " + MAX_BYTES + " caracteres", false));
        }

        long cumplidas = reglas.stream().filter(Regla::cumple).count();
        Nivel nivel;
        if (cumplidas == reglas.size() && p.length() >= 14) nivel = Nivel.FUERTE;
        else if (cumplidas == reglas.size()) nivel = Nivel.MEDIA;
        else if (cumplidas >= reglas.size() - 3) nivel = Nivel.DEBIL;
        else nivel = Nivel.MUY_DEBIL;
        return new Resultado(reglas, nivel);
    }

    private static boolean tresIguales(String p) {
        for (int i = 2; i < p.length(); i++) {
            if (p.charAt(i) == p.charAt(i - 1) && p.charAt(i) == p.charAt(i - 2)) return true;
        }
        return false;
    }

    /** 4 caracteres seguidos en orden (abcd, 1234, dcba) o de una fila del teclado (qwer, asdf). */
    private static boolean tieneSecuencia(String p) {
        for (int i = 3; i < p.length(); i++) {
            int d1 = p.charAt(i - 2) - p.charAt(i - 3);
            int d2 = p.charAt(i - 1) - p.charAt(i - 2);
            int d3 = p.charAt(i) - p.charAt(i - 1);
            if ((d1 == 1 || d1 == -1) && d1 == d2 && d2 == d3 && Character.isLetterOrDigit(p.charAt(i))) return true;
        }
        for (String fila : FILAS_TECLADO) {
            for (int i = 0; i + 4 <= fila.length(); i++) {
                String trozo = fila.substring(i, i + 4);
                if (p.contains(trozo) || p.contains(new StringBuilder(trozo).reverse())) return true;
            }
        }
        return false;
    }

    /** "Password2026!" o "Colombia#123" siguen siendo comunes: se quitan números y símbolos antes de comparar. */
    private static boolean esComun(String p) {
        String base = Texto.normalizar(p).replaceAll("[^a-z]", "");
        if (base.isEmpty()) return true; // solo números y símbolos
        if (COMUNES.contains(base)) return true;
        for (String c : COMUNES) {
            if (c.length() >= 6 && base.contains(c) && base.length() - c.length() <= 3) return true;
        }
        return false;
    }

    private static boolean contieneDatosPersonales(String p, String email, String nombre) {
        String clave = Texto.normalizar(p).replace(" ", "");
        List<String> partes = new ArrayList<>();
        if (email != null && email.contains("@")) {
            for (String trozo : Texto.palabras(email.substring(0, email.indexOf('@')))) partes.add(trozo);
        }
        if (nombre != null) partes.addAll(Texto.palabras(nombre));
        for (String parte : partes) {
            if (parte.length() >= 4 && clave.contains(parte)) return true;
        }
        return false;
    }
}
