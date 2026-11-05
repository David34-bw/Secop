package co.secopradar.modelo.dao;

import co.secopradar.modelo.repositorio.ProcesoOcultoRepositorio;
import java.util.HashSet;
import java.util.Set;

/** Acceso a la tabla procesos_ocultos (lo que cada usuario quitó de Oportunidades). */
public class ProcesoOcultoDAO implements ProcesoOcultoRepositorio {

    /** Oculta el proceso; si ya estaba oculto no hace nada. */
    @Override
    public void ocultar(int usuarioId, String procesoId) {
        BaseDatos.actualizar("INSERT INTO procesos_ocultos (usuario_id, proceso_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                usuarioId, procesoId);
    }

    @Override
    public void mostrar(int usuarioId, String procesoId) {
        BaseDatos.actualizar("DELETE FROM procesos_ocultos WHERE usuario_id = ? AND proceso_id = ?", usuarioId, procesoId);
    }

    @Override
    public Set<String> deUsuario(int usuarioId) {
        return new HashSet<>(BaseDatos.consultar("SELECT proceso_id FROM procesos_ocultos WHERE usuario_id = ?",
                rs -> rs.getString(1), usuarioId));
    }
}
