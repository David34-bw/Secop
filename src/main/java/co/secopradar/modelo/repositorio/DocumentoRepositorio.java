package co.secopradar.modelo.repositorio;

import co.secopradar.modelo.Documento;
import co.secopradar.modelo.TipoDocumento;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Documentos que sube el usuario. Los servicios dependen de esta interfaz, no de cómo se
 * guardan los datos: hoy la implementa DocumentoDAO (JDBC: H2 o Supabase).
 */
public interface DocumentoRepositorio {

    List<Documento> deUsuario(int usuarioId);

    Optional<Documento> porId(int id, int usuarioId);

    /** Contenido del archivo, solo si es del usuario. */
    Optional<byte[]> contenido(int id, int usuarioId);

    int crear(int usuarioId, TipoDocumento tipo, String nombreArchivo, String tipoMime, byte[] contenido, LocalDate vence);

    /** Borra los archivos de un tipo (al reemplazar un documento que es único). */
    void eliminarTipo(int usuarioId, TipoDocumento tipo);

    boolean eliminar(int id, int usuarioId);

    void cambiarVence(int id, int usuarioId, LocalDate vence);
}
