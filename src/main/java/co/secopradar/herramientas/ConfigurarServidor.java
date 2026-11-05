package co.secopradar.herramientas;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Herramienta para scripts/configurar-api.ps1 (no es parte de la app).
 * Se conecta a Supabase con bd.url, bd.usuario y bd.password de
 * config.properties + config.local.properties, igual que la app en modo JDBC.
 *
 *   programar-job         guarda la URL y el secreto del job en Vault y programa
 *                         la actualización diaria con pg_cron (se puede repetir).
 *                         El secreto llega en la variable de entorno
 *                         SECOP_JOB_SECRET; nunca por argumentos ni archivos.
 *   ultima-actualizacion  muestra las últimas corridas de ingesta y cruce.
 */
public final class ConfigurarServidor {

    private static final String JOB = "secop-actualizacion-diaria";
    /** 21:00 de Colombia = 02:00 UTC. */
    private static final String HORARIO = "0 2 * * *";

    private ConfigurarServidor() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("programar-job") || args[0].equals("ultima-actualizacion"))) {
            System.err.println("Uso: ConfigurarServidor programar-job | ultima-actualizacion");
            System.exit(2);
        }
        Properties config = leerConfig();
        try (Connection c = conectar(config)) {
            if (args[0].equals("programar-job")) programarJob(c, config);
            else ultimaActualizacion(c);
        } catch (SQLException e) {
            System.err.println("Error de base de datos: " + e.getMessage());
            System.exit(1);
        }
    }

    static Properties leerConfig() throws IOException {
        Properties p = new Properties();
        try (InputStream in = ConfigurarServidor.class.getResourceAsStream("/config.properties")) {
            if (in != null) p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        Path local = Path.of("config.local.properties");
        if (Files.exists(local)) {
            try (var r = Files.newBufferedReader(local, StandardCharsets.UTF_8)) {
                p.load(r);
            }
        }
        return p;
    }

    private static Connection conectar(Properties config) throws SQLException {
        String password = System.getenv("SECOP_BD_PASSWORD");
        if (password == null || password.isBlank()) password = config.getProperty("bd.password", "");
        if (password.isBlank()) {
            System.err.println("Falta bd.password en config.local.properties.");
            System.exit(1);
        }
        return DriverManager.getConnection(config.getProperty("bd.url"), config.getProperty("bd.usuario", "postgres"), password);
    }

    /** Crea o actualiza un secreto de Vault por nombre (sin dejar el valor en ningún lado más). */
    private static void guardarSecreto(Connection c, String nombre, String valor) throws SQLException {
        String id = null;
        try (PreparedStatement ps = c.prepareStatement("SELECT id::text FROM vault.secrets WHERE name = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) id = rs.getString(1);
            }
        }
        String sql = id == null ? "SELECT vault.create_secret(?, ?)" : "SELECT vault.update_secret(?::uuid, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (id == null) {
                ps.setString(1, valor);
                ps.setString(2, nombre);
            } else {
                ps.setString(1, id);
                ps.setString(2, valor);
            }
            ps.execute();
        }
    }

    private static void programarJob(Connection c, Properties config) throws SQLException {
        String secreto = System.getenv("SECOP_JOB_SECRET");
        if (secreto == null || secreto.length() < 20) {
            System.err.println("Falta la variable SECOP_JOB_SECRET (la crea el script).");
            System.exit(1);
        }
        // https://<ref>.supabase.co a partir de api.url (.../functions/v1/api).
        String urlProyecto = config.getProperty("api.url", "").replaceFirst("/functions/v1/api/?$", "");
        if (!urlProyecto.matches("https://[a-z0-9]+\\.supabase\\.co")) {
            System.err.println("api.url no es válida: " + config.getProperty("api.url"));
            System.exit(1);
        }
        try (Statement st = c.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS pg_cron WITH SCHEMA pg_catalog");
            st.execute("CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions");
        }
        guardarSecreto(c, "secop_project_url", urlProyecto);
        guardarSecreto(c, "secop_job_secret", secreto);
        // cron.schedule con el mismo nombre reemplaza el job anterior: se puede repetir sin duplicar.
        String comando = "SELECT net.http_post("
                + "url := (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'secop_project_url') || '/functions/v1/api/job.ingesta', "
                + "headers := jsonb_build_object('Content-Type', 'application/json', "
                + "'x-secop-job', (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'secop_job_secret')), "
                + "body := '{\"offset\": 0, \"dias\": 5}'::jsonb, timeout_milliseconds := 60000)";
        try (PreparedStatement ps = c.prepareStatement("SELECT cron.schedule(?, ?, ?)")) {
            ps.setString(1, JOB);
            ps.setString(2, HORARIO);
            ps.setString(3, comando);
            ps.execute();
        }
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT schedule, active FROM cron.job WHERE jobname = '" + JOB + "'")) {
            if (!rs.next()) throw new SQLException("El job no quedó programado");
            System.out.println("Job '" + JOB + "' programado (" + rs.getString(1) + " UTC, activo: " + rs.getBoolean(2) + ")");
        }
    }

    private static void ultimaActualizacion(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT corrida_at, leidos, nuevos, actualizados, exito, error "
                    + "FROM secop.ingesta_log ORDER BY id DESC LIMIT 1")) {
                if (rs.next()) {
                    System.out.println("Ingesta " + rs.getString(1) + ": " + rs.getInt(2) + " leídos, " + rs.getInt(3) + " nuevos, "
                            + rs.getInt(4) + " actualizados" + (rs.getBoolean(5) ? "" : " — ERROR: " + rs.getString(6)));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT corrida_at, perfiles, procesos, nuevas, quitadas, exito, error "
                    + "FROM secop.coincidencias_log ORDER BY id DESC LIMIT 1")) {
                if (rs.next()) {
                    System.out.println("Cruce   " + rs.getString(1) + ": " + rs.getInt(2) + " perfiles, " + rs.getInt(3)
                            + " procesos, " + rs.getInt(4) + " nuevas" + (rs.getBoolean(6) ? "" : " — ERROR: " + rs.getString(7)));
                }
            }
        }
    }
}
