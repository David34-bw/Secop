package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Plan;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.dao.UsuarioDAO;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import org.mindrot.jbcrypt.BCrypt;

import java.time.OffsetDateTime;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Registro e inicio de sesión con correo y contraseña (port de api/registro y api/login). */
public class CuentaService {

    public static final String VERSION_TERMINOS = "1.0";
    public static final String VERSION_PRIVACIDAD = "1.0";

    private static final int MAX_LARGO = 254;
    /**
     * usuario: letras, números y . _ % + - (sin punto al inicio, al final ni
     * dos seguidos), máximo 64. dominio: etiquetas de letras, números y
     * guiones (sin guion en los bordes) y una extensión de 2 a 24 letras.
     */
    private static final Pattern FORMATO = Pattern.compile(
            "^(?=.{1,64}@)[a-z0-9_%+-]+(\\.[a-z0-9_%+-]+)*@([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24}$");
    private static final Set<String> DOMINIOS_TEMPORALES = Set.of(
            "mailinator.com", "yopmail.com", "yopmail.net", "10minutemail.com", "10minutemail.net", "guerrillamail.com",
            "guerrillamail.info", "temp-mail.org", "temp-mail.io", "tempmail.com", "tempmail.net", "trashmail.com",
            "getnada.com", "dispostable.com", "sharklasers.com", "maildrop.cc", "mailnesia.com", "throwawaymail.com",
            "fakeinbox.com", "emailondeck.com", "mintemail.com", "mohmal.com", "burnermail.io", "tempr.email",
            "discard.email", "spamgourmet.com", "mailcatch.com", "tempinbox.com", "moakt.com", "emailfake.com");
    /** Dominios y extensiones reservados para ejemplos y pruebas: nunca son correos reales. */
    private static final Set<String> DOMINIOS_DE_EJEMPLO = Set.of(
            "example.com", "example.org", "example.net", "test.com", "prueba.com", "correo.com", "email.com",
            "dominio.com", "ejemplo.com", "asdf.com", "abc.com", "xxx.com", "aaa.com");
    private static final Set<String> EXTENSIONES_RESERVADAS = Set.of("test", "example", "invalid", "localhost", "local");
    private static final Map<String, String> DOMINIOS_MAL_ESCRITOS = Map.ofEntries(
            Map.entry("gmial.com", "gmail.com"), Map.entry("gmal.com", "gmail.com"), Map.entry("gmail.co", "gmail.com"),
            Map.entry("gamil.com", "gmail.com"), Map.entry("gmail.con", "gmail.com"), Map.entry("gmail.cm", "gmail.com"),
            Map.entry("gmai.com", "gmail.com"), Map.entry("gmil.com", "gmail.com"), Map.entry("gnail.com", "gmail.com"),
            Map.entry("gmail.om", "gmail.com"), Map.entry("hotmial.com", "hotmail.com"), Map.entry("hotmail.co", "hotmail.com"),
            Map.entry("hotmail.con", "hotmail.com"), Map.entry("hotmal.com", "hotmail.com"), Map.entry("hotmil.com", "hotmail.com"),
            Map.entry("outlok.com", "outlook.com"), Map.entry("outlook.con", "outlook.com"), Map.entry("outloo.com", "outlook.com"),
            Map.entry("yaho.com", "yahoo.com"), Map.entry("yahoo.con", "yahoo.com"), Map.entry("yahho.com", "yahoo.com"),
            Map.entry("live.con", "live.com"), Map.entry("icloud.con", "icloud.com"));

    public static final String SIN_MX = "Ese dominio no recibe correos, revisa que esté bien escrito";

    /** Error de validación con un mensaje para mostrar. */
    public static class ErrorCuenta extends RuntimeException {
        public ErrorCuenta(String mensaje) {
            super(mensaje);
        }
    }

    private final UsuarioDAO usuarios;

    /** Consulta DNS: true = recibe correo, false = no, null = no se sabe (no se bloquea). */
    private final Function<String, Boolean> recibeCorreo;

    public CuentaService(UsuarioDAO usuarios) {
        this(usuarios, CuentaService::dominioRecibeCorreo);
    }

    CuentaService(UsuarioDAO usuarios, Function<String, Boolean> recibeCorreo) {
        this.usuarios = usuarios;
        this.recibeCorreo = recibeCorreo;
    }

