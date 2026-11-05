package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.api.ClienteApi;
import co.secopradar.modelo.api.CoincidenciaApi;
import co.secopradar.modelo.api.OtrosApi;
import co.secopradar.modelo.api.PerfilApi;
import co.secopradar.modelo.api.ProcesoApi;
import co.secopradar.modelo.api.UsuarioApi;
import co.secopradar.modelo.dao.BaseDatos;
import co.secopradar.modelo.dao.CoincidenciaDAO;
import co.secopradar.modelo.dao.DocumentoDAO;
import co.secopradar.modelo.dao.LogDAO;
import co.secopradar.modelo.dao.PerfilDAO;
import co.secopradar.modelo.dao.PostulacionDAO;
import co.secopradar.modelo.dao.ProcesoDAO;
import co.secopradar.modelo.dao.ProcesoOcultoDAO;
import co.secopradar.modelo.dao.ProponenteDAO;
import co.secopradar.modelo.dao.UsuarioDAO;
import co.secopradar.modelo.repositorio.CoincidenciaRepositorio;
import co.secopradar.modelo.repositorio.DocumentoRepositorio;
import co.secopradar.modelo.repositorio.LogRepositorio;
import co.secopradar.modelo.repositorio.PerfilRepositorio;
import co.secopradar.modelo.repositorio.PostulacionRepositorio;
import co.secopradar.modelo.repositorio.ProcesoOcultoRepositorio;
import co.secopradar.modelo.repositorio.ProcesoRepositorio;
import co.secopradar.modelo.repositorio.ProponenteRepositorio;
import co.secopradar.modelo.repositorio.UsuarioRepositorio;
import co.secopradar.modelo.util.Registro;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Arma el modelo: lee la configuración, elige cómo llegar a los datos y crea
 * los repositorios y servicios una sola vez. Los controladores lo usan con
 * Servicios.get().
 *
 * bd.modo en config.properties:
 *  - jdbc (por defecto): PostgreSQL de Supabase con la contraseña de
 *    config.local.properties (o la variable SECOP_BD_PASSWORD).
 *  - api: por HTTPS (puerto 443) con la Edge Function "api" de Supabase y la
 *    clave publishable (api.clave en config.local.properties o SECOP_API_CLAVE).
 *    Sirve en redes donde el firewall bloquea PostgreSQL.
 * Si no se puede conectar, se usa la base local H2 y la app lo avisa.
 */
public final class Servicios {

    private static Servicios instancia;

    public final Properties config;
    /** null si se conectó a lo configurado; si no, el motivo por el que se usa la base local. */
    public final String avisoConexion;
    /** Cliente de la API por HTTPS; null en modo JDBC (o si la API falló y se usa la base local). */
    public final ClienteApi api;

    public final UsuarioRepositorio usuarios;
    public final PerfilRepositorio perfiles;
    public final ProcesoRepositorio procesos;
    public final CoincidenciaRepositorio coincidencias;
    public final ProcesoOcultoRepositorio ocultos;
    public final LogRepositorio logs;
    public final ProponenteRepositorio proponentes;
    public final DocumentoRepositorio archivos;
    public final PostulacionRepositorio postulacionesDao;

    public final CuentaService cuentas;
    public final PlanesService planes;
    public final IngestaService ingesta;
    public final CoincidenciasService cruce;
    public final PerfilesService perfilesService;
    public final OportunidadesService oportunidades;
    public final BusquedaService busqueda;
    public final PortadaService portada;
    public final LegalService legal;
    public final DocumentosService documentos;
    public final PostulacionesService postulaciones;
    public final LimiteIntentos limiteIntentos = new LimiteIntentos();
    public final CronService actualizacion;

    /** Resultado de conectar: el cliente de la API (null si quedó en JDBC) y el aviso. */
    record Conexion(ClienteApi api, String aviso) {
    }

