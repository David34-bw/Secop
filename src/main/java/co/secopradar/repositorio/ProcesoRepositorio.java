package co.secopradar.repositorio;

import co.secopradar.dominio.Proceso;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class ProcesoRepositorio {

    static final String COLUMNAS = "id_del_proceso, entidad, nombre, objeto, modalidad, categoria, tipo_contrato, valor, "
            + "departamento, ciudad, estado_apertura, fecha_publicacion, fecha_cierre, url, ordenentidad";

    static final RowMapper<Proceso> MAPPER = (rs, i) -> new Proceso(
            rs.getString("id_del_proceso"), rs.getString("entidad"), rs.getString("nombre"), rs.getString("objeto"),
            rs.getString("modalidad"), rs.getString("categoria"), rs.getString("tipo_contrato"),
            Filas.largo(rs, "valor"), rs.getString("departamento"), rs.getString("ciudad"),
            rs.getString("estado_apertura"), Filas.fecha(rs, "fecha_publicacion"), Filas.fecha(rs, "fecha_cierre"),
            rs.getString("url"), rs.getString("ordenentidad"));

    /** Fila lista para guardar: el proceso más los campos extra de la fuente. */
    public record Fila(Proceso proceso, String estadoProcedimiento, Integer respuestas, String raw) {
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public ProcesoRepositorio(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    /** Procesos vigentes: sin fecha de cierre o que cerraron hace menos de 2 días. */
    public List<Proceso> vigentes() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM procesos WHERE fecha_cierre IS NULL OR fecha_cierre >= ?",
                MAPPER, OffsetDateTime.now().minusDays(2));
    }

    public Set<String> existentes(Collection<String> ids) {
        if (ids.isEmpty()) return Set.of();
        return new HashSet<>(named.queryForList("SELECT id_del_proceso FROM procesos WHERE id_del_proceso IN (:ids)",
                new MapSqlParameterSource("ids", ids), String.class));
    }

    public void insertar(List<Fila> filas) {
        jdbc.batchUpdate("INSERT INTO procesos (" + COLUMNAS + ", estado_procedimiento, respuestas, raw, ingresado_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                filas, 200, (ps, f) -> {
                    Proceso p = f.proceso();
                    ps.setString(1, p.id());
                    ps.setString(2, p.entidad());
                    ps.setString(3, p.nombre());
                    ps.setString(4, p.objeto());
                    ps.setString(5, p.modalidad());
                    ps.setString(6, p.categoria());
                    ps.setString(7, p.tipoContrato());
                    if (p.valor() == null) ps.setNull(8, Types.BIGINT);
                    else ps.setLong(8, p.valor());
                    ps.setString(9, p.departamento());
                    ps.setString(10, p.ciudad());
                    ps.setString(11, p.estadoApertura());
                    ps.setObject(12, p.fechaPublicacion());
                    ps.setObject(13, p.fechaCierre());
                    ps.setString(14, p.url());
                    ps.setString(15, p.ordenEntidad());
                    ps.setString(16, f.estadoProcedimiento());
                    if (f.respuestas() == null) ps.setNull(17, Types.INTEGER);
                    else ps.setInt(17, f.respuestas());
                    ps.setString(18, f.raw());
                    ps.setObject(19, OffsetDateTime.now());
                });
    }

    /** Lo que cambia en la fuente: estado, cierre, valor, entidad y nombre (objeto se conserva). */
    public void actualizar(List<Fila> filas) {
        jdbc.batchUpdate("UPDATE procesos SET estado_apertura = ?, fecha_cierre = ?, valor = ?, ordenentidad = ?, "
                        + "nombre = ?, estado_procedimiento = ?, respuestas = ?, raw = ? WHERE id_del_proceso = ?",
                filas, 200, (ps, f) -> {
                    Proceso p = f.proceso();
                    ps.setString(1, p.estadoApertura());
                    ps.setObject(2, p.fechaCierre());
                    if (p.valor() == null) ps.setNull(3, Types.BIGINT);
                    else ps.setLong(3, p.valor());
                    ps.setString(4, p.ordenEntidad());
                    ps.setString(5, p.nombre());
                    ps.setString(6, f.estadoProcedimiento());
                    if (f.respuestas() == null) ps.setNull(7, Types.INTEGER);
                    else ps.setInt(7, f.respuestas());
                    ps.setString(8, f.raw());
                    ps.setString(9, p.id());
                });
    }

    public long contar() {
        return jdbc.queryForObject("SELECT count(*) FROM procesos", Long.class);
    }

    public OffsetDateTime ultimoIngreso() {
        return jdbc.queryForObject("SELECT max(ingresado_at) FROM procesos", OffsetDateTime.class);
    }

    // ---- Datos de la portada ----

    /** Procesos abiertos (estado Abierto y cierre que no ha pasado) por departamento y tipo de contrato. */
    public List<Map<String, Object>> abiertosPorDepartamentoYTipo(OffsetDateTime ahora) {
        return jdbc.queryForList("SELECT departamento, tipo_contrato AS tipo, count(*) AS n FROM procesos "
                + "WHERE estado_apertura = 'Abierto' AND fecha_cierre >= ? GROUP BY departamento, tipo_contrato", ahora);
    }

    public Map<String, Object> resumenAbiertos(OffsetDateTime ahora, OffsetDateTime inicioHoy) {
        return jdbc.queryForMap("SELECT count(*) AS abiertos, "
                        + "sum(CASE WHEN ingresado_at >= ? THEN 1 ELSE 0 END) AS nuevos_hoy, "
                        + "sum(CASE WHEN fecha_cierre < ? THEN 1 ELSE 0 END) AS cierran_semana "
                        + "FROM procesos WHERE estado_apertura = 'Abierto' AND fecha_cierre >= ?",
                inicioHoy, ahora.plusDays(7), ahora);
    }

    /**
     * Ofertas recibidas en mínima cuantía, en procesos ya cerrados y en
     * evaluación o adjudicados (donde SECOP registra bien las respuestas).
     */
    public Map<String, Object> ofertasMinimaCuantia(OffsetDateTime ahora) {
        return jdbc.queryForMap("SELECT count(*) AS cerrados, "
                        + "sum(CASE WHEN respuestas <= 1 THEN 1 ELSE 0 END) AS una_o_ninguna, "
                        + "min(fecha_publicacion) AS desde FROM procesos "
                        + "WHERE modalidad = 'Mínima cuantía' AND fecha_cierre < ? "
                        + "AND estado_procedimiento IN ('Evaluación', 'Seleccionado', 'Adjudicado') AND respuestas IS NOT NULL",
                ahora.minusDays(3));
    }

    public List<Proceso> porIds(Collection<String> ids) {
        if (ids.isEmpty()) return new ArrayList<>();
        return named.query("SELECT " + COLUMNAS + " FROM procesos WHERE id_del_proceso IN (:ids)",
                new MapSqlParameterSource("ids", ids), MAPPER);
    }
}
