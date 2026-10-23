package co.secopradar.modelo.dao;

import co.secopradar.modelo.Perfil;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Acceso a la tabla perfiles. Las listas se guardan como texto JSON. */
public class PerfilDAO {

    private static final BaseDatos.Mapeador<Perfil> MAPEADOR = rs -> new Perfil(
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

    public List<Perfil> deUsuario(int userId) {
        return BaseDatos.consultar("SELECT * FROM perfiles WHERE user_id = ? ORDER BY created_at DESC, id DESC", MAPEADOR, userId);
    }

    /** En orden de creación: los primeros que creó la persona son los que se conservan. */
    public List<Perfil> deUsuarioPorAntiguedad(int userId) {
        return BaseDatos.consultar("SELECT * FROM perfiles WHERE user_id = ? ORDER BY created_at, id", MAPEADOR, userId);
    }

    public List<Perfil> activos() {
        return BaseDatos.consultar("SELECT * FROM perfiles WHERE activo ORDER BY id", MAPEADOR);
    }

    public Optional<Perfil> porId(int id) {
        return BaseDatos.uno("SELECT * FROM perfiles WHERE id = ?", MAPEADOR, id);
    }

    public int contarActivos(int userId) {
        return (int) BaseDatos.numero("SELECT count(*) FROM perfiles WHERE user_id = ? AND activo", userId);
    }

    public int contar() {
        return (int) BaseDatos.numero("SELECT count(*) FROM perfiles");
    }

    public int crear(Perfil p) {
        return BaseDatos.insertar("INSERT INTO perfiles (user_id, nombre, categorias, palabras_clave, palabras_excluir, "
                        + "departamentos, ciudades, modalidades, tipos_contrato, tipos_entidad, rangos_valor, "
                        + "dias_min_restantes, profesion_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                p.userId(), p.nombre(), Filas.json(p.categorias()), Filas.json(p.palabrasClave()),
                Filas.json(p.palabrasExcluir()), Filas.json(p.departamentos()), Filas.json(p.ciudades()),
                Filas.json(p.modalidades()), Filas.json(p.tiposContrato()), Filas.json(p.tiposEntidad()),
                Filas.json(p.rangosValor()), p.diasMinRestantes(), p.profesionId());
    }

    /** El user_id en el WHERE evita borrar perfiles ajenos. */
    public int eliminar(int id, int userId) {
        return BaseDatos.actualizar("DELETE FROM perfiles WHERE id = ? AND user_id = ?", id, userId);
    }

    public void cambiarEstado(int id, boolean activo, boolean desactivadoPorPlan) {
        BaseDatos.actualizar("UPDATE perfiles SET activo = ?, desactivado_por_plan = ? WHERE id = ?", activo, desactivadoPorPlan, id);
    }

    /** Ranking de profesiones elegidas (Administración). */
    public List<Map<String, Object>> rankingProfesiones() {
        return BaseDatos.filas("SELECT profesion_id, count(*) AS n FROM perfiles "
                + "WHERE profesion_id IS NOT NULL GROUP BY profesion_id ORDER BY n DESC");
    }
}
