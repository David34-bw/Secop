package co.secopradar.modelo.repositorio;

import co.secopradar.modelo.Proponente;

import java.util.Optional;

/**
 * Datos del usuario como proponente. Los servicios dependen de esta interfaz, no de cómo se
 * guardan los datos: hoy la implementa ProponenteDAO (JDBC: H2 o Supabase).
 */
public interface ProponenteRepositorio {

    Optional<Proponente> deUsuario(int usuarioId);

    void guardar(int usuarioId, Proponente p);
}
