package co.secopradar.web;

import co.secopradar.dominio.Catalogo;
import co.secopradar.dominio.Modalidades;
import co.secopradar.dominio.Perfil;
import co.secopradar.dominio.Plan;
import co.secopradar.dominio.RangoValor;
import co.secopradar.dominio.Usuario;
import co.secopradar.repositorio.PerfilRepositorio;
import co.secopradar.servicio.CoincidenciasService;
import co.secopradar.servicio.Matching;
import co.secopradar.servicio.PlanesService;
import co.secopradar.util.Formato;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Perfiles de búsqueda (port de app/(app)/perfiles y api/perfiles). */
@Controller
public class PerfilesController {

    private static final Logger log = LoggerFactory.getLogger(PerfilesController.class);
    private static final int MAX_ITEMS = 60;
    private static final int MAX_TEXTO = 200;

    /** Perfil con sus etiquetas listas para la plantilla. */
    public record PerfilVista(Perfil perfil, List<String> categorias, String profesion, List<String> rangos) {
    }

    public record OpcionGrupo(String valor, String label, String titulo) {
    }

    /**
     * Un grupo de opciones del formulario. Hay que elegir al menos una: una
     * de la lista, "todos" (no filtra) u "otro" (texto libre).
     */
    public record Grupo(String campo, String titulo, String ayuda, List<OpcionGrupo> opciones,
                        String etiquetaTodos, String etiquetaOtro, String placeholderOtro, String error) {
    }

    static final List<Grupo> GRUPOS = List.of(
            new Grupo("departamentos", "Departamentos", "¿Dónde puedes trabajar?",
                    Catalogo.DEPARTAMENTOS.stream().map(d -> new OpcionGrupo(d, d, null)).toList(),
                    "Todo el país", "Otro", "Escribe el departamento (separa varios con coma)",
                    "Elige al menos un departamento, «Todo el país» u «Otro»"),
            new Grupo("modalidades", "Modalidades", "Pasa el mouse sobre cada una para ver qué es.",
                    Modalidades.PARA_PERFIL.stream().map(m -> new OpcionGrupo(m, m, Modalidades.DESCRIPCION.get(m))).toList(),
                    "Todas las modalidades", "Otra", "Escribe la modalidad como aparece en SECOP",
                    "Elige al menos una modalidad, «Todas» u «Otra»"),
            new Grupo("tipos_contrato", "Tipos de contrato", null,
                    Catalogo.TIPOS_CONTRATO.stream().map(t -> new OpcionGrupo(t, t, null)).toList(),
                    "Todos los tipos", "Otro", "Escribe el tipo de contrato (separa varios con coma)",
                    "Elige al menos un tipo de contrato, «Todos» u «Otro»"),
            new Grupo("tipos_entidad", "Tipo de entidad", null,
                    Catalogo.TIPOS_ENTIDAD.stream().map(t -> new OpcionGrupo(t, t, null)).toList(),
                    "Todas las entidades", "Otra", "Escribe el tipo de entidad",
                    "Elige al menos un tipo de entidad, «Todas» u «Otra»"),
            new Grupo("rangos_valor", "Valor del proceso", null,
                    RangoValor.TODOS.stream().map(r -> new OpcionGrupo(r.id(), Formato.labelRango(r), null)).toList(),
                    "Cualquier valor", "Otro rango", null,
                    "Elige al menos un rango de valor, «Cualquier valor» u «Otro rango»"));

    private final PerfilRepositorio perfiles;
    private final PlanesService planes;
    private final CoincidenciasService coincidencias;
    private final Catalogo catalogo;

    public PerfilesController(PerfilRepositorio perfiles, PlanesService planes, CoincidenciasService coincidencias,
                              Catalogo catalogo) {
        this.perfiles = perfiles;
        this.planes = planes;
        this.coincidencias = coincidencias;
        this.catalogo = catalogo;
    }

