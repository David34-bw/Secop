package co.secopradar.modelo.dao;

import co.secopradar.modelo.repositorio.FotoRepositorio;

import java.util.Optional;

/** Acceso a la tabla fotos_perfil (una foto por usuario). */
public class FotoDAO implements FotoRepositorio {

    @Override
    public Optional<byte[]> de(int usuarioId) {
        return BaseDatos.uno("SELECT contenido FROM fotos_perfil WHERE usuario_id = ?", rs -> rs.getBytes(1), usuarioId);
    }

    /** UPDATE y, si no había foto, INSERT (H2 no tiene ON CONFLICT ... DO UPDATE). */
    @Override
    public void guardar(int usuarioId, byte[] contenido, String tipo) {
        int n = BaseDatos.actualizar("UPDATE fotos_perfil SET contenido = ?, tipo = ?, actualizado_at = CURRENT_TIMESTAMP "
                + "WHERE usuario_id = ?", contenido, tipo, usuarioId);
        if (n == 0) {
            BaseDatos.actualizar("INSERT INTO fotos_perfil (usuario_id, contenido, tipo) VALUES (?, ?, ?)",
                    usuarioId, contenido, tipo);
        }
    }

    @Override
    public void quitar(int usuarioId) {
        BaseDatos.actualizar("DELETE FROM fotos_perfil WHERE usuario_id = ?", usuarioId);
    }
}
