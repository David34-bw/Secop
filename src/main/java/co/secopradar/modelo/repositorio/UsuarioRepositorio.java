package co.secopradar.modelo.repositorio;

import co.secopradar.modelo.Usuario;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Usuarios (tabla users). Los servicios dependen de esta interfaz, no de cómo se
 * guardan los datos: hoy la implementa UsuarioDAO (JDBC: H2 o Supabase).
 */
public interface UsuarioRepositorio {

    Optional<Usuario> porId(int id);

    Optional<Usuario> porEmail(String email);

    Optional<String> hashDe(String email);

    List<Usuario> todos();

    int contar();

    int crear(String email, String hash, String nombre, String rol, OffsetDateTime pruebaHasta, String versionTerminos, String versionPrivacidad);

    void cambiarNombre(int id, String nombre);

    void cambiarPassword(int id, String hash);

    void cambiarAvisos(int id, boolean activos);

    /** Cambio manual de plan desde Administración (reemplaza el cobro de Mercado Pago). */
    void asignarPlan(int id, String plan);

    void asignarRol(int id, String rol);

    void reiniciarPrueba(int id, OffsetDateTime hasta);

    /** Gracia vencida tras un cobro rechazado: el dato pasa a Gratis. */
    int vencerGracias();
}
