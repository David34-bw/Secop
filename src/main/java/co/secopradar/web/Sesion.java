package co.secopradar.web;

import co.secopradar.dominio.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Sesión del usuario: solo se guarda su id en la HttpSession. */
public final class Sesion {

    static final String CLAVE_ID = "usuarioId";
    static final String ATRIBUTO_USUARIO = "usuarioActual";

    private Sesion() {
    }

    public static void iniciar(HttpServletRequest req, Usuario u) {
        // Nueva sesión al entrar: evita fijación de sesión.
        HttpSession vieja = req.getSession(false);
        if (vieja != null) vieja.invalidate();
        req.getSession(true).setAttribute(CLAVE_ID, u.id());
    }

    public static void cerrar(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s != null) s.invalidate();
    }

    public static Integer id(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s == null ? null : (Integer) s.getAttribute(CLAVE_ID);
    }

    /** Usuario cargado por el interceptor en las rutas protegidas. */
    public static Usuario usuario(HttpServletRequest req) {
        return (Usuario) req.getAttribute(ATRIBUTO_USUARIO);
    }

    public static String ip(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
