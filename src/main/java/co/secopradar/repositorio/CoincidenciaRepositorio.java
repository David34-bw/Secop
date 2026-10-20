package co.secopradar.repositorio;

import co.secopradar.dominio.Proceso;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class CoincidenciaRepositorio {

    /** Una coincidencia con su proceso y el perfil que la encontró. */
    public record Fila(int id, int score, boolean notificado, int perfilId, String perfil, Proceso proceso) {
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public CoincidenciaRepositorio(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    /** Coincidencias guardadas de un perfil: proceso -> puntaje. */
    public Map<String, Integer> dePerfil(int perfilId) {
        Map<String, Integer> m = new HashMap<>();
        jdbc.query("SELECT proceso_id, score FROM coincidencias WHERE perfil_id = ?",
                rs -> {
                    m.put(rs.getString("proceso_id"), rs.getInt("score"));
                }, perfilId);
        return m;
    }

    public void insertar(int perfilId, Map<String, Integer> scores, boolean notificado) {
        List<Map.Entry<String, Integer>> filas = List.copyOf(scores.entrySet());
        jdbc.batchUpdate("INSERT INTO coincidencias (perfil_id, proceso_id, score, notificado) VALUES (?, ?, ?, ?)",
                filas, 500, (ps, e) -> {
                    ps.setInt(1, perfilId);
                    ps.setString(2, e.getKey());
                    ps.setInt(3, e.getValue());
                    ps.setBoolean(4, notificado);
                });
    }

    public void actualizarScores(int perfilId, Map<String, Integer> scores) {
        List<Map.Entry<String, Integer>> filas = List.copyOf(scores.entrySet());
        jdbc.batchUpdate("UPDATE coincidencias SET score = ? WHERE perfil_id = ? AND proceso_id = ?",
                filas, 500, (ps, e) -> {
                    ps.setInt(1, e.getValue());
                    ps.setInt(2, perfilId);
                    ps.setString(3, e.getKey());
                });
    }

    public int eliminar(int perfilId, Collection<String> procesoIds) {
        if (procesoIds.isEmpty()) return 0;
        return named.update("DELETE FROM coincidencias WHERE perfil_id = :perfil AND proceso_id IN (:ids)",
                new MapSqlParameterSource("perfil", perfilId).addValue("ids", procesoIds));
    }

    /**
     * Coincidencias de los perfiles activos del usuario con procesos que no
     * han cerrado. Los duplicados y el límite del plan se resuelven en Java.
     */
    public List<Fila> vigentesDeUsuario(int userId) {
        return jdbc.query("SELECT c.id AS c_id, c.score, c.notificado, pf.id AS pf_id, pf.nombre AS perfil, "
                        + prefijo("p", ProcesoRepositorio.COLUMNAS)
                        + " FROM coincidencias c JOIN perfiles pf ON pf.id = c.perfil_id "
                        + "JOIN procesos p ON p.id_del_proceso = c.proceso_id "
                        + "WHERE pf.user_id = ? AND pf.activo AND (p.fecha_cierre IS NULL OR p.fecha_cierre >= ?)",
                (rs, i) -> new Fila(rs.getInt("c_id"), rs.getInt("score"), rs.getBoolean("notificado"),
                        rs.getInt("pf_id"), rs.getString("perfil"), ProcesoRepositorio.MAPPER.mapRow(rs, i)),
                userId, OffsetDateTime.now());
    }

    private static String prefijo(String alias, String columnas) {
        StringBuilder sb = new StringBuilder();
        for (String c : columnas.split(",")) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(alias).append('.').append(c.trim());
        }
        return sb.toString();
    }

    public void marcarNotificadas(Collection<Integer> ids) {
        if (ids.isEmpty()) return;
        named.update("UPDATE coincidencias SET notificado = TRUE WHERE id IN (:ids)", new MapSqlParameterSource("ids", ids));
    }

    public long contar() {
        return jdbc.queryForObject("SELECT count(*) FROM coincidencias", Long.class);
    }
}
