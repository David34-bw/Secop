package co.secopradar.modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * En qué va una postulación: qué etapas están completas, cuál sigue y qué
 * falta en la de documentos. Las etapas se completan en orden: una etapa
 * solo se puede marcar cuando las anteriores están completas.
 */
public final class Avance {

    public enum Estado { COMPLETA, ACTUAL, BLOQUEADA }

    private final Postulacion p;
    private final Preparacion preparacion;

    public Avance(Postulacion p, Preparacion preparacion) {
        this.p = p;
        this.preparacion = preparacion;
    }

    public Postulacion postulacion() {
        return p;
    }

    public Preparacion preparacion() {
        return preparacion;
    }

    public boolean completa(Etapa e) {
        return switch (e) {
            case REGISTRO -> preparacion.registradoSecop();
            case REVISAR -> p.revisado();
            case PLIEGO -> p.pliegoLeido();
            case DOCUMENTOS -> faltantesDocumentos().isEmpty();
            case PROPUESTA -> p.valorOfertado() != null;
            case PRESENTAR -> p.presentadaAt() != null;
            case EVALUACION -> p.resultado() == Postulacion.Resultado.GANADA || p.resultado() == Postulacion.Resultado.NO_GANADA;
        };
    }

    /** Documentos de la modalidad y requisitos propios del pliego que faltan. */
    public List<String> faltantesDocumentos() {
        List<String> faltan = new ArrayList<>(preparacion.documentosFaltantes(p.modalidad()));
        for (Postulacion.Requisito r : p.requisitosExtra()) {
            if (!r.cumplido()) faltan.add(r.texto());
        }
        return faltan;
    }

    /** Todas las anteriores completas: esta etapa se puede trabajar. */
    public boolean habilitada(Etapa e) {
        for (Etapa anterior : Etapa.values()) {
            if (anterior == e) return true;
            if (!completa(anterior)) return false;
        }
        return true;
    }

    /** La primera etapa sin completar (null si terminó todas). */
    public Etapa actual() {
        for (Etapa e : Etapa.values()) {
            if (!completa(e)) return e;
        }
        return null;
    }

    public Estado estado(Etapa e) {
        if (completa(e) && habilitada(e)) return Estado.COMPLETA;
        return e == actual() ? Estado.ACTUAL : Estado.BLOQUEADA;
    }

    /** Etapas completas seguidas desde la primera. */
    public int completas() {
        Etapa a = actual();
        return a == null ? Etapa.values().length : a.ordinal();
    }

    public double progreso() {
        return (double) completas() / Etapa.values().length;
    }

    /** "Etapa 4 de 7: Reunir los documentos", "Terminada", "Desististe"... */
    public String resumen() {
        if (p.desistida()) return "Desististe de este proceso";
        if (p.resultado() == Postulacion.Resultado.GANADA) return "¡Ganada!";
        if (p.resultado() == Postulacion.Resultado.NO_GANADA) return "No ganada";
        if (p.resultado() == Postulacion.Resultado.SUBSANAR) return "Subsanar documentos";
        Etapa a = actual();
        return a == null ? "Terminada" : "Etapa " + a.numero() + " de " + Etapa.values().length + ": " + a.titulo;
    }
}
