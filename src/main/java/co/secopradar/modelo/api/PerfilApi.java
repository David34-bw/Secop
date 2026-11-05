package co.secopradar.modelo.api;

import co.secopradar.modelo.Perfil;
import co.secopradar.modelo.repositorio.PerfilRepositorio;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static co.secopradar.modelo.api.ClienteApi.cuerpo;

/** Perfiles del usuario de la sesión, por la API. */
public class PerfilApi implements PerfilRepositorio {

    private final ClienteApi api;

    public PerfilApi(ClienteApi api) {
        this.api = api;
    }

    /** El servidor siempre devuelve los perfiles del dueño del token (userId solo se usa en modo JDBC). */
    @Override
    public List<Perfil> deUsuario(int userId) {
        return api.lista("perfiles.listar", null, "perfiles", Perfil.class);
    }

    @Override
    public List<Perfil> deUsuarioPorAntiguedad(int userId) {
        return deUsuario(userId).stream()
                .sorted(Comparator.comparing(Perfil::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Perfil::id))
                .toList();
    }

    @Override
    public List<Perfil> activos() {
        throw new SoloServidor("leer los perfiles de todos (job.cruce)");
    }

    @Override
    public Optional<Perfil> porId(int id) {
        return deUsuario(0).stream().filter(p -> p.id() == id).findFirst();
    }

    @Override
    public int contarActivos(int userId) {
        return (int) deUsuario(userId).stream().filter(Perfil::activo).count();
    }

    @Override
    public int contar() {
        return api.llamar("admin.resumen", null).path("perfiles").asInt();
    }

    @Override
    public int crear(Perfil p) {
        throw new SoloServidor("crear perfiles (perfiles.crear, con el cruce en la misma transacción)");
    }

    @Override
    public int eliminar(int id, int userId) {
        return api.llamar("perfiles.eliminar", cuerpo("id", id)).path("ok").asBoolean() ? 1 : 0;
    }

    @Override
    public void cambiarEstado(int id, boolean activo, boolean desactivadoPorPlan) {
        throw new SoloServidor("activar o apagar perfiles por el plan");
    }

    @Override
    public List<Map<String, Object>> rankingProfesiones() {
        return ClienteApi.JSON.convertValue(api.llamar("admin.resumen", null).path("rankingProfesiones"),
                new TypeReference<List<Map<String, Object>>>() {
                });
    }
}
