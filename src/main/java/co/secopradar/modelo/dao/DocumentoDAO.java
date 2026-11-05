package co.secopradar.modelo.dao;

import co.secopradar.modelo.Documento;
import co.secopradar.modelo.TipoDocumento;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla documentos. El archivo se guarda dentro de la base
 * (columna contenido) para que esté disponible desde cualquier computador;
 * las consultas de listado no lo traen.
 */
public class DocumentoDAO {

    private static final String COLUMNAS = "id, user_id, tipo, nombre_archivo, tamano, vence, subido_at";

    private static final BaseDatos.Mapeador<Documento> MAPEADOR = rs -> new Documento(
            rs.getInt("id"), rs.getInt("user_id"), TipoDocumento.valueOf(rs.getString("tipo")),
            rs.getString("nombre_archivo"), rs.getLong("tamano"), rs.getObject("vence", LocalDate.class),
            Filas.fecha(rs, "subido_at"));

    public List<Documento> deUsuario(int usuarioId) {
        return BaseDatos.consultar("SELECT " + COLUMNAS + " FROM documentos WHERE user_id = ? ORDER BY tipo, subido_at",
                MAPEADOR, usuarioId);
    }

    public Optional<Documento> porId(int id, int usuarioId) {
        return BaseDatos.uno("SELECT " + COLUMNAS + " FROM documentos WHERE id = ? AND user_id = ?", MAPEADOR, id, usuarioId);
    }

    /** Contenido del archivo, solo si es del usuario. */
    public Optional<byte[]> contenido(int id, int usuarioId) {
        return BaseDatos.uno("SELECT contenido FROM documentos WHERE id = ? AND user_id = ?",
                rs -> rs.getBytes(1), id, usuarioId);
    }

    public int crear(int usuarioId, TipoDocumento tipo, String nombreArchivo, String tipoMime, byte[] contenido, LocalDate vence) {
        return BaseDatos.insertar("INSERT INTO documentos (user_id, tipo, nombre_archivo, tipo_mime, tamano, contenido, vence) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                usuarioId, tipo.name(), nombreArchivo, tipoMime, (long) contenido.length, contenido, vence);
    }

    /** Borra los archivos de un tipo (al reemplazar un documento que es único). */
    public void eliminarTipo(int usuarioId, TipoDocumento tipo) {
        BaseDatos.actualizar("DELETE FROM documentos WHERE user_id = ? AND tipo = ?", usuarioId, tipo.name());
    }

    public boolean eliminar(int id, int usuarioId) {
        return BaseDatos.actualizar("DELETE FROM documentos WHERE id = ? AND user_id = ?", id, usuarioId) > 0;
    }

    public void cambiarVence(int id, int usuarioId, LocalDate vence) {
        BaseDatos.actualizar("UPDATE documentos SET vence = ? WHERE id = ? AND user_id = ?", vence, id, usuarioId);
    }
}
