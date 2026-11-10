package co.secopradar.modelo.repositorio;

import java.util.Optional;

/**
 * Foto de perfil del usuario (ya recortada a 256x256). La implementan FotoDAO
 * (JDBC: H2 o Supabase) y OtrosApi.FotoApi (por HTTPS).
 */
public interface FotoRepositorio {

    Optional<byte[]> de(int usuarioId);

    /** Guarda o reemplaza la foto. tipo: image/jpeg o image/png. */
    void guardar(int usuarioId, byte[] contenido, String tipo);

    void quitar(int usuarioId);
}
