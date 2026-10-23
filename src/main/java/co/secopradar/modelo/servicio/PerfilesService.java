package co.secopradar.modelo.servicio;

import co.secopradar.modelo.BorradorPerfil;
import co.secopradar.modelo.Catalogo;
import co.secopradar.modelo.GrupoOpciones;
import co.secopradar.modelo.Perfil;
import co.secopradar.modelo.Plan;
import co.secopradar.modelo.RangoValor;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.dao.PerfilDAO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reglas para crear, validar y borrar perfiles de búsqueda (port de api/perfiles). */
public class PerfilesService {

    private static final Logger log = Logger.getLogger(PerfilesService.class.getName());
    private static final int MAX_ITEMS = 60;
    private static final int MAX_TEXTO = 200;

    /** Error ligado a un campo del formulario, para marcarlo en la vista. */
    public record ErrorCampo(String campo, String mensaje) {
    }

    public static final class ErrorPerfil extends RuntimeException {
        public final ErrorCampo error;

        public ErrorPerfil(ErrorCampo error) {
            super(error.mensaje());
            this.error = error;
        }
    }

    public record Creado(int id, int coincidencias) {
    }

    private final PerfilDAO perfiles;
    private final PlanesService planes;
    private final CoincidenciasService cruce;
    private final Catalogo catalogo = Catalogo.get();

    public PerfilesService(PerfilDAO perfiles, PlanesService planes, CoincidenciasService cruce) {
        this.perfiles = perfiles;
        this.planes = planes;
        this.cruce = cruce;
    }

    /** Todos los errores del borrador (vacío si se puede guardar). */
    public List<ErrorCampo> validar(BorradorPerfil b) {
        List<ErrorCampo> errores = new ArrayList<>();
        // Profesión obligatoria: una del catálogo u "otra" escrita.
        if (b.profesionOtra) {
            if (b.textoProfesionOtra == null || b.textoProfesionOtra.isBlank()) {
                errores.add(new ErrorCampo("profesion", "Escribe tu profesión u oficio"));
            }
        } else if (b.profesionId == null || catalogo.profesion(b.profesionId).isEmpty()) {
            errores.add(new ErrorCampo("profesion", "Elige tu profesión, o «Otra» para escribirla"));
        }
        if (b.nombre == null || b.nombre.isBlank()) errores.add(new ErrorCampo("nombre", "Ponle un nombre al perfil"));

        // Cada grupo: al menos una opción, "todos" u "otro" (con algo escrito).
        for (GrupoOpciones g : GrupoOpciones.TODOS) {
            BorradorPerfil.Eleccion e = b.grupo(g.campo());
            if (g.esRangoValor() && e.otro) {
                if (rangoOtro(b).isEmpty()) {
                    errores.add(new ErrorCampo(g.campo(),
                            "En «Otro rango» escribe montos válidos en pesos: un mínimo, un máximo o ambos (el máximo mayor que el mínimo)"));
                }
            } else if (e.otro && e.valoresOtro().isEmpty()) {
                errores.add(new ErrorCampo(g.campo(), "Escribe tu opción en el campo de «" + g.etiquetaOtro() + "»"));
            } else if (!e.todos && !e.otro && e.marcadas.isEmpty()) {
                errores.add(new ErrorCampo(g.campo(), g.error()));
            }
        }
        return errores;
    }

