package co.secopradar.modelo.servicio;

import co.secopradar.modelo.RangoValor;
import co.secopradar.modelo.Region;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Filtros del panel. Viven en la URL (?q=...&cierra=semana...) para que
 * se puedan recargar y compartir (port de dashboard/filtros.js).
 */
public record Filtros(String q, String entidad, String departamento, String region, String modalidad,
                      String cierra, List<String> valor, String orden, int limite) {

    public static final int LIMITE_INICIAL = 50;
    public static final int LIMITE_MAXIMO = 500;
    public static final int PASO_CARGAR_MAS = 50;
    private static final int MAX_TEXTO = 100;

    public static final List<String[]> OPCIONES_CIERRE = List.of(
            new String[]{"hoy", "Hoy"}, new String[]{"semana", "Esta semana"}, new String[]{"mes", "Este mes"});
    public static final List<String[]> OPCIONES_ORDEN = List.of(
            new String[]{"relevancia", "Más relevantes"}, new String[]{"cierre", "Cierran primero"}, new String[]{"valor", "Mayor valor"});

    private static String texto(String v) {
        if (v == null) return "";
        String t = v.trim();
        return t.length() > MAX_TEXTO ? t.substring(0, MAX_TEXTO) : t;
    }

    public static Filtros leer(Map<String, String> sp) {
        String cierra = sp.get("cierra");
        String orden = sp.get("orden");
        int limite;
        try {
            limite = Integer.parseInt(sp.getOrDefault("limite", ""));
        } catch (NumberFormatException e) {
            limite = LIMITE_INICIAL;
        }
        List<String> valor = Arrays.stream(texto(sp.get("valor")).split(","))
                .filter(id -> RangoValor.porId(id).isPresent()).toList();
        return new Filtros(
                texto(sp.get("q")),
                texto(sp.get("entidad")),
                texto(sp.get("departamento")),
                Region.porId(sp.getOrDefault("region", "")).map(Region::id).orElse(""),
                texto(sp.get("modalidad")),
                OPCIONES_CIERRE.stream().anyMatch(o -> o[0].equals(cierra)) ? cierra : "",
                valor,
                OPCIONES_ORDEN.stream().anyMatch(o -> o[0].equals(orden)) ? orden : "relevancia",
                Math.min(Math.max(limite, LIMITE_INICIAL), LIMITE_MAXIMO));
    }

    /** Cuántos filtros (no el orden ni la paginación) están activos. */
    public int contar() {
        int n = 0;
        for (String s : List.of(q, entidad, departamento, region, modalidad, cierra)) if (!s.isEmpty()) n++;
        if (!valor.isEmpty()) n++;
        return n;
    }

    public boolean tieneValor(String id) {
        return valor.contains(id);
    }

    public Filtros conQ(String q) {
        return new Filtros(q, entidad, departamento, region, modalidad, cierra, valor, orden, LIMITE_INICIAL);
    }

    public Filtros conCierra(String c) {
        return new Filtros(q, entidad, departamento, region, modalidad, c, valor, orden, LIMITE_INICIAL);
    }

    public Filtros alternarValor(String id) {
        List<String> v = new ArrayList<>(valor);
        if (!v.remove(id)) v.add(id);
        return new Filtros(q, entidad, departamento, region, modalidad, cierra, v, orden, LIMITE_INICIAL);
    }

    public Filtros conLimite(int l) {
        return new Filtros(q, entidad, departamento, region, modalidad, cierra, valor, orden, l);
    }

    public static Filtros sinFiltros(String orden) {
        return new Filtros("", "", "", "", "", "", List.of(), orden, LIMITE_INICIAL);
    }

    /** Enlace al panel con estos filtros. */
    public String href() {
        Map<String, String> qs = new LinkedHashMap<>();
        if (!q.isEmpty()) qs.put("q", q);
        if (!entidad.isEmpty()) qs.put("entidad", entidad);
        if (!departamento.isEmpty()) qs.put("departamento", departamento);
        if (!region.isEmpty()) qs.put("region", region);
        if (!modalidad.isEmpty()) qs.put("modalidad", modalidad);
        if (!cierra.isEmpty()) qs.put("cierra", cierra);
        if (!valor.isEmpty()) qs.put("valor", String.join(",", valor));
        if (!"relevancia".equals(orden)) qs.put("orden", orden);
        if (limite != LIMITE_INICIAL) qs.put("limite", String.valueOf(limite));
        if (qs.isEmpty()) return "/dashboard";
        StringBuilder sb = new StringBuilder("/dashboard?");
        qs.forEach((k, v) -> {
            if (sb.charAt(sb.length() - 1) != '?') sb.append('&');
            sb.append(k).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }
}
