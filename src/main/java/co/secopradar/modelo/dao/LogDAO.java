package co.secopradar.modelo.dao;

import java.util.List;
import java.util.Map;

/** Registro de cada actualización (descarga y cruce), para la pantalla de Administración. */
public class LogDAO {

    public void ingesta(int leidos, int nuevos, int actualizados, boolean exito, String error) {
        BaseDatos.actualizar("INSERT INTO ingesta_log (leidos, nuevos, actualizados, exito, error) VALUES (?, ?, ?, ?, ?)",
                leidos, nuevos, actualizados, exito, recortar(error));
    }

    public void coincidencias(int perfiles, int procesos, int nuevas, int quitadas, boolean exito, String error) {
        BaseDatos.actualizar("INSERT INTO coincidencias_log (perfiles, procesos, nuevas, quitadas, exito, error) "
                + "VALUES (?, ?, ?, ?, ?, ?)", perfiles, procesos, nuevas, quitadas, exito, recortar(error));
    }

    /** Últimas corridas de una tabla de log (ingesta_log o coincidencias_log). */
    public List<Map<String, Object>> ultimas(String tabla, int n) {
        if (!List.of("ingesta_log", "coincidencias_log").contains(tabla)) throw new IllegalArgumentException(tabla);
        return BaseDatos.filas("SELECT * FROM " + tabla + " ORDER BY corrida_at DESC, id DESC LIMIT " + n);
    }

    private static String recortar(String s) {
        return s == null || s.length() <= 3900 ? s : s.substring(0, 3900);
    }
}
