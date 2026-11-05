package co.secopradar.modelo.repositorio;

import java.util.Set;

/**
 * Procesos que el usuario ocultó de Oportunidades. Los servicios dependen de esta interfaz, no de cómo se
 * guardan los datos: hoy la implementa ProcesoOcultoDAO (JDBC: H2 o Supabase).
 */
public interface ProcesoOcultoRepositorio {

    /** Oculta el proceso; si ya estaba oculto no hace nada. */
    void ocultar(int usuarioId, String procesoId);

    void mostrar(int usuarioId, String procesoId);

    Set<String> deUsuario(int usuarioId);
}
