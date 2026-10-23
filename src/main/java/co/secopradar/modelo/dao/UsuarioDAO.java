package co.secopradar.modelo.dao;

import co.secopradar.modelo.Usuario;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Acceso a la tabla users. */
public class UsuarioDAO {

    private static final String COLUMNAS =
            "id, email, nombre, rol, plan, prueba_hasta, gracia_hasta, prepago_plan, prepago_hasta, alertas_activas, created_at";

    private static final BaseDatos.Mapeador<Usuario> MAPEADOR = rs -> new Usuario(
            rs.getInt("id"), rs.getString("email"), rs.getString("nombre"), rs.getString("rol"),
            rs.getString("plan"), Filas.fecha(rs, "prueba_hasta"), Filas.fecha(rs, "gracia_hasta"),
            rs.getString("prepago_plan"), Filas.fecha(rs, "prepago_hasta"),
            rs.getBoolean("alertas_activas"), Filas.fecha(rs, "created_at"));

    public Optional<Usuario> porId(int id) {
        return BaseDatos.uno("SELECT " + COLUMNAS + " FROM users WHERE id = ?", MAPEADOR, id);
    }

    public Optional<Usuario> porEmail(String email) {
        return BaseDatos.uno("SELECT " + COLUMNAS + " FROM users WHERE email = ?", MAPEADOR, email);
    }

    public Optional<String> hashDe(String email) {
        return BaseDatos.uno("SELECT password_hash FROM users WHERE email = ?", rs -> rs.getString(1), email)
                .filter(h -> h != null && !h.isEmpty());
    }

    public List<Usuario> todos() {
        return BaseDatos.consultar("SELECT " + COLUMNAS + " FROM users ORDER BY id", MAPEADOR);
    }

    public int contar() {
        return (int) BaseDatos.numero("SELECT count(*) FROM users");
    }

    public int crear(String email, String hash, String nombre, String rol, OffsetDateTime pruebaHasta,
                     String versionTerminos, String versionPrivacidad) {
        OffsetDateTime ahora = OffsetDateTime.now();
        return BaseDatos.insertar("INSERT INTO users (email, password_hash, nombre, rol, prueba_hasta, email_verificado, "
                        + "terminos_aceptados_at, terminos_version, privacidad_aceptada_at, privacidad_version) "
                        + "VALUES (?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?)",
                email, hash, nombre, rol, pruebaHasta, ahora, versionTerminos, ahora, versionPrivacidad);
    }

    public void cambiarNombre(int id, String nombre) {
        BaseDatos.actualizar("UPDATE users SET nombre = ? WHERE id = ?", nombre, id);
    }

    public void cambiarPassword(int id, String hash) {
        BaseDatos.actualizar("UPDATE users SET password_hash = ? WHERE id = ?", hash, id);
    }

    public void cambiarAvisos(int id, boolean activos) {
        BaseDatos.actualizar("UPDATE users SET alertas_activas = ? WHERE id = ?", activos, id);
    }

    /** Cambio manual de plan desde Administración (reemplaza el cobro de Mercado Pago). */
    public void asignarPlan(int id, String plan) {
        BaseDatos.actualizar("UPDATE users SET plan = ?, gracia_hasta = NULL WHERE id = ?", plan, id);
    }

    public void asignarRol(int id, String rol) {
        BaseDatos.actualizar("UPDATE users SET rol = ? WHERE id = ?", rol, id);
    }

    public void reiniciarPrueba(int id, OffsetDateTime hasta) {
        BaseDatos.actualizar("UPDATE users SET prueba_hasta = ? WHERE id = ?", hasta, id);
    }

    /** Gracia vencida tras un cobro rechazado: el dato pasa a Gratis. */
    public int vencerGracias() {
        return BaseDatos.actualizar("UPDATE users SET plan = 'gratis', gracia_hasta = NULL "
                + "WHERE gracia_hasta IS NOT NULL AND gracia_hasta < ? AND plan <> 'gratis'", OffsetDateTime.now());
    }
}
