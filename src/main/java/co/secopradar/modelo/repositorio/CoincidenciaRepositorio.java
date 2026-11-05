package co.secopradar.modelo.repositorio;

import co.secopradar.modelo.Proceso;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Coincidencias perfil-proceso. Los servicios dependen de esta interfaz, no de cómo se
 * guardan los datos: hoy la implementa CoincidenciaDAO (JDBC: H2 o Supabase).
 */
public interface CoincidenciaRepositorio {

    /** Una coincidencia con su proceso y el perfil que la encontró. */
    record Fila(int id, int score, boolean notificado, int perfilId, String perfil, Proceso proceso) {
    }

    /** Coincidencias guardadas de un perfil: proceso -> puntaje. */
    Map<String, Integer> dePerfil(int perfilId);

    void insertar(int perfilId, Map<String, Integer> scores, boolean notificado);

    void actualizarScores(int perfilId, Map<String, Integer> scores);

    int eliminar(int perfilId, Collection<String> procesoIds);

    /**
     * Coincidencias de los perfiles activos del usuario con procesos que no
     * han cerrado. Los duplicados y el límite del plan se resuelven en Java.
     */
    List<Fila> vigentesDeUsuario(int userId);

    /** Marca como vistas (ya avisadas) las coincidencias, solo si son de perfiles del usuario. */
    void marcarNotificadas(int userId, Collection<Integer> ids);

    long contar();
}
