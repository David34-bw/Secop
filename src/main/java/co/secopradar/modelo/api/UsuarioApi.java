package co.secopradar.modelo.api;

import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.repositorio.UsuarioRepositorio;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static co.secopradar.modelo.api.ClienteApi.cuerpo;

/** Usuarios por la API. El hash de la contraseña nunca llega a la app. */
public class UsuarioApi implements UsuarioRepositorio {

    private final ClienteApi api;

    public UsuarioApi(ClienteApi api) {
        this.api = api;
    }

    private boolean esPropio(int id) {
        return Sesion.usuarioId() != null && Sesion.usuarioId() == id;
    }

    @Override
    public Optional<Usuario> porId(int id) {
        if (!api.conSesion()) return Optional.empty();
        if (esPropio(id)) return Optional.of(api.llamar("cuenta.yo", null, null, Usuario.class));
        // Otro usuario: solo un administrador puede verlo (el servidor lo revisa).
        return todos().stream().filter(u -> u.id() == id).findFirst();
    }

    @Override
    public Optional<Usuario> porEmail(String email) {
        throw new SoloServidor("buscar usuarios por correo");
    }

    @Override
    public Optional<String> hashDe(String email) {
        throw new SoloServidor("verificar la contraseña");
    }

    @Override
    public List<Usuario> todos() {
        return api.lista("admin.usuarios", null, "usuarios", Usuario.class);
    }

    @Override
    public int contar() {
        return api.llamar("admin.resumen", null).path("usuarios").asInt();
    }

    @Override
    public int crear(String email, String hash, String nombre, String rol, OffsetDateTime pruebaHasta,
                     String versionTerminos, String versionPrivacidad) {
        throw new SoloServidor("crear cuentas (auth.registrar)");
    }

    @Override
    public void cambiarNombre(int id, String nombre) {
        api.llamar("cuenta.cambiarNombre", cuerpo("nombre", nombre));
    }

    @Override
    public void cambiarPassword(int id, String hash) {
        throw new SoloServidor("cambiar la contraseña (cuenta.cambiarPassword)");
    }

    @Override
    public void cambiarAvisos(int id, boolean activos) {
        api.llamar("cuenta.avisos", cuerpo("activos", activos));
    }

    @Override
    public void asignarPlan(int id, String plan) {
        api.llamar("admin.asignarPlan", cuerpo("usuarioId", id, "plan", plan));
    }

    @Override
    public void asignarRol(int id, String rol) {
        api.llamar("admin.asignarRol", cuerpo("usuarioId", id, "rol", rol));
    }

    @Override
    public void reiniciarPrueba(int id, OffsetDateTime hasta) {
        // El servidor da los mismos 30 días de prueba (Plan.DIAS_PRUEBA).
        api.llamar("admin.reiniciarPrueba", cuerpo("usuarioId", id));
    }

    @Override
    public int vencerGracias() {
        throw new SoloServidor("vencer gracias (job.planes)");
    }
}
