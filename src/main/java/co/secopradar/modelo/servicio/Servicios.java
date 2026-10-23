package co.secopradar.modelo.servicio;

import co.secopradar.modelo.dao.BaseDatos;
import co.secopradar.modelo.dao.CoincidenciaDAO;
import co.secopradar.modelo.dao.LogDAO;
import co.secopradar.modelo.dao.PerfilDAO;
import co.secopradar.modelo.dao.ProcesoDAO;
import co.secopradar.modelo.dao.UsuarioDAO;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Arma el modelo: lee config.properties, abre la base de datos y crea los
 * DAO y servicios una sola vez. Los controladores lo usan con
 * Servicios.get().
 */
public final class Servicios {

    private static Servicios instancia;

    public final Properties config;

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
        // -Dsecop.bd.url=... permite usar otra base (por ejemplo, una copia para pruebas).
        BaseDatos.iniciar(System.getProperty("secop.bd.url", config.getProperty("bd.url", BaseDatos.URL_POR_DEFECTO)));

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

    private static Properties leerConfig() {
        Properties p = new Properties();
        try (InputStream in = Servicios.class.getResourceAsStream("/config.properties")) {
            if (in != null) p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p;
    }
}
