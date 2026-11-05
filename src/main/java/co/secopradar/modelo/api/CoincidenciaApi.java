package co.secopradar.modelo.api;

import co.secopradar.modelo.Proceso;
import co.secopradar.modelo.repositorio.CoincidenciaRepositorio;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static co.secopradar.modelo.api.ClienteApi.cuerpo;

/** Coincidencias del usuario por la API. El cruce lo hace el servidor. */
public class CoincidenciaApi implements CoincidenciaRepositorio {

    private final ClienteApi api;

    public CoincidenciaApi(ClienteApi api) {
        this.api = api;
    }

    /** Respuesta de oportunidades.vigentes -> filas de Java. */
    public static List<Fila> filas(JsonNode r) {
        List<Fila> l = new ArrayList<>();
        for (JsonNode f : r.path("filas")) {
            l.add(new Fila(f.path("id").asInt(), f.path("score").asInt(), f.path("notificado").asBoolean(),
                    f.path("perfilId").asInt(), f.path("perfil").asText(null),
                    ClienteApi.convertir(f.path("proceso"), Proceso.class)));
        }
        return l;
    }

    @Override
    public Map<String, Integer> dePerfil(int perfilId) {
        throw new SoloServidor("leer coincidencias por perfil (job.cruce)");
    }

    @Override
    public void insertar(int perfilId, Map<String, Integer> scores, boolean notificado) {
        throw new SoloServidor("guardar coincidencias (job.cruce)");
    }

    @Override
    public void actualizarScores(int perfilId, Map<String, Integer> scores) {
        throw new SoloServidor("guardar coincidencias (job.cruce)");
    }

    @Override
    public int eliminar(int perfilId, Collection<String> procesoIds) {
        throw new SoloServidor("quitar coincidencias (job.cruce)");
    }

    /**
     * Todas las coincidencias vigentes del usuario, ocultas incluidas (la
     * app las separa). Con un plan limitado vienen ya recortadas por el servidor.
     */
    @Override
    public List<Fila> vigentesDeUsuario(int userId) {
        return filas(api.llamar("oportunidades.vigentes", cuerpo("incluirOcultas", true)));
    }

    @Override
    public void marcarNotificadas(int userId, Collection<Integer> ids) {
        if (!ids.isEmpty()) api.llamar("oportunidades.marcarVistas", cuerpo("ids", List.copyOf(ids)));
    }

    @Override
    public long contar() {
        return api.llamar("admin.resumen", null).path("coincidencias").asLong();
    }
}
