package co.secopradar.modelo.dao;

import co.secopradar.modelo.repositorio.ProponenteRepositorio;
import co.secopradar.modelo.Proponente;

import java.time.OffsetDateTime;
import java.util.Optional;

/** Acceso a la tabla proponentes (una fila por usuario, se crea al guardar por primera vez). */
public class ProponenteDAO implements ProponenteRepositorio {

    private static final String COLUMNAS = "tipo_persona, tipo_documento, numero_documento, telefono, departamento, "
            + "ciudad, profesion, titulo, universidad, matricula_profesional, anios_experiencia, resumen, "
            + "registrado_secop, tiene_rup, numero_rup, sigep_actualizada";

    private static final BaseDatos.Mapeador<Proponente> MAPEADOR = rs -> new Proponente(
            rs.getString("tipo_persona"), rs.getString("tipo_documento"), rs.getString("numero_documento"),
            rs.getString("telefono"), rs.getString("departamento"), rs.getString("ciudad"), rs.getString("profesion"),
            rs.getString("titulo"), rs.getString("universidad"), rs.getString("matricula_profesional"),
            Filas.entero(rs, "anios_experiencia"), rs.getString("resumen"), rs.getBoolean("registrado_secop"),
            rs.getBoolean("tiene_rup"), rs.getString("numero_rup"), rs.getBoolean("sigep_actualizada"));

    @Override
    public Optional<Proponente> deUsuario(int usuarioId) {
        return BaseDatos.uno("SELECT " + COLUMNAS + " FROM proponentes WHERE user_id = ?", MAPEADOR, usuarioId);
    }

    @Override
    public void guardar(int usuarioId, Proponente p) {
        Object[] valores = {p.tipoPersona(), p.tipoDocumento(), p.numeroDocumento(), p.telefono(), p.departamento(),
                p.ciudad(), p.profesion(), p.titulo(), p.universidad(), p.matriculaProfesional(), p.aniosExperiencia(),
                p.resumen(), p.registradoSecop(), p.tieneRup(), p.numeroRup(), p.sigepActualizada(), OffsetDateTime.now(),
                usuarioId};
        int n = BaseDatos.actualizar("UPDATE proponentes SET tipo_persona = ?, tipo_documento = ?, numero_documento = ?, "
                + "telefono = ?, departamento = ?, ciudad = ?, profesion = ?, titulo = ?, universidad = ?, "
                + "matricula_profesional = ?, anios_experiencia = ?, resumen = ?, registrado_secop = ?, tiene_rup = ?, "
                + "numero_rup = ?, sigep_actualizada = ?, actualizado_at = ? WHERE user_id = ?", valores);
        if (n == 0) {
            BaseDatos.actualizar("INSERT INTO proponentes (" + COLUMNAS + ", actualizado_at, user_id) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", valores);
        }
    }
}