    public static String limpiarCorreo(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /** Reglas que no necesitan red. Devuelve el mensaje de error, o null si el formato es válido. */
    public static String validarFormatoCorreo(String limpio) {
        if (limpio.isEmpty()) return "Escribe tu correo";
        if (limpio.length() > MAX_LARGO || limpio.chars().filter(c -> c == '@').count() != 1
                || !FORMATO.matcher(limpio).matches()) {
            return "Escribe un correo válido, por ejemplo nombre@gmail.com";
        }
        String[] partes = limpio.split("@", 2);
        String dominio = partes[1];
        String sugerido = DOMINIOS_MAL_ESCRITOS.get(dominio);
        if (sugerido != null) return "¿Quisiste decir " + partes[0] + "@" + sugerido + "?";
        if (DOMINIOS_TEMPORALES.contains(dominio)) return "No se permiten correos temporales";
        String extension = dominio.substring(dominio.lastIndexOf('.') + 1);
        if (DOMINIOS_DE_EJEMPLO.contains(dominio) || EXTENSIONES_RESERVADAS.contains(extension)) {
            return "Usa tu correo real, no uno de ejemplo";
        }
        return null;
    }

    /** Formato y, además, que el dominio tenga servidor de correo (registros MX). */
    public String validarCorreo(String limpio) {
        String error = validarFormatoCorreo(limpio);
        if (error != null) return error;
        Boolean recibe = recibeCorreo.apply(limpio.substring(limpio.indexOf('@') + 1));
        return Boolean.FALSE.equals(recibe) ? SIN_MX : null;
    }

    /**
     * Pregunta al DNS si el dominio tiene registros MX. Si el DNS no
     * responde a tiempo o falla por su lado devuelve null ("no se sabe")
     * para no bloquear a una persona real.
     */
    static Boolean dominioRecibeCorreo(String dominio) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "1500");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        try {
            DirContext ctx = new InitialDirContext(env);
            try {
                Attribute mx = ctx.getAttributes(dominio, new String[]{"MX"}).get("MX");
                return mx != null && mx.size() > 0;
            } finally {
                ctx.close();
            }
        } catch (NameNotFoundException e) {
            return false;
        } catch (NamingException e) {
            return null;
        }
    }

    /**
     * Crea la cuenta con la prueba de 30 días del plan Profesional. La
     * primera cuenta de la base queda como administradora (versión local).
     * En esta versión no se envía código de verificación por correo.
     */
    public Usuario registrar(String emailCrudo, String password, String nombre, boolean acepto) {
        String email = limpiarCorreo(emailCrudo);
        if (email.isEmpty() || password == null || password.isEmpty()) {
            throw new ErrorCuenta("Correo y contraseña son obligatorios");
        }
        String errorNombre = ValidadorNombre.validar(nombre);
        if (errorNombre != null) throw new ErrorCuenta(errorNombre);
        String error = validarCorreo(email);
        if (error != null) throw new ErrorCuenta(error);
        ValidadorPassword.Resultado clave = ValidadorPassword.evaluar(password, email, nombre);
        if (!clave.valida()) throw new ErrorCuenta("La contraseña no es segura: " + clave.primerError().toLowerCase());
        if (!acepto) {
            throw new ErrorCuenta("Debes aceptar los Términos y condiciones y la Política de tratamiento de datos personales");
        }
        if (usuarios.porEmail(email).isPresent()) throw new ErrorCuenta("Ya existe una cuenta con ese correo");

        String rol = usuarios.contar() == 0 ? "admin" : "usuario";
        String nombreLimpio = ValidadorNombre.formatear(nombre);
        int id = usuarios.crear(email, BCrypt.hashpw(password, BCrypt.gensalt(10)), nombreLimpio, rol,
                OffsetDateTime.now().plusDays(Plan.DIAS_PRUEBA), VERSION_TERMINOS, VERSION_PRIVACIDAD);
        return usuarios.porId(id).orElseThrow();
    }

    public Optional<Usuario> autenticar(String emailCrudo, String password) {
        String email = limpiarCorreo(emailCrudo);
        Optional<String> hash = usuarios.hashDe(email);
        if (hash.isEmpty() || password == null || !BCrypt.checkpw(password, hash.get())) return Optional.empty();
        return usuarios.porEmail(email);
    }

    public void cambiarPassword(Usuario u, String actual, String nueva) {
        if (autenticar(u.email(), actual).isEmpty()) throw new ErrorCuenta("La contraseña actual no es correcta");
        ValidadorPassword.Resultado clave = ValidadorPassword.evaluar(nueva, u.email(), u.nombre());
        if (!clave.valida()) throw new ErrorCuenta("La contraseña nueva no es segura: " + clave.primerError().toLowerCase());
        if (nueva.equals(actual)) throw new ErrorCuenta("La contraseña nueva debe ser distinta de la actual");
        usuarios.cambiarPassword(u.id(), BCrypt.hashpw(nueva, BCrypt.gensalt(10)));
    }

    /** Cambia el nombre si es válido; lo guarda con mayúsculas iniciales. */
    public String cambiarNombre(Usuario u, String nombre) {
        String error = ValidadorNombre.validar(nombre);
        if (error != null) throw new ErrorCuenta(error);
        String formateado = ValidadorNombre.formatear(nombre);
        usuarios.cambiarNombre(u.id(), formateado);
        return formateado;
    }
}
