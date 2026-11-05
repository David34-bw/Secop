package co.secopradar.modelo;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Qué tan listo está el usuario para presentarse según la modalidad del
 * proceso, a partir de los documentos que subió (vigentes) y de si ya está
 * registrado como proveedor en SECOP II. Sigue la guía "Qué documentos pide
 * SECOP II para presentarse a un proceso".
 */
public final class Preparacion {

    /** Lo que se pide en todas las modalidades. */
    private static final List<TipoDocumento> BASICOS =
            List.of(TipoDocumento.CEDULA, TipoDocumento.INHABILIDADES, TipoDocumento.SEGURIDAD_SOCIAL);

    public static final String REGISTRO_SECOP = "Cuenta de proveedor en SECOP II";

    public enum Grupo {
        MINIMA_CUANTIA("Mínima cuantía", "Contratos pequeños. Experiencia solo si la piden; gana el menor precio que cumpla.",
                false, List.of()),
        SELECCION_ABREVIADA("Selección abreviada / subasta inversa",
                "Experiencia e indicadores financieros. A veces garantía de seriedad.",
                true, List.of(TipoDocumento.RUP, TipoDocumento.EXPERIENCIA)),
        LICITACION("Licitación pública", "Contratos grandes: experiencia, indicadores financieros, equipo de trabajo y garantías.",
                true, List.of(TipoDocumento.RUP, TipoDocumento.EXPERIENCIA)),
        CONCURSO_MERITOS("Concurso de méritos", "Consultorías. Cuenta la experiencia y formación; el precio no da puntaje.",
                true, List.of(TipoDocumento.RUP, TipoDocumento.EXPERIENCIA, TipoDocumento.TITULO)),
        CONTRATACION_DIRECTA("Prestación de servicios (contratación directa)",
                "No hay concurso: la entidad escoge al contratista. Hoja de vida en SIGEP II, títulos y experiencia.",
                false, List.of(TipoDocumento.HOJA_VIDA_SIGEP, TipoDocumento.TITULO, TipoDocumento.EXPERIENCIA));

        public final String nombre;
        public final String descripcion;
        public final boolean pideRup;
        private final List<TipoDocumento> adicionales;

        Grupo(String nombre, String descripcion, boolean pideRup, List<TipoDocumento> adicionales) {
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.pideRup = pideRup;
            this.adicionales = adicionales;
        }

        public List<TipoDocumento> requeridos() {
            List<TipoDocumento> l = new ArrayList<>(BASICOS);
            l.addAll(adicionales);
            return l;
        }

        /** Grupo de una modalidad tal como viene en SECOP ("Selección abreviada subasta inversa"...). */
        public static Optional<Grupo> de(String modalidad) {
            if (modalidad == null) return Optional.empty();
            String m = Normalizer.normalize(modalidad, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
            if (m.contains("minima cuantia")) return Optional.of(MINIMA_CUANTIA);
            if (m.contains("seleccion abreviada")) return Optional.of(SELECCION_ABREVIADA);
            if (m.contains("licitacion")) return Optional.of(LICITACION);
            if (m.contains("concurso de meritos")) return Optional.of(CONCURSO_MERITOS);
            if (m.contains("directa") || m.contains("prestacion de servicios")) return Optional.of(CONTRATACION_DIRECTA);
            return Optional.empty();
        }
    }

    /** Resultado para un grupo: lo que falta (vacío = listo). */
    public record Estado(Grupo grupo, List<String> faltantes) {
        public boolean listo() {
            return faltantes.isEmpty();
        }
    }

    private final Set<TipoDocumento> vigentes;
    private final boolean registradoSecop;

    public Preparacion(List<Documento> documentos, Proponente proponente) {
        Set<TipoDocumento> s = EnumSet.noneOf(TipoDocumento.class);
        for (Documento d : documentos) {
            if (d.vigencia() != Documento.Vigencia.VENCIDO) s.add(d.tipo());
        }
        this.vigentes = s;
        this.registradoSecop = proponente != null && proponente.registradoSecop();
    }

    public boolean tiene(TipoDocumento t) {
        return vigentes.contains(t);
    }

    public Estado estado(Grupo g) {
        List<String> faltan = new ArrayList<>();
        if (!registradoSecop) faltan.add(REGISTRO_SECOP);
        for (TipoDocumento t : g.requeridos()) {
            if (!vigentes.contains(t)) faltan.add(t.nombre);
        }
        return new Estado(g, faltan);
    }

    public List<Estado> estados() {
        return java.util.Arrays.stream(Grupo.values()).map(this::estado).toList();
    }

    /** Estado para la modalidad de un proceso; vacío si la modalidad no está en la guía. */
    public Optional<Estado> paraModalidad(String modalidad) {
        return Grupo.de(modalidad).map(this::estado);
    }

    /** Cuántos de los documentos básicos (los que piden casi siempre) están al día. */
    public int basicosListos() {
        return (int) TipoDocumento.de(TipoDocumento.Grupo.BASICO).stream().filter(vigentes::contains).count();
    }

    public static int totalBasicos() {
        return TipoDocumento.de(TipoDocumento.Grupo.BASICO).size();
    }
}
