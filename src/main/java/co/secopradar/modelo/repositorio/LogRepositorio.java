package co.secopradar.modelo.repositorio;

import java.util.List;
import java.util.Map;

/**
 * Historial de la ingesta y del cruce. Los servicios dependen de esta interfaz, no de cómo se
 * guardan los datos: hoy la implementa LogDAO (JDBC: H2 o Supabase).
 */
public interface LogRepositorio {

    void ingesta(int leidos, int nuevos, int actualizados, boolean exito, String error);

    void coincidencias(int perfiles, int procesos, int nuevas, int quitadas, boolean exito, String error);

    /** Últimas corridas de una tabla de log (ingesta_log o coincidencias_log). */
    List<Map<String, Object>> ultimas(String tabla, int n);
}
