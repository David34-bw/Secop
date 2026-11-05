package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Avance;
import co.secopradar.modelo.Etapa;
import co.secopradar.modelo.Modalidades;
import co.secopradar.modelo.Postulacion;
import co.secopradar.modelo.Proceso;
import co.secopradar.modelo.repositorio.PostulacionRepositorio;
import co.secopradar.modelo.repositorio.ProcesoRepositorio;
import co.secopradar.modelo.util.Formato;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * "Mis postulaciones": seguir un proceso por las 7 etapas de la guía. Las
 * etapas van en orden: no se puede marcar una si la anterior no está
 * completa, ni desmarcar una si ya hay otra más adelante marcada.
 */
public class PostulacionesService {

    public static class ErrorPostulacion extends RuntimeException {
        public ErrorPostulacion(String mensaje) {
            super(mensaje);
        }
    }

    /** Una postulación con su avance calculado. */
    public record Detalle(Postulacion postulacion, Avance avance) {
    }

    private final PostulacionRepositorio postulaciones;
    private final ProcesoRepositorio procesos;
    private final DocumentosService documentos;

    public PostulacionesService(PostulacionRepositorio postulaciones, ProcesoRepositorio procesos, DocumentosService documentos) {
        this.postulaciones = postulaciones;
        this.procesos = procesos;
        this.documentos = documentos;
    }

    /** Empieza a seguir un proceso. Si ya lo seguía, devuelve esa postulación. */
    public int postularse(int usuarioId, String procesoId) {
        Integer existente = postulaciones.idsPorProceso(usuarioId).get(procesoId);
        if (existente != null) return existente;
        Proceso p = procesos.porIds(List.of(procesoId)).stream().findFirst()
                .orElseThrow(() -> new ErrorPostulacion("No se encontró el proceso."));
        if (Modalidades.esAvisoTemprano(p.modalidad())) {
            throw new ErrorPostulacion("Este proceso es un sondeo de mercado: aún no recibe ofertas.");
        }
        if (p.fechaCierre() != null && p.fechaCierre().isBefore(OffsetDateTime.now())) {
            throw new ErrorPostulacion("Este proceso ya cerró el " + Formato.fechaLarga(p.fechaCierre()) + ".");
        }
        return postulaciones.crear(usuarioId, p);
    }

    public Map<String, Integer> idsPorProceso(int usuarioId) {
        return postulaciones.idsPorProceso(usuarioId);
    }

    public List<Detalle> lista(int usuarioId) {
        var prep = documentos.preparacion(usuarioId);
        return postulaciones.deUsuario(usuarioId).stream().map(p -> new Detalle(p, new Avance(p, prep))).toList();
    }

    /** Abre una postulación con los datos del proceso al día. */
    public Detalle abrir(int id, int usuarioId) {
        Postulacion p = buscar(id, usuarioId);
        procesos.porIds(List.of(p.procesoId())).stream().findFirst().ifPresent(pr -> postulaciones.refrescar(id, pr));
        return detalle(id, usuarioId);
    }

    private Detalle detalle(int id, int usuarioId) {
        Postulacion p = buscar(id, usuarioId);
        return new Detalle(p, new Avance(p, documentos.preparacion(usuarioId)));
    }

    private Postulacion buscar(int id, int usuarioId) {
        return postulaciones.porId(id, usuarioId).orElseThrow(() -> new ErrorPostulacion("No se encontró la postulación."));
    }

    /** Revisa que se pueda trabajar la etapa (las anteriores completas y la postulación abierta). */
    private Detalle exigir(int id, int usuarioId, Etapa e) {
        Detalle d = detalle(id, usuarioId);
        if (d.postulacion().desistida()) throw new ErrorPostulacion("Desististe de este proceso. Reanúdalo para seguir.");
        if (!d.avance().habilitada(e)) {
            Etapa falta = d.avance().actual();
            throw new ErrorPostulacion("Primero completa la etapa " + falta.numero() + ": " + falta.titulo + ".");
        }
        return d;
    }

    /** No se puede desmarcar una etapa si ya hay otra posterior marcada por el usuario. */
    private void exigirSinPosteriores(Detalle d, Etapa e) {
        Postulacion p = d.postulacion();
        boolean[] marcadas = {false, p.revisado(), p.pliegoLeido(), false, p.valorOfertado() != null,
                p.presentadaAt() != null, p.resultado() != null};
        for (int i = e.ordinal() + 1; i < marcadas.length; i++) {
            if (marcadas[i]) {
                Etapa posterior = Etapa.values()[i];
                throw new ErrorPostulacion("Primero desmarca la etapa " + posterior.numero() + ": " + posterior.titulo + ".");
            }
        }
    }

    // ---- Etapas ----

    public Detalle marcarRegistro(int id, int usuarioId) {
        documentos.marcarRegistrado(usuarioId);
        return detalle(id, usuarioId);
    }

    public Detalle marcarRevisado(int id, int usuarioId, boolean revisado) {
        Detalle d = exigir(id, usuarioId, Etapa.REVISAR);
        if (!revisado) exigirSinPosteriores(d, Etapa.REVISAR);
        if (revisado && d.postulacion().cerrado()) {
            throw new ErrorPostulacion("Este proceso ya cerró: ya no alcanzas a presentarte.");
        }
        postulaciones.marcarRevisado(id, usuarioId, revisado);
        return detalle(id, usuarioId);
    }

    public Detalle marcarPliego(int id, int usuarioId, boolean leido, String notas) {
        Detalle d = exigir(id, usuarioId, Etapa.PLIEGO);
        if (!leido) exigirSinPosteriores(d, Etapa.PLIEGO);
        postulaciones.marcarPliego(id, usuarioId, leido, limpiar(notas, 4000));
        return detalle(id, usuarioId);
    }

