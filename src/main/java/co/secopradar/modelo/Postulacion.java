package co.secopradar.modelo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Un proceso al que el usuario decidió presentarse, con lo que lleva de
 * cada etapa. Los datos del proceso (entidad, objeto, cierre...) se copian
 * al crearla y se refrescan desde procesos cuando se abre.
 */
public record Postulacion(
        int id,
        int usuarioId,
        String procesoId,
        String entidad,
        String objeto,
        String modalidad,
        Long valor,
        OffsetDateTime fechaCierre,
        String url,
        boolean revisado,
        boolean pliegoLeido,
        String notas,
        List<Requisito> requisitosExtra,
        Long valorOfertado,
        String propuestaArchivo,
        OffsetDateTime presentadaAt,
        Resultado resultado,
        LocalDate plazoSubsanar,
        boolean desistida,
        OffsetDateTime createdAt) {

    /** Algo que pide el pliego de este proceso en particular, además de los documentos generales. */
    public record Requisito(String texto, boolean cumplido) {
    }

    public enum Resultado {
        GANADA("¡Ganada! Te adjudicaron el contrato"),
        NO_GANADA("No ganada"),
        SUBSANAR("Piden subsanar documentos");

        public final String texto;

        Resultado(String texto) {
            this.texto = texto;
        }
    }

    /** Ya cerró la recepción de ofertas. */
    public boolean cerrado() {
        return fechaCierre != null && fechaCierre.isBefore(OffsetDateTime.now());
    }

    public boolean terminada() {
        return desistida || resultado == Resultado.GANADA || resultado == Resultado.NO_GANADA;
    }
}
