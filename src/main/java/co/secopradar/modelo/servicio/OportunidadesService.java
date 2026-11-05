package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Oportunidad;
import co.secopradar.modelo.Plan;
import co.secopradar.modelo.Proceso;
import co.secopradar.modelo.RangoValor;
import co.secopradar.modelo.Region;
import co.secopradar.modelo.repositorio.CoincidenciaRepositorio;
import co.secopradar.modelo.repositorio.CoincidenciaRepositorio.Fila;
import co.secopradar.modelo.repositorio.ProcesoOcultoRepositorio;
import co.secopradar.modelo.util.Formato;
import co.secopradar.modelo.util.Texto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Lo que ve cada usuario en su panel y en sus alertas (port de
 * dashboard/page.js, lib/limites.js y lib/alertas.js). La consulta trae
 * las coincidencias vigentes y el resto (duplicados, límite del plan,
 * filtros y orden) se hace en Java, igual para H2 y PostgreSQL.
 *
 * El usuario puede ocultar procesos (clic derecho en la tarjeta): no salen
 * en el panel ni en los avisos, salvo con el filtro "Mostrar ocultas".
 */
public class OportunidadesService {

    private static final Comparator<OffsetDateTime> FECHA_NULOS_AL_FINAL = Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<Fila> POR_RELEVANCIA = Comparator
            .comparingInt((Fila f) -> -f.score())
            .thenComparing(f -> f.proceso().fechaCierre(), FECHA_NULOS_AL_FINAL)
            .thenComparingInt(Fila::id);

    /** Mismo proceso publicado dos veces en SECOP o encontrado por dos perfiles. */
    record Clave(String entidad, String objeto, Long valor, OffsetDateTime cierre) {
        static Clave de(Proceso p) {
            return new Clave(p.entidad(), p.objeto(), p.valor(),
                    p.fechaCierre() == null ? null : p.fechaCierre().withOffsetSameInstant(java.time.ZoneOffset.UTC));
        }
    }

    public record Panel(
            List<Oportunidad> oportunidades,
            int total,
            int cierranSemana,
            long valorTotal,
            int totalFiltrado,
            int ocultasPorPlan,
            int ocultasPorUsuario,
            Set<String> procesosOcultos,
            List<String> departamentos,
            List<String> modalidades,
            List<String> entidades) {

        public boolean hayMas() {
            return totalFiltrado > oportunidades.size();
        }

        public String valorTotalTexto() {
            return Formato.pesos(valorTotal);
        }
    }

    private final CoincidenciaRepositorio coincidencias;
    private final ProcesoOcultoRepositorio ocultos;

    public OportunidadesService(CoincidenciaRepositorio coincidencias, ProcesoOcultoRepositorio ocultos) {
        this.coincidencias = coincidencias;
        this.ocultos = ocultos;
    }

    public void ocultar(int userId, String procesoId) {
        ocultos.ocultar(userId, procesoId);
    }

    public void mostrar(int userId, String procesoId) {
        ocultos.mostrar(userId, procesoId);
    }

    /** Marca como vistas las oportunidades nuevas (solo las del usuario). */
    public void marcarVistas(int userId, List<Integer> idsCoincidencias) {
        coincidencias.marcarNotificadas(userId, idsCoincidencias);
    }

    /**
     * Filas ocultas por el usuario. Se compara por la Clave del proceso para
     * que tampoco reaparezca si SECOP lo vuelve a publicar con otro id.
     */
    static Predicate<Fila> oculta(List<Fila> filas, Set<String> idsOcultos) {
        Set<Clave> claves = new java.util.HashSet<>();
        for (Fila f : filas) if (idsOcultos.contains(f.proceso().id())) claves.add(Clave.de(f.proceso()));
        return f -> claves.contains(Clave.de(f.proceso()));
    }

