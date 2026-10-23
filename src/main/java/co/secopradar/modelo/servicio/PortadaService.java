package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Region;
import co.secopradar.modelo.dao.ProcesoDAO;
import co.secopradar.modelo.util.Formato;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Datos en vivo de la portada (port de src/lib/portada.js), guardados en
 * memoria 10 minutos para no consultar la base en cada visita.
 */
public class PortadaService {

    /** Sector = tipo de contrato de SECOP (el código UNSPSC casi nunca viene en los abiertos). */
    public record Sector(String id, String nombre, List<String> tipos) {
    }

    public static final List<Sector> SECTORES = List.of(
            new Sector("servicios", "Prestación de servicios", List.of("Prestación de servicios")),
            new Sector("obra", "Obra", List.of("Obra")),
            new Sector("compraventa", "Compraventa", List.of("Compraventa")),
            new Sector("suministros", "Suministros", List.of("Suministros")),
            new Sector("consultoria", "Consultoría e interventoría", List.of("Consultoría", "Interventoría")),
            new Sector("otros", "Otros", List.of()));

    public record FilaRegion(Region region, int total, Map<String, Integer> porSector, List<Map.Entry<String, Integer>> departamentos) {
    }

    public record Datos(long abiertos, long nuevosHoy, long cierranSemana, List<FilaRegion> regiones, long sinDepartamento,
                        long ofertasCerrados, long ofertasUnaONinguna, Integer porcentaje, String desde, Instant calculadoEn) {

        public String numero(long n) {
            return Formato.numero(n);
        }

        public String fraseOfertas() {
            if (porcentaje == null) return "";
            if (porcentaje >= 45 && porcentaje <= 55) return "La mitad de los procesos recibe una sola oferta o ninguna.";
            return porcentaje + " de cada 100 procesos reciben una sola oferta o ninguna.";
        }
    }

    private final ProcesoDAO procesos;
    private volatile Datos cache;

    public PortadaService(ProcesoDAO procesos) {
        this.procesos = procesos;
    }

    public static String sectorDe(String tipo) {
        return SECTORES.stream().filter(s -> s.tipos().contains(tipo)).findFirst().orElse(SECTORES.get(SECTORES.size() - 1)).id();
    }

    public Datos datos() {
        Datos d = cache;
        if (d == null || d.calculadoEn().isBefore(Instant.now().minusSeconds(600))) {
            d = calcular();
            cache = d;
        }
        return d;
    }

    public void invalidar() {
        cache = null;
    }

    private static long n(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private Datos calcular() {
        OffsetDateTime ahora = OffsetDateTime.now();
        Map<String, Object> resumen = procesos.resumenAbiertos(ahora, Formato.inicioDeHoy());

        // { departamento: { sector: n } }, sin "No Definido".
        Map<String, Map<String, Integer>> porDepartamento = new TreeMap<>();
        long conDepartamento = 0;
        for (Map<String, Object> fila : procesos.abiertosPorDepartamentoYTipo(ahora)) {
            String dep = (String) fila.get("departamento");
            if (dep == null || dep.equals("No Definido")) continue;
            int cantidad = (int) n(fila.get("n"));
            porDepartamento.computeIfAbsent(dep, k -> new LinkedHashMap<>())
                    .merge(sectorDe((String) fila.get("tipo")), cantidad, Integer::sum);
            conDepartamento += cantidad;
        }

        List<FilaRegion> regiones = new ArrayList<>();
        for (Region r : Region.TODAS) {
            Map<String, Integer> sectores = new LinkedHashMap<>();
            SECTORES.forEach(s -> sectores.put(s.id(), 0));
            List<Map.Entry<String, Integer>> deps = new ArrayList<>();
            int total = 0;
            for (String dep : r.departamentos()) {
                Map<String, Integer> fila = porDepartamento.get(dep);
                if (fila == null) continue;
                int t = fila.values().stream().mapToInt(Integer::intValue).sum();
                fila.forEach((s, c) -> sectores.merge(s, c, Integer::sum));
                deps.add(Map.entry(Region.nombreCorto(dep), t));
                total += t;
            }
            deps.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            regiones.add(new FilaRegion(r, total, sectores, deps));
        }

        Map<String, Object> ofertas = procesos.ofertasMinimaCuantia(ahora);
        long cerrados = n(ofertas.get("cerrados"));
        long unaONinguna = n(ofertas.get("una_o_ninguna"));
        Object desde = ofertas.get("desde");
        String desdeTexto = null;
        if (desde instanceof OffsetDateTime f) desdeTexto = Formato.fechaLarga(f);
        else if (desde instanceof java.sql.Timestamp t) desdeTexto = Formato.fechaLarga(t.toInstant().atOffset(java.time.ZoneOffset.UTC));

        long abiertos = n(resumen.get("abiertos"));
        return new Datos(abiertos, n(resumen.get("nuevos_hoy")), n(resumen.get("cierran_semana")), regiones,
                abiertos - conDepartamento, cerrados, unaONinguna,
                cerrados > 0 ? (int) Math.round(100.0 * unaONinguna / cerrados) : null, desdeTexto, Instant.now());
    }
}
