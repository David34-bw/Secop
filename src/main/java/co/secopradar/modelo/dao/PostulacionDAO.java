package co.secopradar.modelo.dao;

import co.secopradar.modelo.repositorio.PostulacionRepositorio;
import co.secopradar.modelo.Postulacion;
import co.secopradar.modelo.Proceso;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Acceso a la tabla postulaciones. El archivo de la propuesta solo se trae al pedirlo. */
public class PostulacionDAO implements PostulacionRepositorio {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Postulacion.Requisito>> REQUISITOS = new TypeReference<>() {
    };

    private static final String COLUMNAS = "id, user_id, proceso_id, entidad, objeto, modalidad, valor, fecha_cierre, url, "
            + "revisado, pliego_leido, notas, requisitos_extra, valor_ofertado, propuesta_nombre, presentada_at, "
            + "resultado, plazo_subsanar, desistida, created_at";

    private static final BaseDatos.Mapeador<Postulacion> MAPEADOR = rs -> new Postulacion(
            rs.getInt("id"), rs.getInt("user_id"), rs.getString("proceso_id"), rs.getString("entidad"),
            rs.getString("objeto"), rs.getString("modalidad"), Filas.largo(rs, "valor"), Filas.fecha(rs, "fecha_cierre"),
            rs.getString("url"), rs.getBoolean("revisado"), rs.getBoolean("pliego_leido"), rs.getString("notas"),
            requisitos(rs.getString("requisitos_extra")), Filas.largo(rs, "valor_ofertado"), rs.getString("propuesta_nombre"),
            Filas.fecha(rs, "presentada_at"),
            rs.getString("resultado") == null ? null : Postulacion.Resultado.valueOf(rs.getString("resultado")),
            rs.getObject("plazo_subsanar", LocalDate.class), rs.getBoolean("desistida"), Filas.fecha(rs, "created_at"));

    private static List<Postulacion.Requisito> requisitos(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, REQUISITOS);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static String json(List<Postulacion.Requisito> l) {
        try {
            return JSON.writeValueAsString(l);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<Postulacion> deUsuario(int usuarioId) {
        return BaseDatos.consultar("SELECT " + COLUMNAS + " FROM postulaciones WHERE user_id = ? "
                + "ORDER BY desistida, fecha_cierre", MAPEADOR, usuarioId);
    }

    @Override
    public Optional<Postulacion> porId(int id, int usuarioId) {
        return BaseDatos.uno("SELECT " + COLUMNAS + " FROM postulaciones WHERE id = ? AND user_id = ?", MAPEADOR, id, usuarioId);
    }

    /** proceso -> id de la postulación, para saber en qué tarjetas mostrar "Ver mi postulación". */
    @Override
    public Map<String, Integer> idsPorProceso(int usuarioId) {
        Map<String, Integer> m = new HashMap<>();
        BaseDatos.consultar("SELECT proceso_id, id FROM postulaciones WHERE user_id = ?",
                rs -> m.put(rs.getString(1), rs.getInt(2)), usuarioId);
        return m;
    }

    @Override
    public int crear(int usuarioId, Proceso p) {
        return BaseDatos.insertar("INSERT INTO postulaciones (user_id, proceso_id, entidad, objeto, modalidad, valor, "
                        + "fecha_cierre, url) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                usuarioId, p.id(), p.entidad(), p.objeto(), p.modalidad(), p.valor(), p.fechaCierre(), p.url());
    }

    /** Pone al día los datos del proceso (una adenda puede mover el cierre). */
    @Override
    public void refrescar(int id, Proceso p) {
        BaseDatos.actualizar("UPDATE postulaciones SET entidad = ?, objeto = ?, modalidad = ?, valor = ?, fecha_cierre = ?, "
                + "url = ? WHERE id = ?", p.entidad(), p.objeto(), p.modalidad(), p.valor(), p.fechaCierre(), p.url(), id);
    }

    private void cambiar(int id, int usuarioId, String asignaciones, Object... valores) {
        Object[] params = new Object[valores.length + 3];
        System.arraycopy(valores, 0, params, 0, valores.length);
        params[valores.length] = OffsetDateTime.now();
        params[valores.length + 1] = id;
        params[valores.length + 2] = usuarioId;
        BaseDatos.actualizar("UPDATE postulaciones SET " + asignaciones + ", actualizado_at = ? WHERE id = ? AND user_id = ?", params);
    }

    @Override
    public void marcarRevisado(int id, int usuarioId, boolean v) {
        cambiar(id, usuarioId, "revisado = ?", v);
    }

    @Override
    public void marcarPliego(int id, int usuarioId, boolean v, String notas) {
        cambiar(id, usuarioId, "pliego_leido = ?, notas = ?", v, notas);
    }

    @Override
    public void guardarNotas(int id, int usuarioId, String notas) {
        cambiar(id, usuarioId, "notas = ?", notas);
    }

    @Override
    public void guardarRequisitos(int id, int usuarioId, List<Postulacion.Requisito> requisitos) {
        cambiar(id, usuarioId, "requisitos_extra = ?", json(requisitos));
    }

    @Override
    public void guardarPropuesta(int id, int usuarioId, Long valor) {
        cambiar(id, usuarioId, "valor_ofertado = ?", valor);
    }

    @Override
    public void guardarArchivoPropuesta(int id, int usuarioId, String nombre, byte[] contenido) {
        cambiar(id, usuarioId, "propuesta_nombre = ?, propuesta_contenido = ?", nombre, contenido);
    }

    @Override
    public Optional<byte[]> archivoPropuesta(int id, int usuarioId) {
        return BaseDatos.uno("SELECT propuesta_contenido FROM postulaciones WHERE id = ? AND user_id = ?",
                rs -> rs.getBytes(1), id, usuarioId);
    }

    @Override
    public void marcarPresentada(int id, int usuarioId, OffsetDateTime cuando) {
        cambiar(id, usuarioId, "presentada_at = ?", cuando);
    }

    @Override
    public void guardarResultado(int id, int usuarioId, Postulacion.Resultado r, LocalDate plazo) {
        cambiar(id, usuarioId, "resultado = ?, plazo_subsanar = ?", r == null ? null : r.name(), plazo);
    }

    @Override
    public void marcarDesistida(int id, int usuarioId, boolean v) {
        cambiar(id, usuarioId, "desistida = ?", v);
    }

    @Override
    public void eliminar(int id, int usuarioId) {
        BaseDatos.actualizar("DELETE FROM postulaciones WHERE id = ? AND user_id = ?", id, usuarioId);
    }
}