    /** De cada grupo de duplicados queda la publicación más reciente (la vigente en SECOP). */
    static List<Fila> sinDuplicados(List<Fila> filas) {
        Comparator<Fila> masReciente = Comparator
                .comparing((Fila f) -> f.proceso().fechaPublicacion(), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(f -> f.proceso().id())
                .thenComparingInt(Fila::id);
        Map<Clave, Fila> mejor = new LinkedHashMap<>();
        for (Fila f : filas) {
            mejor.merge(Clave.de(f.proceso()), f, (a, b) -> masReciente.compare(a, b) >= 0 ? a : b);
        }
        return new ArrayList<>(mejor.values());
    }

    /**
     * Con un plan que limita cuántas coincidencias se ven, todo el panel
     * (lista, contadores, filtros) trabaja solo sobre las N mejores.
     */
    static List<Fila> visibles(List<Fila> vigentes, Plan plan) {
        List<Fila> orden = new ArrayList<>(vigentes);
        orden.sort(POR_RELEVANCIA);
        if (plan.maxCoincidencias() == null || orden.size() <= plan.maxCoincidencias()) return orden;
        return new ArrayList<>(orden.subList(0, plan.maxCoincidencias()));
    }

    /**
     * Lo que el usuario puede ver antes de filtros y orden: sin duplicados,
     * sin ocultos (salvo que los pida) y con el límite del plan. En modo API
     * lo calcula el servidor (OportunidadesServiceApi).
     */
    protected record Base(List<Fila> visibles, int ocultasPorPlan, int ocultasPorUsuario, Set<String> procesosOcultos) {
    }

    protected Base base(int userId, Plan plan, boolean incluirOcultas) {
        List<Fila> todas = coincidencias.vigentesDeUsuario(userId);
        Set<String> idsOcultos = ocultos.deUsuario(userId);
        Predicate<Fila> oculta = oculta(todas, idsOcultos);
        List<Fila> sinRepetir = sinDuplicados(todas);
        int ocultasPorUsuario = (int) sinRepetir.stream().filter(oculta).count();
        // Las ocultas no cuentan para el límite del plan ni para las cifras.
        List<Fila> vigentes = incluirOcultas ? sinRepetir : sinRepetir.stream().filter(oculta.negate()).toList();
        Set<String> procesosOcultos = new java.util.HashSet<>();
        for (Fila x : sinRepetir) if (oculta.test(x)) procesosOcultos.add(x.proceso().id());
        int ocultas = plan.maxCoincidencias() == null ? 0 : Math.max(vigentes.size() - plan.maxCoincidencias(), 0);
        return new Base(visibles(vigentes, plan), ocultas, ocultasPorUsuario, procesosOcultos);
    }

    public Panel panel(int userId, Plan plan, Filtros f) {
        Base b = base(userId, plan, f.ocultas());
        List<Fila> visibles = b.visibles();
        int ocultas = b.ocultasPorPlan();
        int ocultasPorUsuario = b.ocultasPorUsuario();
        Set<String> procesosOcultos = b.procesosOcultos();

        OffsetDateTime ahora = OffsetDateTime.now();
        OffsetDateTime enUnaSemana = ahora.plusDays(7);
        int cierranSemana = 0;
        long valorTotal = 0;
        TreeSet<String> deps = new TreeSet<>(), mods = new TreeSet<>(), ents = new TreeSet<>();
        for (Fila v : visibles) {
            Proceso p = v.proceso();
            if (p.fechaCierre() != null && !p.fechaCierre().isAfter(enUnaSemana)) cierranSemana++;
            if (p.valor() != null) valorTotal += p.valor();
            if (p.departamento() != null) deps.add(p.departamento());
            if (p.modalidad() != null) mods.add(p.modalidad());
            if (p.entidad() != null) ents.add(p.entidad());
        }

        List<Fila> filtradas = visibles.stream().filter(predicado(f)).sorted(orden(f.orden())).toList();
        List<Oportunidad> pagina = filtradas.stream().limit(f.limite())
                .map(OportunidadesService::aOportunidad).toList();

        return new Panel(pagina, visibles.size(), cierranSemana, valorTotal, filtradas.size(), ocultas,
                ocultasPorUsuario, procesosOcultos,
                new ArrayList<>(deps), new ArrayList<>(mods), new ArrayList<>(ents));
    }

    private static Oportunidad aOportunidad(Fila f) {
        return new Oportunidad(f.id(), f.score(), f.perfil(), f.notificado(), f.proceso());
    }

    private static Comparator<Fila> orden(String orden) {
        return switch (orden) {
            case "cierre" -> Comparator.comparing((Fila x) -> x.proceso().fechaCierre(), FECHA_NULOS_AL_FINAL);
            case "valor" -> Comparator.comparing((Fila x) -> x.proceso().valor(), Comparator.nullsLast(Comparator.reverseOrder()));
            default -> POR_RELEVANCIA;
        };
    }

    static Predicate<Fila> predicado(Filtros f) {
        List<Predicate<Proceso>> condiciones = new ArrayList<>();

        if (!f.q().isEmpty()) {
            Consulta consulta = Consulta.parsear(f.q());
            condiciones.add(p -> consulta.coincide(Matching.textosDe(p)));
        }
        if (!f.entidad().isEmpty()) {
            condiciones.add(p -> entidadParecida(f.entidad(), p.entidad()));
        }
        if (!f.departamento().isEmpty()) condiciones.add(p -> f.departamento().equals(p.departamento()));
        if (!f.region().isEmpty()) {
            List<String> deps = Region.porId(f.region()).map(Region::departamentos).orElse(List.of());
            condiciones.add(p -> deps.contains(p.departamento()));
        }
        if (!f.modalidad().isEmpty()) condiciones.add(p -> f.modalidad().equals(p.modalidad()));

        OffsetDateTime ahora = OffsetDateTime.now();
        switch (f.cierra()) {
            case "hoy" -> {
                // Antes de la medianoche de Colombia, no en 24 horas.
                OffsetDateTime manana = LocalDate.now(Formato.BOGOTA).plusDays(1).atStartOfDay(Formato.BOGOTA).toOffsetDateTime();
                condiciones.add(p -> p.fechaCierre() != null && p.fechaCierre().isBefore(manana));
            }
            case "semana" -> condiciones.add(p -> p.fechaCierre() != null && !p.fechaCierre().isAfter(ahora.plusDays(7)));
            case "mes" -> condiciones.add(p -> p.fechaCierre() != null && !p.fechaCierre().isAfter(ahora.plusDays(30)));
            default -> {
            }
        }
        if (!f.valor().isEmpty()) {
            List<RangoValor> rangos = f.valor().stream().map(RangoValor::porId).flatMap(java.util.Optional::stream).toList();
            condiciones.add(p -> p.valor() != null && rangos.stream().anyMatch(r -> r.contiene(p.valor())));
        }
        return fila -> condiciones.stream().allMatch(c -> c.test(fila.proceso()));
    }

    /**
     * Contiene el texto o se le parece: "icbf", "Bienestar Familiar" o
     * "gobernacion antioq" encuentran la entidad.
     */
    static boolean entidadParecida(String buscado, String entidad) {
        String b = Texto.normalizar(buscado);
        String e = Texto.normalizar(entidad);
        if (b.isEmpty()) return true;
        if (e.contains(b)) return true;
        List<String> palabrasEntidad = Texto.palabras(entidad);
        return Texto.palabras(buscado).stream().allMatch(w ->
                palabrasEntidad.stream().anyMatch(pe -> pe.startsWith(w) || (w.length() >= 5 && distancia(w, pe) <= 1)));
    }

    private static int distancia(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) return 2;
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + costo);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    // ---- Alertas ----