    public Detalle guardarNotas(int id, int usuarioId, String notas) {
        buscar(id, usuarioId);
        postulaciones.guardarNotas(id, usuarioId, limpiar(notas, 4000));
        return detalle(id, usuarioId);
    }

    /** Requisito propio del pliego (se anota desde la etapa 3, al leerlo). */
    public Detalle agregarRequisito(int id, int usuarioId, String texto) {
        Detalle d = exigir(id, usuarioId, Etapa.PLIEGO);
        String t = limpiar(texto, 200);
        if (t == null || !t.matches(".*\\p{L}.*")) throw new ErrorPostulacion("Escribe qué pide el pliego, ej: Certificado de curso de alturas.");
        List<Postulacion.Requisito> l = new ArrayList<>(d.postulacion().requisitosExtra());
        if (l.size() >= 30) throw new ErrorPostulacion("Máximo 30 requisitos por proceso.");
        if (l.stream().anyMatch(r -> r.texto().equalsIgnoreCase(t))) throw new ErrorPostulacion("Ese requisito ya está en la lista.");
        l.add(new Postulacion.Requisito(t, false));
        postulaciones.guardarRequisitos(id, usuarioId, l);
        return detalle(id, usuarioId);
    }

    public Detalle alternarRequisito(int id, int usuarioId, int indice) {
        Detalle d = exigir(id, usuarioId, Etapa.PLIEGO);
        List<Postulacion.Requisito> l = new ArrayList<>(d.postulacion().requisitosExtra());
        Postulacion.Requisito r = l.get(indice);
        l.set(indice, new Postulacion.Requisito(r.texto(), !r.cumplido()));
        postulaciones.guardarRequisitos(id, usuarioId, l);
        return detalle(id, usuarioId);
    }

    public Detalle quitarRequisito(int id, int usuarioId, int indice) {
        Detalle d = exigir(id, usuarioId, Etapa.PLIEGO);
        List<Postulacion.Requisito> l = new ArrayList<>(d.postulacion().requisitosExtra());
        l.remove(indice);
        postulaciones.guardarRequisitos(id, usuarioId, l);
        return detalle(id, usuarioId);
    }

    /** Valor de la oferta (null = desmarcar la etapa). No puede pasar del presupuesto oficial. */
    public Detalle guardarPropuesta(int id, int usuarioId, Long valor) {
        Detalle d = exigir(id, usuarioId, Etapa.PROPUESTA);
        if (valor == null) {
            exigirSinPosteriores(d, Etapa.PROPUESTA);
        } else {
            if (valor <= 0) throw new ErrorPostulacion("Escribe el valor de tu oferta en pesos.");
            Long presupuesto = d.postulacion().valor();
            if (presupuesto != null && presupuesto > 0 && valor > presupuesto) {
                throw new ErrorPostulacion("Tu oferta supera el presupuesto oficial (" + Formato.pesos(presupuesto)
                        + "): la rechazarían. Revisa tus costos.");
            }
        }
        postulaciones.guardarPropuesta(id, usuarioId, valor);
        return detalle(id, usuarioId);
    }

    public Detalle subirArchivoPropuesta(int id, int usuarioId, Path archivo) {
        exigir(id, usuarioId, Etapa.PROPUESTA);
        byte[] datos = DocumentosService.leer(archivo);
        String nombre = archivo.getFileName().toString();
        if (nombre.length() > 200) nombre = nombre.substring(nombre.length() - 200);
        postulaciones.guardarArchivoPropuesta(id, usuarioId, nombre, datos);
        return detalle(id, usuarioId);
    }

    public Optional<byte[]> archivoPropuesta(int id, int usuarioId) {
        return postulaciones.archivoPropuesta(id, usuarioId);
    }

    public Detalle marcarPresentada(int id, int usuarioId, boolean presentada) {
        Detalle d = exigir(id, usuarioId, Etapa.PRESENTAR);
        if (presentada) {
            if (d.postulacion().cerrado()) {
                throw new ErrorPostulacion("El proceso cerró el " + Formato.fechaHora(d.postulacion().fechaCierre())
                        + ": SECOP II ya no recibe ofertas.");
            }
            postulaciones.marcarPresentada(id, usuarioId, OffsetDateTime.now());
        } else {
            exigirSinPosteriores(d, Etapa.PRESENTAR);
            postulaciones.marcarPresentada(id, usuarioId, null);
        }
        return detalle(id, usuarioId);
    }

    /** Resultado de la evaluación (null = desmarcar). Subsanar necesita un plazo. */
    public Detalle guardarResultado(int id, int usuarioId, Postulacion.Resultado r, LocalDate plazo) {
        exigir(id, usuarioId, Etapa.EVALUACION);
        if (r == Postulacion.Resultado.SUBSANAR) {
            if (plazo == null) throw new ErrorPostulacion("Indica hasta cuándo tienes para subsanar.");
            if (plazo.isBefore(LocalDate.now())) throw new ErrorPostulacion("El plazo para subsanar ya pasó.");
        } else {
            plazo = null;
        }
        postulaciones.guardarResultado(id, usuarioId, r, plazo);
        return detalle(id, usuarioId);
    }

    public Detalle desistir(int id, int usuarioId, boolean desistir) {
        buscar(id, usuarioId);
        postulaciones.marcarDesistida(id, usuarioId, desistir);
        return detalle(id, usuarioId);
    }

    public void eliminar(int id, int usuarioId) {
        postulaciones.eliminar(id, usuarioId);
    }

    private static String limpiar(String s, int maximo) {
        if (s == null) return null;
        String t = s.strip();
        if (t.isEmpty()) return null;
        return t.length() > maximo ? t.substring(0, maximo) : t;
    }
}
