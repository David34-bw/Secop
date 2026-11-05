package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.api.ClienteApi;
import co.secopradar.modelo.api.ClienteApi.ErrorApi;
import co.secopradar.modelo.repositorio.UsuarioRepositorio;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.function.Function;

import static co.secopradar.modelo.api.ClienteApi.cuerpo;

/**
 * Cuentas en modo API: la contraseña se manda por HTTPS y el servidor la
 * verifica con bcrypt (la app nunca ve el hash). Las validaciones de la app
 * (nombre, correo con DNS, fuerza de la contraseña) se mantienen para avisar
 * antes de enviar; el servidor las repite.
 */
public class CuentaServiceApi extends CuentaService {

    private final ClienteApi api;

    public CuentaServiceApi(UsuarioRepositorio usuarios, ClienteApi api) {
        super(usuarios);
        this.api = api;
    }

    CuentaServiceApi(UsuarioRepositorio usuarios, ClienteApi api, Function<String, Boolean> recibeCorreo) {
        super(usuarios, recibeCorreo);
        this.api = api;
    }

    private Usuario conSesion(JsonNode r) {
        api.usarToken(r.path("token").asText());
        return ClienteApi.convertir(r.path("usuario"), Usuario.class);
    }

    @Override
    public Usuario registrar(String emailCrudo, String password, String nombre, boolean acepto) {
        String email = validarRegistro(emailCrudo, password, nombre, acepto);
        try {
            return conSesion(api.llamar("auth.registrar",
                    cuerpo("email", email, "password", password, "nombre", nombre, "aceptaTerminos", acepto)));
        } catch (ErrorApi e) {
            throw new ErrorCuenta(e.getMessage());
        }
    }

    /** Vacío si el correo o la contraseña no son correctos; ErrorCuenta si hay otro problema (bloqueo, sin conexión). */
    @Override
    public Optional<Usuario> autenticar(String emailCrudo, String password) {
        try {
            return Optional.of(conSesion(api.llamar("auth.login", cuerpo("email", limpiarCorreo(emailCrudo), "password", password))));
        } catch (ErrorApi e) {
            if (e.estado == 400) return Optional.empty();
            throw new ErrorCuenta(e.getMessage());
        }
    }

    @Override
    public void cerrarSesion() {
        if (!api.conSesion()) return;
        try {
            api.llamar("auth.logout", null);
        } catch (ErrorApi e) {
            // Si no hay conexión, la sesión vence sola en el servidor.
        } finally {
            api.olvidarToken();
        }
    }

    @Override
    public void cambiarPassword(Usuario u, String actual, String nueva) {
        try {
            // El servidor cierra las demás sesiones y devuelve una nueva para esta ventana.
            conSesion(api.llamar("cuenta.cambiarPassword", cuerpo("actual", actual, "nueva", nueva)));
        } catch (ErrorApi e) {
            throw new ErrorCuenta(e.getMessage());
        }
    }

    @Override
    public String cambiarNombre(Usuario u, String nombre) {
        String error = ValidadorNombre.validar(nombre);
        if (error != null) throw new ErrorCuenta(error);
        try {
            return api.llamar("cuenta.cambiarNombre", cuerpo("nombre", nombre)).path("nombre").asText();
        } catch (ErrorApi e) {
            throw new ErrorCuenta(e.getMessage());
        }
    }
}