    public record Pendientes(List<Integer> idsCoincidencias, List<Oportunidad> oportunidades, Map<String, Integer> porPerfil) {
    }

    /**
     * Lo que va en el correo de alertas: coincidencias sin notificar de
     * perfiles activos y procesos vigentes. No es "nueva" si ya se avisó el
     * mismo proceso con otro id. Cada proceso sale una vez, con el mejor
     * puntaje del grupo.
     */
    public Pendientes alertasPendientes(int userId) {
        List<Fila> todas = coincidencias.vigentesDeUsuario(userId);
        List<Fila> filas = todas.stream().filter(oculta(todas, ocultos.deUsuario(userId)).negate()).toList();
        Map<Clave, Boolean> yaAvisados = new HashMap<>();
        for (Fila f : filas) if (f.notificado()) yaAvisados.put(Clave.de(f.proceso()), true);

        List<Fila> nuevas = filas.stream()
                .filter(f -> !f.notificado() && !yaAvisados.containsKey(Clave.de(f.proceso())))
                .sorted(Comparator.comparing((Fila f) -> f.proceso().fechaPublicacion(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(f -> f.proceso().id(), Comparator.reverseOrder()))
                .toList();

        Map<Clave, Oportunidad> porProceso = new LinkedHashMap<>();
        for (Fila f : nuevas) {
            porProceso.merge(Clave.de(f.proceso()), aOportunidad(f), (a, b) ->
                    b.score() > a.score() ? new Oportunidad(a.coincidenciaId(), b.score(), b.perfil(), false, a.proceso()) : a);
        }
        List<Oportunidad> ordenadas = new ArrayList<>(porProceso.values());
        ordenadas.sort(Comparator.comparingInt((Oportunidad o) -> -o.score())
                .thenComparing(o -> o.proceso().fechaCierre(), FECHA_NULOS_AL_FINAL));

        Map<String, Integer> porPerfil = new LinkedHashMap<>();
        for (Oportunidad o : ordenadas) porPerfil.merge(Objects.toString(o.perfil(), ""), 1, Integer::sum);

        return new Pendientes(nuevas.stream().map(Fila::id).toList(), ordenadas, porPerfil);
    }
}
