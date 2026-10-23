package co.secopradar.modelo;

/**
 * Usuario que inició sesión en esta ventana. Solo se guarda su id: el
 * resto se vuelve a leer de la base para tener siempre el plan al día.
 */
public final class Sesion {

    private static Integer usuarioId;

    private Sesion() {
    }

    public static void iniciar(Usuario u) {
        usuarioId = u.id();
    }

    public static void cerrar() {
        usuarioId = null;
    }

    public static Integer usuarioId() {
        return usuarioId;
    }

    public static boolean activa() {
        return usuarioId != null;
    }
}