    @GetMapping("/perfiles")
    public String pagina(HttpServletRequest req, Model model) {
        Usuario u = Sesion.usuario(req);
        Plan plan = Plan.efectivo(u);
        List<PerfilVista> vistas = perfiles.deUsuario(u.id()).stream().map(p -> new PerfilVista(p,
                p.categorias().stream().map(c -> {
                    String n = catalogo.nombreUnspsc(c);
                    return n == null ? c : n;
                }).toList(),
                p.profesionId() == null ? null : catalogo.profesion(p.profesionId()).map(Catalogo.Profesion::nombre).orElse(null),
                p.rangosValor().stream().map(id -> RangoValor.porId(id).map(Formato::labelRango).orElse(id)).toList())).toList();
        long activos = vistas.stream().filter(v -> v.perfil().activo()).count();

        model.addAttribute("perfiles", vistas);
        model.addAttribute("plan", plan);
        model.addAttribute("puedeCrear", plan.maxPerfiles() == null || activos < plan.maxPerfiles());
        model.addAttribute("mensajeLimite", plan.maxPerfiles() == null ? "" : plan.mensajeLimitePerfiles());
        model.addAttribute("grupoDepartamentos", GRUPOS.get(0));
        model.addAttribute("gruposAjustes", GRUPOS.subList(1, GRUPOS.size()));
        return "perfiles";
    }

    @GetMapping("/api/catalogo")
    @ResponseBody
    public Map<String, Object> catalogo() {
        return catalogo.paraFormulario();
    }

    @PostMapping("/api/perfiles")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Usuario u = Sesion.usuario(req);
        // Límite de perfiles activos del plan, verificado en el servidor.
        Plan plan = Plan.efectivo(u);
        if (plan.maxPerfiles() != null && perfiles.contarActivos(u.id()) >= plan.maxPerfiles()) {
            return ResponseEntity.status(403).body(Map.of("error", plan.mensajeLimitePerfiles(), "limite", "perfiles"));
        }
        String nombre = texto(body.get("nombre"));
        if (nombre.isEmpty()) return error("nombre", "Ponle un nombre al perfil");

        // Profesión obligatoria: una del catálogo u "otra" escrita.
        String profesionOtra = texto(body.get("profesion_otra"));
        if (catalogo.profesion(texto(body.get("profesion_id"))).isEmpty() && profesionOtra.isEmpty()) {
            return error("profesion", "Elige tu profesión o escribe la tuya en «Otra»");
        }

        Perfil nuevo = leerPerfil(body, u.id(), nombre);

        boolean pidioRangoOtro = !texto(body.get("rango_otro_min")).isEmpty() || !texto(body.get("rango_otro_max")).isEmpty();
        if (pidioRangoOtro && rangoOtro(body).isEmpty()) {
            return error("rangos_valor", "En «Otro rango» escribe montos válidos en pesos (el máximo debe ser mayor que el mínimo)");
        }
        // Cada grupo: al menos una opción, "todos" u "otro".
        for (Grupo g : GRUPOS) {
            boolean todos = Boolean.TRUE.equals(body.get(g.campo() + "_todos"));
            if (!todos && valoresDe(nuevo, g.campo()).isEmpty()) return error(g.campo(), g.error());
        }
        // Sin señal temática el perfil traería de todo: la profesión escrita cuenta como palabra clave.
        if (!profesionOtra.isEmpty() && !nuevo.palabrasClave().contains(profesionOtra)) {
            List<String> claves = new ArrayList<>(nuevo.palabrasClave());
            claves.add(profesionOtra);
            nuevo = new Perfil(null, nuevo.userId(), nuevo.nombre(), nuevo.categorias(), claves, nuevo.palabrasExcluir(),
                    nuevo.departamentos(), nuevo.ciudades(), nuevo.modalidades(), nuevo.tiposContrato(), nuevo.tiposEntidad(),
                    nuevo.rangosValor(), nuevo.diasMinRestantes(), nuevo.profesionId(), true, false, null);
        }
        int id = perfiles.crear(nuevo);
        Perfil guardado = perfiles.porId(id).orElseThrow();

        int n = 0;
        try {
            n = coincidencias.generarParaPerfilNuevo(guardado);
        } catch (RuntimeException e) {
            log.error("[perfiles] Error generando coincidencias", e);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("coincidencias", n);
        return ResponseEntity.ok(r);
    }