    /**
     * Convierte el borrador en perfil. Sirve también para la vista previa:
     * los grupos sin elegir quedan vacíos (no filtran).
     */
    public Perfil aPerfil(BorradorPerfil b, int userId) {
        List<String> categorias = limpiar(b.categorias).stream()
                .filter(c -> Matching.CODIGO_UNSPSC_VALIDO.matcher(c).matches()).toList();
        List<String> claves = new ArrayList<>(limpiar(b.palabrasClave));
        // Sin señal temática el perfil traería de todo: la profesión escrita cuenta como palabra clave.
        if (b.profesionOtra && b.textoProfesionOtra != null && !b.textoProfesionOtra.isBlank()
                && !claves.contains(b.textoProfesionOtra.trim())) {
            claves.add(b.textoProfesionOtra.trim());
        }

        BorradorPerfil.Eleccion rangos = b.grupo(GrupoOpciones.RANGOS_VALOR);
        List<String> idsRango = new ArrayList<>();
        if (!rangos.todos) {
            rangos.marcadas.stream().filter(id -> RangoValor.porId(id).isPresent()).forEach(idsRango::add);
            if (rangos.otro) rangoOtro(b).ifPresent(r -> idsRango.add(r.id()));
        }

        String profesionId = !b.profesionOtra && b.profesionId != null && catalogo.profesion(b.profesionId).isPresent()
                ? b.profesionId : null;
        String nombre = b.nombre == null ? "" : b.nombre.trim();
        return new Perfil(null, userId, nombre.length() > MAX_TEXTO ? nombre.substring(0, MAX_TEXTO) : nombre,
                categorias, claves, limpiar(b.palabrasExcluir),
                valores(b, GrupoOpciones.DEPARTAMENTOS), limpiar(b.ciudades), valores(b, GrupoOpciones.MODALIDADES),
                valores(b, GrupoOpciones.TIPOS_CONTRATO), valores(b, GrupoOpciones.TIPOS_ENTIDAD), idsRango,
                b.diasMinRestantes != null && b.diasMinRestantes > 0 ? b.diasMinRestantes : null,
                profesionId, true, false, null);
    }

    /** Valores de un grupo; con "todos" marcado queda vacío (no filtra). */
    private static List<String> valores(BorradorPerfil b, String campo) {
        BorradorPerfil.Eleccion e = b.grupo(campo);
        if (e.todos) return List.of();
        List<String> l = new ArrayList<>(e.marcadas);
        l.addAll(e.valoresOtro());
        return limpiar(l);
    }

    private static List<String> limpiar(List<String> lista) {
        List<String> l = new ArrayList<>();
        for (String x : lista) {
            String s = x == null ? "" : x.trim();
            if (!s.isEmpty() && s.length() <= MAX_TEXTO && !l.contains(s) && l.size() < MAX_ITEMS) l.add(s);
        }
        return l;
    }

    /** Rango escrito en "Otro rango": mínimo y/o máximo en pesos ("5.000.000" vale). */
    public static Optional<RangoValor> rangoOtro(BorradorPerfil b) {
        String min = soloDigitos(b.rangoOtroMin);
        String max = soloDigitos(b.rangoOtroMax);
        if (min.isEmpty() && max.isEmpty()) return Optional.empty();
        try {
            long mn = min.isEmpty() ? 0 : Long.parseLong(min);
            Long mx = max.isEmpty() ? null : Long.parseLong(max);
            return RangoValor.personalizado(mn, mx);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String soloDigitos(String s) {
        return s == null ? "" : s.replaceAll("[.\\s$]", "");
    }

    public int vistaPrevia(BorradorPerfil b, int userId) {
        return cruce.contarVistaPrevia(aPerfil(b, userId));
    }

    /** Valida, revisa el límite del plan, guarda y genera las coincidencias del perfil nuevo. */
    public Creado crear(BorradorPerfil b, Usuario u) {
        Plan plan = Plan.efectivo(u);
        if (plan.maxPerfiles() != null && perfiles.contarActivos(u.id()) >= plan.maxPerfiles()) {
            throw new ErrorPerfil(new ErrorCampo("general", plan.mensajeLimitePerfiles()));
        }
        List<ErrorCampo> errores = validar(b);
        if (!errores.isEmpty()) throw new ErrorPerfil(errores.get(0));

        int id = perfiles.crear(aPerfil(b, u.id()));
        int n = 0;
        try {
            n = cruce.generarParaPerfilNuevo(perfiles.porId(id).orElseThrow());
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Error generando coincidencias del perfil " + id, e);
        }
        return new Creado(id, n);
    }

    public boolean puedeCrear(Usuario u) {
        Plan plan = Plan.efectivo(u);
        return plan.maxPerfiles() == null || perfiles.contarActivos(u.id()) < plan.maxPerfiles();
    }

    public void eliminar(int perfilId, Usuario u) {
        perfiles.eliminar(perfilId, u.id());
        // Borrar uno libera cupo: si el plan había apagado otros, se reactiva el siguiente.
        planes.sincronizarPerfiles(u.id(), Plan.efectivo(u));
    }
}
