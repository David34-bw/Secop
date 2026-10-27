package co.secopradar.modelo.servicio;

import co.secopradar.modelo.dao.BaseDatos;
import co.secopradar.modelo.dao.CoincidenciaDAO;
import co.secopradar.modelo.dao.LogDAO;
import co.secopradar.modelo.dao.PerfilDAO;
import co.secopradar.modelo.dao.ProcesoDAO;
import co.secopradar.modelo.dao.UsuarioDAO;
import co.secopradar.modelo.util.Registro;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Arma el modelo: lee la configuración, abre la base de datos y crea los
 * DAO y servicios una sola vez. Los controladores lo usan con
 * Servicios.get().
 *
 * Base de datos: Supabase (PostgreSQL en la nube) si hay contraseña en
 * config.local.properties o en la variable SECOP_BD_PASSWORD; si no la hay
 * o no se puede conectar, la base local H2 (y la app lo avisa).
 */
public final class Servicios {

    private static Servicios instancia;

    public final Properties config;
    /** null si se conectó a lo configurado; si no, el motivo por el que se usa la base local. */
    public final String avisoConexion;

    public final UsuarioDAO usuarios = new UsuarioDAO();
    public final PerfilDAO perfiles = new PerfilDAO();
    public final ProcesoDAO procesos = new ProcesoDAO();
    public final CoincidenciaDAO coincidencias = new CoincidenciaDAO();
    public final LogDAO logs = new LogDAO();

    public final CuentaService cuentas;
    public final PlanesService planes;
    public final IngestaService ingesta;
    public final CoincidenciasService cruce;
    public final PerfilesService perfilesService;
    public final OportunidadesService oportunidades;
    public final BusquedaService busqueda;
    public final PortadaService portada;
    public final LegalService legal;
    public final LimiteIntentos limiteIntentos = new LimiteIntentos();
    public final CronService actualizacion;

    private Servicios(Properties config) {
        this.config = config;
        this.avisoConexion = conectar(config);

        cuentas = new CuentaService(usuarios);
        planes = new PlanesService(usuarios, perfiles);
        ingesta = new IngestaService(procesos, logs, new ObjectMapper(), config.getProperty("socrata.app-token", ""));
        cruce = new CoincidenciasService(perfiles, procesos, coincidencias, logs);
        perfilesService = new PerfilesService(perfiles, planes, cruce);
        oportunidades = new OportunidadesService(coincidencias);
        busqueda = new BusquedaService(procesos);
        portada = new PortadaService(procesos);
        legal = new LegalService(config.getProperty("correo-contacto", "contacto@secop-radar.local"));
        actualizacion = new CronService(ingesta, cruce, planes, portada, procesos,
                entero("ingesta.dias-atras", 5), entero("actualizacion.hora", 21));
    }

    private static final Registro log = Registro.de(Servicios.class);

    /** Conecta a Supabase o, si no se puede, a la base local. Devuelve el aviso (o null si todo bien). */
    private static String conectar(Properties config) {
        String local = config.getProperty("bd.local.url", BaseDatos.URL_POR_DEFECTO);
        // -Dsecop.bd.url=... fuerza otra base (por ejemplo, una copia para pruebas).
        String forzada = System.getProperty("secop.bd.url");
        if (forzada != null) {
            BaseDatos.iniciar(forzada, System.getProperty("secop.bd.usuario", "sa"),
                    System.getProperty("secop.bd.password", ""), System.getProperty("secop.bd.esquema"));
            return null;
        }
        String url = config.getProperty("bd.url", "").trim();
        String password = System.getenv("SECOP_BD_PASSWORD");
        if (password == null || password.isBlank()) password = config.getProperty("bd.password", "");
        if (url.isEmpty() || password.isBlank()) {
            BaseDatos.iniciar(local, "sa", "");
            return url.isEmpty() ? null
                    : "Sin contraseña de Supabase (config.local.properties): estás usando la base local de este computador.";
        }
        try {
            BaseDatos.iniciar(url, config.getProperty("bd.usuario", "postgres"), password, config.getProperty("bd.esquema", "secop"));
            log.info("Conectado a {}", BaseDatos.descripcion());
            return null;
        } catch (BaseDatos.ErrorBaseDatos e) {
            log.warn("No se pudo conectar a Supabase: {}", e.getMessage());
            BaseDatos.iniciar(local, "sa", "");
            String motivo = e.getMessage() != null && e.getMessage().contains("password authentication failed")
                    ? "la contraseña de config.local.properties no es correcta"
                    : "no hay conexión (" + e.getMessage() + ")";
            return "No se pudo conectar a Supabase: " + motivo + ". Estás usando la base local de este computador.";
        }
    }

    public static synchronized Servicios iniciar() {
        if (instancia == null) instancia = new Servicios(leerConfig());
        return instancia;
    }

    public static Servicios get() {
        if (instancia == null) throw new IllegalStateException("El modelo no se ha iniciado (Servicios.iniciar())");
        return instancia;
    }

    public void cerrar() {
        actualizacion.detener();
        BaseDatos.cerrar();
    }

    public int entero(String clave, int porDefecto) {
        try {
            return Integer.parseInt(config.getProperty(clave, String.valueOf(porDefecto)).trim());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    public boolean booleano(String clave, boolean porDefecto) {
        return Boolean.parseBoolean(config.getProperty(clave, String.valueOf(porDefecto)).trim());
    }

    /**
     * config.properties (dentro del proyecto, se sube a GitHub) y encima
     * config.local.properties (en la carpeta del proyecto, NO se sube:
     * ahí va la contraseña de Supabase).
     */
    private static Properties leerConfig() {
        Properties p = new Properties();
        try (InputStream in = Servicios.class.getResourceAsStream("/config.properties")) {
            if (in != null) p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        java.nio.file.Path local = java.nio.file.Path.of("config.local.properties");
        if (java.nio.file.Files.exists(local)) {
            try (var r = java.nio.file.Files.newBufferedReader(local, java.nio.charset.StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo leer config.local.properties", e);
            }
        }
        return p;
    }
}
