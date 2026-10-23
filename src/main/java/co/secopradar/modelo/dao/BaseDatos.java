package co.secopradar.modelo.dao;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Conexión a la base H2 local (./data/secop.mv.db) y ayudas de JDBC que
 * usan todos los DAO. Al iniciar crea las tablas de schema.sql si no existen.
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

    private static String url = URL_POR_DEFECTO;
    // Mantiene la base abierta mientras corre la app: abrir otra conexión es casi gratis.
    private static Connection guardiana;

    private BaseDatos() {
    }

    public static synchronized void iniciar(String urlJdbc) {
        url = urlJdbc;
        try {
            guardiana = DriverManager.getConnection(url, "sa", "");
            ejecutarScript("/schema.sql");
        } catch (SQLException e) {
            throw new ErrorBaseDatos("No se pudo abrir la base de datos (" + url + "). "
                    + "¿La aplicación ya está abierta en otra ventana?", e);
        }
    }

    public static synchronized void cerrar() {
        try {
            if (guardiana != null) guardiana.close();
        } catch (SQLException ignorada) {
            // Se está cerrando la app.
        }
    }

    public static Connection conexion() throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
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

    public static <T> Optional<T> uno(String sql, Mapeador<T> mapeador, Object... params) {
        return consultar(sql, mapeador, params).stream().findFirst();
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
            for (int i = 1; i <= md.getColumnCount(); i++) fila.put(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));
            return fila;
        }, params);
    }

    public static Map<String, Object> fila(String sql, Object... params) {
        List<Map<String, Object>> l = filas(sql, params);
        return l.isEmpty() ? new LinkedHashMap<>() : l.get(0);
    }
}
