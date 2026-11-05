package co.secopradar.modelo.repositorio;

import co.secopradar.modelo.Perfil;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Perfiles de búsqueda. Los servicios dependen de esta interfaz, no de cómo se
 * guardan los datos: hoy la implementa PerfilDAO (JDBC: H2 o Supabase).
 */
public interface PerfilRepositorio {

    List<Perfil> deUsuario(int userId);

    /** En orden de creación: los primeros que creó la persona son los que se conservan. */
    List<Perfil> deUsuarioPorAntiguedad(int userId);

    List<Perfil> activos();

    Optional<Perfil> porId(int id);

    int contarActivos(int userId);

    int contar();

    int crear(Perfil p);

    /** El user_id en el WHERE evita borrar perfiles ajenos. */
    int eliminar(int id, int userId);

    void cambiarEstado(int id, boolean activo, boolean desactivadoPorPlan);

    /** Ranking de profesiones elegidas (Administración). */
    List<Map<String, Object>> rankingProfesiones();
}