    @PostMapping("/api/perfiles/preview")
    @ResponseBody
    public Map<String, Object> vistaPrevia(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Perfil borrador = leerPerfil(body, Sesion.usuario(req).id(), "borrador");
        return Map.of("count", coincidencias.contarVistaPrevia(borrador));
    }

    @PostMapping("/perfiles/{id}/eliminar")
    public String eliminar(@PathVariable int id, HttpServletRequest req) {
        Usuario u = Sesion.usuario(req);
        perfiles.eliminar(id, u.id());
        // Borrar uno libera cupo: si el plan había apagado otros, se reactiva el siguiente.
        planes.sincronizarPerfiles(u.id(), Plan.efectivo(u));
        return "redirect:/perfiles";
    }

    private static ResponseEntity<Map<String, Object>> error(String campo, String mensaje) {
        return ResponseEntity.badRequest().body(Map.of("error", mensaje, "campo", campo));
    }

    private static List<String> valoresDe(Perfil p, String campo) {
        return switch (campo) {
            case "departamentos" -> p.departamentos();
            case "modalidades" -> p.modalidades();
            case "tipos_contrato" -> p.tiposContrato();
            case "tipos_entidad" -> p.tiposEntidad();
            case "rangos_valor" -> p.rangosValor();
            default -> List.of();
        };
    }

    private Perfil leerPerfil(Map<String, Object> body, int userId, String nombre) {
        List<String> categorias = lista(body.get("categorias")).stream()
                .filter(c -> Matching.CODIGO_UNSPSC_VALIDO.matcher(c).matches()).toList();
        List<String> rangos = new ArrayList<>(lista(body.get("rangos_valor")).stream()
                .filter(id -> RangoValor.porId(id).isPresent() && !RangoValor.esPersonalizado(id)).toList());
        rangoOtro(body).ifPresent(r -> rangos.add(r.id()));
        // Solo se guarda profesion_id si existe en el catálogo.
        String profesion = texto(body.get("profesion_id"));
        String profesionId = catalogo.profesion(profesion).isPresent() ? profesion : null;
        return new Perfil(null, userId, nombre.length() > MAX_TEXTO ? nombre.substring(0, MAX_TEXTO) : nombre,
                categorias, lista(body.get("palabras_clave")), lista(body.get("palabras_excluir")),
                grupo(body, "departamentos"), lista(body.get("ciudades")), grupo(body, "modalidades"),
                grupo(body, "tipos_contrato"), grupo(body, "tipos_entidad"),
                Boolean.TRUE.equals(body.get("rangos_valor_todos")) ? List.of() : rangos,
                numeroONulo(body.get("dias_min_restantes")), profesionId, true, false, null);
    }

    /** Valores de un grupo; con "todos" marcado queda vacío (no filtra). */
    private static List<String> grupo(Map<String, Object> body, String campo) {
        return Boolean.TRUE.equals(body.get(campo + "_todos")) ? List.of() : lista(body.get(campo));
    }

    /** Rango escrito en "Otro rango": mínimo y/o máximo en pesos. */
    private static java.util.Optional<RangoValor> rangoOtro(Map<String, Object> body) {
        String min = texto(body.get("rango_otro_min")).replaceAll("[.\\s$]", "");
        String max = texto(body.get("rango_otro_max")).replaceAll("[.\\s$]", "");
        if (min.isEmpty() && max.isEmpty()) return java.util.Optional.empty();
        try {
            long mn = min.isEmpty() ? 0 : Long.parseLong(min);
            Long mx = max.isEmpty() ? null : Long.parseLong(max);
            return RangoValor.personalizado(mn, mx);
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    private static String texto(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static List<String> lista(Object o) {
        List<String> l = new ArrayList<>();
        if (o instanceof List<?> lista) {
            for (Object x : lista) {
                String s = texto(x);
                if (!s.isEmpty() && s.length() <= MAX_TEXTO && !l.contains(s) && l.size() < MAX_ITEMS) l.add(s);
            }
        }
        return l;
    }

    private static Integer numeroONulo(Object o) {
        try {
            double n = Double.parseDouble(texto(o));
            return Double.isFinite(n) && n > 0 ? (int) Math.round(n) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
