package co.secopradar.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Registro de cada corrida del cron (ingesta, cruce y alertas), para /admin. */
@Repository
public class LogRepositorio {

    private final JdbcTemplate jdbc;

    public LogRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void ingesta(int leidos, int nuevos, int actualizados, boolean exito, String error) {
        jdbc.update("INSERT INTO ingesta_log (leidos, nuevos, actualizados, exito, error) VALUES (?, ?, ?, ?, ?)",
                leidos, nuevos, actualizados, exito, recortar(error));
    }

    public void coincidencias(int perfiles, int procesos, int nuevas, int quitadas, boolean exito, String error) {
        jdbc.update("INSERT INTO coincidencias_log (perfiles, procesos, nuevas, quitadas, exito, error) VALUES (?, ?, ?, ?, ?, ?)",
                perfiles, procesos, nuevas, quitadas, exito, recortar(error));
    }

    public void alertas(int candidatos, int enviados, int fallidos, int omitidos, boolean exito, String error) {
        jdbc.update("INSERT INTO alertas_log (candidatos, enviados, fallidos, omitidos_por_limite, exito, error) "
                + "VALUES (?, ?, ?, ?, ?, ?)", candidatos, enviados, fallidos, omitidos, exito, recortar(error));
    }

    public void envioAlerta(int userId, int coincidencias, boolean prueba) {
        jdbc.update("INSERT INTO alertas_envios (user_id, coincidencias, prueba) VALUES (?, ?, ?)", userId, coincidencias, prueba);
    }

    public int alertasEnviadasDesde(OffsetDateTime desde) {
        return jdbc.queryForObject("SELECT count(*) FROM alertas_envios WHERE enviado_at >= ?", Integer.class, desde);
    }

    public int alertasPrevias(int userId) {
        return jdbc.queryForObject("SELECT count(*) FROM alertas_envios WHERE user_id = ? AND NOT prueba", Integer.class, userId);
    }

    /** Últimas corridas de una tabla de log (ingesta_log, coincidencias_log, alertas_log). */
    public List<Map<String, Object>> ultimas(String tabla, int n) {
        if (!List.of("ingesta_log", "coincidencias_log", "alertas_log").contains(tabla)) {
            throw new IllegalArgumentException(tabla);
        }
        return jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY corrida_at DESC, id DESC LIMIT " + n);
    }

    private static String recortar(String s) {
        return s == null || s.length() <= 3900 ? s : s.substring(0, 3900);
    }
}
