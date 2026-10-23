package co.secopradar.modelo.dao;

import co.secopradar.modelo.Proceso;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Acceso a la tabla coincidencias (qué proceso encontró cada perfil y con qué puntaje). */
public class CoincidenciaDAO {

    /** Una coincidencia con su proceso y el perfil que la encontró. */
    public record Fila(int id, int score, boolean notificado, int perfilId, String perfil, Proceso proceso) {
    }

    /** Coincidencias guardadas de un perfil: proceso -> puntaje. */
    public Map<String, Integer> dePerfil(int perfilId) {
        Map<String, Integer> m = new HashMap<>();
        BaseDatos.consultar("SELECT proceso_id, score FROM coincidencias WHERE perfil_id = ?",
                rs -> m.put(rs.getString("proceso_id"), rs.getInt("score")), perfilId);
        return m;
    }

    public void insertar(int perfilId, Map<String, Integer> scores, boolean notificado) {
        BaseDatos.lote("INSERT INTO coincidencias (perfil_id, proceso_id, score, notificado) VALUES (?, ?, ?, ?)",
                scores.entrySet(), (ps, e) -> {
                    ps.setInt(1, perfilId);
                    ps.setString(2, e.getKey());
                    ps.setInt(3, e.getValue());
                    ps.setBoolean(4, notificado);
                });
    }

    public void actualizarScores(int perfilId, Map<String, Integer> scores) {
        BaseDatos.lote("UPDATE coincidencias SET score = ? WHERE perfil_id = ? AND proceso_id = ?",
                scores.entrySet(), (ps, e) -> {
                    ps.setInt(1, e.getValue());
                    ps.setInt(2, perfilId);
                    ps.setString(3, e.getKey());
                });
    }

    public int eliminar(int perfilId, Collection<String> procesoIds) {
        if (procesoIds.isEmpty()) return 0;
        Object[] params = new Object[procesoIds.size() + 1];
        params[0] = perfilId;
        int i = 1;
        for (String id : procesoIds) params[i++] = id;
        return BaseDatos.actualizar("DELETE FROM coincidencias WHERE perfil_id = ? AND proceso_id IN ("
                + BaseDatos.marcadores(procesoIds.size()) + ")", params);
    }

    /**
     * Coincidencias de los perfiles activos del usuario con procesos que no
     * han cerrado. Los duplicados y el límite del plan se resuelven en Java.
     */
    public List<Fila> vigentesDeUsuario(int userId) {
        return BaseDatos.consultar("SELECT c.id AS c_id, c.score, c.notificado, pf.id AS pf_id, pf.nombre AS perfil, "
                        + prefijo("p", ProcesoDAO.COLUMNAS)
                        + " FROM coincidencias c JOIN perfiles pf ON pf.id = c.perfil_id "
                        + "JOIN procesos p ON p.id_del_proceso = c.proceso_id "
                        + "WHERE pf.user_id = ? AND pf.activo AND (p.fecha_cierre IS NULL OR p.fecha_cierre >= ?)",
                rs -> new Fila(rs.getInt("c_id"), rs.getInt("score"), rs.getBoolean("notificado"),
                        rs.getInt("pf_id"), rs.getString("perfil"), ProcesoDAO.MAPEADOR.mapear(rs)),
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

    /** Marca como vistas (ya avisadas) las coincidencias. */
    public void marcarNotificadas(Collection<Integer> ids) {
        if (ids.isEmpty()) return;
        BaseDatos.actualizar("UPDATE coincidencias SET notificado = TRUE WHERE id IN ("
                + BaseDatos.marcadores(ids.size()) + ")", ids.toArray());
    }

    public long contar() {
        return BaseDatos.numero("SELECT count(*) FROM coincidencias");
    }
}
