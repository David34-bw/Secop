package co.secopradar.repositorio;

import co.secopradar.dominio.Perfil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PerfilRepositorio {

    private static final RowMapper<Perfil> MAPPER = (rs, i) -> new Perfil(
            rs.getInt("id"), rs.getInt("user_id"), rs.getString("nombre"),
            Filas.lista(rs.getString("categorias")),
            Filas.lista(rs.getString("palabras_clave")),
            Filas.lista(rs.getString("palabras_excluir")),
            Filas.lista(rs.getString("departamentos")),
            Filas.lista(rs.getString("ciudades")),
            Filas.lista(rs.getString("modalidades")),
            Filas.lista(rs.getString("tipos_contrato")),
            Filas.lista(rs.getString("tipos_entidad")),
            Filas.lista(rs.getString("rangos_valor")),
            Filas.entero(rs, "dias_min_restantes"),
            rs.getString("profesion_id"),
            rs.getBoolean("activo"),
            rs.getBoolean("desactivado_por_plan"),
            Filas.fecha(rs, "created_at"));

    private final JdbcTemplate jdbc;

    public PerfilRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Perfil> deUsuario(int userId) {
        return jdbc.query("SELECT * FROM perfiles WHERE user_id = ? ORDER BY created_at DESC, id DESC", MAPPER, userId);
    }

    /** En orden de creación: los primeros que creó la persona son los que se conservan. */
    public List<Perfil> deUsuarioPorAntiguedad(int userId) {
        return jdbc.query("SELECT * FROM perfiles WHERE user_id = ? ORDER BY created_at, id", MAPPER, userId);
    }

    public List<Perfil> activos() {
        return jdbc.query("SELECT * FROM perfiles WHERE activo ORDER BY id", MAPPER);
    }

    public Optional<Perfil> porId(int id) {
        return jdbc.query("SELECT * FROM perfiles WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public int contarActivos(int userId) {
        return jdbc.queryForObject("SELECT count(*) FROM perfiles WHERE user_id = ? AND activo", Integer.class, userId);
    }

    public int contar() {
        return jdbc.queryForObject("SELECT count(*) FROM perfiles", Integer.class);
    }

    public int crear(Perfil p) {
        GeneratedKeyHolder llave = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO perfiles (user_id, nombre, categorias, palabras_clave, palabras_excluir, departamentos, "
                            + "ciudades, modalidades, tipos_contrato, tipos_entidad, rangos_valor, dias_min_restantes, profesion_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"id"});
            ps.setInt(1, p.userId());
            ps.setString(2, p.nombre());
            ps.setString(3, Filas.json(p.categorias()));
            ps.setString(4, Filas.json(p.palabrasClave()));
            ps.setString(5, Filas.json(p.palabrasExcluir()));
            ps.setString(6, Filas.json(p.departamentos()));
            ps.setString(7, Filas.json(p.ciudades()));
            ps.setString(8, Filas.json(p.modalidades()));
            ps.setString(9, Filas.json(p.tiposContrato()));
            ps.setString(10, Filas.json(p.tiposEntidad()));
            ps.setString(11, Filas.json(p.rangosValor()));
            if (p.diasMinRestantes() == null) ps.setNull(12, Types.INTEGER);
            else ps.setInt(12, p.diasMinRestantes());
            ps.setString(13, p.profesionId());
            return ps;
        }, llave);
        Map<String, Object> keys = llave.getKeys();
        return ((Number) (keys != null && keys.size() == 1 ? keys.values().iterator().next() : llave.getKey())).intValue();
    }

    /** El user_id en el WHERE evita que alguien borre perfiles ajenos. */
    public int eliminar(int id, int userId) {
        return jdbc.update("DELETE FROM perfiles WHERE id = ? AND user_id = ?", id, userId);
    }

    public void cambiarEstado(int id, boolean activo, boolean desactivadoPorPlan) {
        jdbc.update("UPDATE perfiles SET activo = ?, desactivado_por_plan = ? WHERE id = ?", activo, desactivadoPorPlan, id);
    }

    /** Ranking de profesiones elegidas (analítica de /admin). */
    public List<Map<String, Object>> rankingProfesiones() {
        return jdbc.queryForList("SELECT profesion_id, count(*) AS n FROM perfiles "
                + "WHERE profesion_id IS NOT NULL GROUP BY profesion_id ORDER BY n DESC");
    }
}
