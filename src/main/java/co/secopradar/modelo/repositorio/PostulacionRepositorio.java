package co.secopradar.modelo.repositorio;

import co.secopradar.modelo.Postulacion;
import co.secopradar.modelo.Proceso;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Postulaciones a procesos y su avance. Los servicios dependen de esta interfaz, no de cómo se
 * guardan los datos: hoy la implementa PostulacionDAO (JDBC: H2 o Supabase).
 */
public interface PostulacionRepositorio {

    List<Postulacion> deUsuario(int usuarioId);

    Optional<Postulacion> porId(int id, int usuarioId);

    /** proceso -> id de la postulación, para saber en qué tarjetas mostrar "Ver mi postulación". */
    Map<String, Integer> idsPorProceso(int usuarioId);

    int crear(int usuarioId, Proceso p);

    /** Pone al día los datos del proceso (una adenda puede mover el cierre). */
    void refrescar(int id, Proceso p);

    void marcarRevisado(int id, int usuarioId, boolean v);

    void marcarPliego(int id, int usuarioId, boolean v, String notas);

    void guardarNotas(int id, int usuarioId, String notas);

    void guardarRequisitos(int id, int usuarioId, List<Postulacion.Requisito> requisitos);

    void guardarPropuesta(int id, int usuarioId, Long valor);

    void guardarArchivoPropuesta(int id, int usuarioId, String nombre, byte[] contenido);

    Optional<byte[]> archivoPropuesta(int id, int usuarioId);

    void marcarPresentada(int id, int usuarioId, OffsetDateTime cuando);

    void guardarResultado(int id, int usuarioId, Postulacion.Resultado r, LocalDate plazo);

    void marcarDesistida(int id, int usuarioId, boolean v);

    void eliminar(int id, int usuarioId);
}
