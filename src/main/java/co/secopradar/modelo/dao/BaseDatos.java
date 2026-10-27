package co.secopradar.modelo.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Conexión a la base de datos y ayudas de JDBC que usan todos los DAO.
 * Funciona con PostgreSQL (Supabase, en la nube) y con H2 (archivo local
 * ./data/secop.mv.db). Usa un pool de conexiones (HikariCP) para no abrir
 * una conexión nueva en cada consulta. Al iniciar crea las tablas de
 * schema.sql si no existen.
 */
public final class BaseDatos {

    public static final String URL_POR_DEFECTO =
            "jdbc:h2:file:./data/secop;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;AUTO_SERVER=TRUE";

    /** Convierte una fila del ResultSet en un objeto. */
    @FunctionalInterface
    public interface Mapeador<T> {
        T mapear(ResultSet rs) throws SQLException;
    }

    /** Llena los parámetros de una sentencia por lotes con un elemento. */
    @FunctionalInterface
    public interface Llenador<T> {
        void llenar(PreparedStatement ps, T elemento) throws SQLException;
    }

    /** Error de base de datos sin excepciones comprobadas. */
    public static class ErrorBaseDatos extends RuntimeException {
        public ErrorBaseDatos(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    private static HikariDataSource pool;
    private static String descripcion = "";

    private BaseDatos() {
    }

    /**
     * Abre el pool de conexiones y crea las tablas si faltan. Lanza
     * ErrorBaseDatos si no se puede conectar (contraseña mala, sin internet...).
     */
    public static synchronized void iniciar(String urlJdbc, String usuario, String password) {
        iniciar(urlJdbc, usuario, password, null);
    }

    /**
     * esquema: en PostgreSQL, el esquema donde viven las tablas (en Supabase,
     * "secop"). Se fija en cada conexión para que nada termine en "public".
     */
    public static synchronized void iniciar(String urlJdbc, String usuario, String password, String esquema) {
        cerrar();
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(urlJdbc);
        cfg.setUsername(usuario);
        cfg.setPassword(password);
        cfg.setPoolName("secop");
        cfg.setMaximumPoolSize(4);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(15_000);
        // Si no conecta, que falle de una vez (y la app pueda usar la base local).
        cfg.setInitializationFailTimeout(15_000);
        if (urlJdbc.startsWith("jdbc:postgresql:") && esquema != null && !esquema.isBlank()) {
            if (!esquema.matches("[a-z_][a-z0-9_]*")) throw new IllegalArgumentException("Nombre de esquema inválido: " + esquema);
            cfg.setConnectionInitSql("SET search_path TO " + esquema);
        }
        try {
            pool = new HikariDataSource(cfg);
            ejecutarScript("/schema.sql");
            descripcion = esPostgres() ? "Supabase (PostgreSQL en la nube)" : "base local H2 (" + urlJdbc.replaceAll(";.*", "") + ")";
        } catch (Exception e) {
            cerrar();
            Throwable causa = e;
            while (causa.getCause() != null && causa.getCause() != causa) causa = causa.getCause();
            throw new ErrorBaseDatos(causa.getMessage(), e);
        }
    }

    public static synchronized void cerrar() {
        if (pool != null) pool.close();
        pool = null;
    }

    public static Connection conexion() throws SQLException {
        if (pool == null) throw new SQLException("La base de datos no está iniciada");
        return pool.getConnection();
    }

    public static boolean esPostgres() {
        return pool != null && pool.getJdbcUrl().startsWith("jdbc:postgresql:");
    }

    /** "Supabase (PostgreSQL en la nube)" o "base local H2 (...)", para mostrar en la app. */
    public static String descripcion() {
        return descripcion;
    }

    private static void ejecutarScript(String ruta) throws SQLException {
        String sql;
        try (InputStream in = BaseDatos.class.getResourceAsStream(ruta)) {
            if (in == null) throw new IllegalStateException("No se encontró " + ruta);
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        StringBuilder limpio = new StringBuilder();
        for (String linea : sql.split("\\R")) {
            if (!linea.trim().startsWith("--")) limpio.append(linea).append('\n');
        }
        try (Connection c = conexion(); Statement st = c.createStatement()) {
            for (String sentencia : limpio.toString().split(";")) {
                if (!sentencia.isBlank()) st.execute(sentencia);
            }
        }
    }

    private static void parametros(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    /** "?, ?, ?" para un IN con n valores. */
    public static String marcadores(int n) {
        return String.join(", ", Collections.nCopies(Math.max(n, 1), "?"));
    }

    public static <T> List<T> consultar(String sql, Mapeador<T> mapeador, Object... params) {
        try (Connection c = conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            parametros(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> lista = new ArrayList<>();
                while (rs.next()) lista.add(mapeador.mapear(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new ErrorBaseDatos(e.getMessage(), e);
        }
    }

    /** Primera fila (vacío si no hay filas o si el valor es NULL, como max() en una tabla vacía). */
    public static <T> Optional<T> uno(String sql, Mapeador<T> mapeador, Object... params) {
        List<T> filas = consultar(sql, mapeador, params);
        return filas.isEmpty() ? Optional.empty() : Optional.ofNullable(filas.get(0));
    }

    /** Primera columna de la primera fila como número (count, max...). */
    public static long numero(String sql, Object... params) {
        return uno(sql, rs -> rs.getLong(1), params).orElse(0L);
    }

    public static int actualizar(String sql, Object... params) {
        try (Connection c = conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            parametros(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErrorBaseDatos(e.getMessage(), e);
        }
    }

    /** INSERT que devuelve el id generado. */
    public static int insertar(String sql, Object... params) {
        try (Connection c = conexion(); PreparedStatement ps = c.prepareStatement(sql, new String[]{"id"})) {
            parametros(ps, params);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("La base no devolvió el id generado");
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new ErrorBaseDatos(e.getMessage(), e);
        }
    }

    /** Ejecuta la misma sentencia para cada elemento, en lotes de 500 y en una transacción. */
    public static <T> void lote(String sql, Collection<T> elementos, Llenador<T> llenador) {
        if (elementos.isEmpty()) return;
        try (Connection c = conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            c.setAutoCommit(false);
            int n = 0;
            for (T e : elementos) {
                llenador.llenar(ps, e);
                ps.addBatch();
                if (++n % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
            c.commit();
        } catch (SQLException e) {
            throw new ErrorBaseDatos(e.getMessage(), e);
        }
    }

    /** Filas como mapas columna -> valor (columnas en minúscula). */
    public static List<Map<String, Object>> filas(String sql, Object... params) {
        return consultar(sql, rs -> {
            ResultSetMetaData md = rs.getMetaData();
            Map<String, Object> fila = new LinkedHashMap<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                Object v = rs.getObject(i);
                // PostgreSQL entrega las fechas como Timestamp; H2 como OffsetDateTime. Se unifican.
                if (v instanceof Timestamp t) v = t.toInstant().atOffset(ZoneOffset.UTC);
                fila.put(md.getColumnLabel(i).toLowerCase(), v);
            }
            return fila;
        }, params);
    }

    public static Map<String, Object> fila(String sql, Object... params) {
        List<Map<String, Object>> l = filas(sql, params);
        return l.isEmpty() ? new LinkedHashMap<>() : l.get(0);
    }
}
