package co.secopradar.repositorio;

import co.secopradar.dominio.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class UsuarioRepositorio {

    private static final String COLUMNAS =
            "id, email, nombre, rol, plan, prueba_hasta, gracia_hasta, prepago_plan, prepago_hasta, alertas_activas, created_at";

    private static final RowMapper<Usuario> MAPPER = (rs, i) -> new Usuario(
            rs.getInt("id"), rs.getString("email"), rs.getString("nombre"), rs.getString("rol"),
            rs.getString("plan"), Filas.fecha(rs, "prueba_hasta"), Filas.fecha(rs, "gracia_hasta"),
            rs.getString("prepago_plan"), Filas.fecha(rs, "prepago_hasta"),
            rs.getBoolean("alertas_activas"), Filas.fecha(rs, "created_at"));

    private final JdbcTemplate jdbc;

    public UsuarioRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Usuario> porId(int id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM users WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<Usuario> porEmail(String email) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM users WHERE email = ?", MAPPER, email).stream().findFirst();
    }

    public Optional<String> hashDe(String email) {
        return jdbc.queryForList("SELECT password_hash FROM users WHERE email = ?", String.class, email)
                .stream().filter(h -> h != null).findFirst();
    }

    public List<Usuario> todos() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM users ORDER BY id", MAPPER);
    }

    /** Usuarios que pueden recibir alertas (correo verificado y alertas activas). */
    public List<Usuario> conAlertasActivas() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM users WHERE email_verificado AND alertas_activas ORDER BY id", MAPPER);
    }

    public int contar() {
        return jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);
    }

    public int crear(String email, String hash, String nombre, String rol, OffsetDateTime pruebaHasta,
                     String versionTerminos, String versionPrivacidad) {
        GeneratedKeyHolder llave = new GeneratedKeyHolder();
        OffsetDateTime ahora = OffsetDateTime.now();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (email, password_hash, nombre, rol, prueba_hasta, email_verificado, "
                            + "terminos_aceptados_at, terminos_version, privacidad_aceptada_at, privacidad_version) "
                            + "VALUES (?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, email);
            ps.setString(2, hash);
            ps.setString(3, nombre);
            ps.setString(4, rol);
            ps.setObject(5, pruebaHasta);
            ps.setObject(6, ahora);
            ps.setString(7, versionTerminos);
            ps.setObject(8, ahora);
            ps.setString(9, versionPrivacidad);
            return ps;
        }, llave);
        Map<String, Object> keys = llave.getKeys();
        return ((Number) (keys != null && keys.size() == 1 ? keys.values().iterator().next() : llave.getKey())).intValue();
    }

    public void cambiarAlertas(int id, boolean activas) {
        jdbc.update("UPDATE users SET alertas_activas = ? WHERE id = ?", activas, id);
    }

    public void cambiarNombre(int id, String nombre) {
        jdbc.update("UPDATE users SET nombre = ? WHERE id = ?", nombre, id);
    }

    public void cambiarPassword(int id, String hash) {
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", hash, id);
    }

    /** Cambio manual de plan desde /admin (reemplaza el cobro de Mercado Pago). */
    public void asignarPlan(int id, String plan) {
        jdbc.update("UPDATE users SET plan = ?, gracia_hasta = NULL WHERE id = ?", plan, id);
    }

    public void asignarRol(int id, String rol) {
        jdbc.update("UPDATE users SET rol = ? WHERE id = ?", rol, id);
    }

    public void reiniciarPrueba(int id, OffsetDateTime hasta) {
        jdbc.update("UPDATE users SET prueba_hasta = ? WHERE id = ?", hasta, id);
    }

    public void eliminar(int id) {
        jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    /** Gracia vencida tras un cobro rechazado: el dato pasa a Gratis. */
    public int vencerGracias() {
        return jdbc.update("UPDATE users SET plan = 'gratis', gracia_hasta = NULL "
                + "WHERE gracia_hasta IS NOT NULL AND gracia_hasta < ? AND plan <> 'gratis'", OffsetDateTime.now());
    }
}