    Servicios(Properties config) {
        this.config = config;
        Conexion conexion = conectar(config);
        this.avisoConexion = conexion.aviso();
        this.api = conexion.api();
        int hora = entero("actualizacion.hora", 21);

        if (api != null) {
            usuarios = new UsuarioApi(api);
            perfiles = new PerfilApi(api);
            procesos = new ProcesoApi(api);
            coincidencias = new CoincidenciaApi(api);
            ocultos = new OtrosApi.ProcesoOcultoApi(api);
            logs = new OtrosApi.LogApi(api);
            proponentes = new OtrosApi.ProponenteApi(api);
            archivos = new OtrosApi.DocumentoApi(api);
            postulacionesDao = new OtrosApi.PostulacionApi(api);
        } else {
            usuarios = new UsuarioDAO();
            perfiles = new PerfilDAO();
            procesos = new ProcesoDAO();
            coincidencias = new CoincidenciaDAO();
            ocultos = new ProcesoOcultoDAO();
            logs = new LogDAO();
            proponentes = new ProponenteDAO();
            archivos = new DocumentoDAO();
            postulacionesDao = new PostulacionDAO();
        }

        ingesta = new IngestaService(procesos, logs, new ObjectMapper(), config.getProperty("socrata.app-token", ""));
        cruce = new CoincidenciasService(perfiles, procesos, coincidencias, logs);
        busqueda = new BusquedaService(procesos);
        portada = new PortadaService(procesos);
        legal = new LegalService(config.getProperty("correo-contacto", "contacto@secop-radar.local"));
        if (api != null) {
            // Lo que en JDBC son varias escrituras, aquí es una sola operación del servidor.
            cuentas = new CuentaServiceApi(usuarios, api);
            planes = new ServiciosApi.Planes(usuarios, perfiles);
            perfilesService = new ServiciosApi.Perfiles(perfiles, planes, cruce, api);
            oportunidades = new ServiciosApi.Oportunidades(coincidencias, ocultos, api);
            documentos = new ServiciosApi.Documentos(proponentes, archivos, api);
            postulaciones = new ServiciosApi.Postulaciones(postulacionesDao, procesos, documentos, api);
            actualizacion = new ServiciosApi.Actualizacion(api, hora);
        } else {
            cuentas = new CuentaService(usuarios);
            planes = new PlanesService(usuarios, perfiles);
            perfilesService = new PerfilesService(perfiles, planes, cruce);
            oportunidades = new OportunidadesService(coincidencias, ocultos);
            documentos = new DocumentosService(proponentes, archivos);
            postulaciones = new PostulacionesService(postulacionesDao, procesos, documentos);
            actualizacion = new CronService(ingesta, cruce, planes, portada, procesos, entero("ingesta.dias-atras", 5), hora);
        }
    }

    private static final Registro log = Registro.de(Servicios.class);

    /** Elige el modo según bd.modo; si falla, la base local. */
    static Conexion conectar(Properties config) {
        // -Dsecop.bd.url=... fuerza otra base JDBC (por ejemplo, una copia para pruebas).
        boolean modoApi = "api".equalsIgnoreCase(config.getProperty("bd.modo", "jdbc").trim());
        if (modoApi && System.getProperty("secop.bd.url") == null) return conectarApi(config);
        return new Conexion(null, conectarJdbc(config));
    }

    private static Conexion conectarApi(Properties config) {
        String url = config.getProperty("api.url", "").trim();
        String clave = System.getenv("SECOP_API_CLAVE");
        if (clave == null || clave.isBlank()) clave = config.getProperty("api.clave", "").trim();
        if (url.isEmpty() || clave.isEmpty()) {
            iniciarLocal(config);
            return new Conexion(null, "Falta api.url o la clave de la aplicación (api.clave en config.local.properties): "
                    + "estás usando la base local de este computador.");
        }
        ClienteApi cliente = new ClienteApi(url, clave);
        try {
            cliente.llamar("estado.datos", null);
            log.info("Conectado a la API {}", url);
            return new Conexion(cliente, null);
        } catch (ClienteApi.ErrorApi e) {
            log.warn("No se pudo conectar a la API: {}", e.getMessage());
            iniciarLocal(config);
            String motivo = e.estado == 403 ? "la clave de la aplicación (api.clave) no es correcta" : e.getMessage();
            return new Conexion(null, "No se pudo conectar al servidor por HTTPS: " + motivo
                    + " Estás usando la base local de este computador.");
        }
    }

    private static void iniciarLocal(Properties config) {
        BaseDatos.iniciar(config.getProperty("bd.local.url", BaseDatos.URL_POR_DEFECTO), "sa", "");
    }

    /** Supabase por JDBC o, si no se puede, la base local. Devuelve el aviso (o null si todo bien). */
    private static String conectarJdbc(Properties config) {
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
            iniciarLocal(config);
            return url.isEmpty() ? null
                    : "Sin contraseña de Supabase (config.local.properties): estás usando la base local de este computador.";
        }
        try {
            BaseDatos.iniciar(url, config.getProperty("bd.usuario", "postgres"), password, config.getProperty("bd.esquema", "secop"));
            log.info("Conectado a {}", BaseDatos.descripcion());
            return null;
        } catch (BaseDatos.ErrorBaseDatos e) {
            log.warn("No se pudo conectar a Supabase: {}", e.getMessage());
            iniciarLocal(config);
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
        cuentas.cerrarSesion();
        BaseDatos.cerrar();
    }

    /** Cerrar sesión desde la app: en modo API también la cierra en el servidor. */
    public void salir() {
        cuentas.cerrarSesion();
        Sesion.cerrar();
    }

    /** Los datos están en Supabase (por JDBC o por la API). */
    public boolean enLaNube() {
        return api != null || BaseDatos.esPostgres();
    }

    /** Para mostrar en la app: dónde están los datos. */
    public String descripcionDatos() {
        if (api == null) return BaseDatos.descripcion();
        return "Supabase por HTTPS (" + api.urlBase().replaceFirst("^https?://", "").replaceFirst("/.*$", "") + ")";
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
     * ahí van la contraseña de Supabase y la clave de la API).
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
