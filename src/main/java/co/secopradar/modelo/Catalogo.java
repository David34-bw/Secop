package co.secopradar.modelo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogos de datos (resources/datos/*.json): profesiones, segmentos
 * UNSPSC con sus familias y clases. Se generaron a partir de
 * src/lib/profesiones.js, src/lib/unspsc.js y FormPerfil.js del original.
 */
public class Catalogo {

    public record Profesion(String id, String nombre, List<String> sinonimos, List<String> categorias,
                            List<String> palabrasClave, List<String> palabrasExcluir, boolean contratacionDirecta) {
    }

    public record Segmento(String code, String label, String ejemplos) {
    }

    public static final List<String> DEPARTAMENTOS = List.of(
            "Distrito Capital de Bogotá", "Antioquia", "Valle del Cauca", "Cundinamarca",
            "Atlántico", "Santander", "Bolívar", "Boyacá", "Nariño", "Córdoba",
            "Tolima", "Cauca", "Norte de Santander", "Magdalena", "Huila",
            "Caldas", "Risaralda", "Meta", "Cesar", "Sucre", "Quindío",
            "La Guajira", "Casanare", "Chocó", "Caquetá", "Putumayo", "Arauca");

    public static final List<String> TIPOS_CONTRATO = List.of(
            "Prestación de servicios", "Suministros", "Compraventa", "Obra", "Interventoría",
            "Consultoría", "Seguros", "Arrendamiento de muebles", "Concesión", "Servicios financieros");

    public static final List<String> TIPOS_ENTIDAD = List.of("Nacional", "Territorial", "Corporación Autónoma");

    private final ObjectMapper json;
    private final String avisoContratacionDirecta;
    private final List<Profesion> profesiones = new ArrayList<>();
    private final List<Segmento> segmentos = new ArrayList<>();
    private final JsonNode unspsc;
    private final Map<String, String> nombres = new HashMap<>();

    private static Catalogo instancia;

    /** Se carga una sola vez (los catálogos no cambian mientras corre la app). */
    public static synchronized Catalogo get() {
        if (instancia == null) instancia = new Catalogo(new ObjectMapper());
        return instancia;
    }

    private Catalogo(ObjectMapper json) {
        this.json = json;
        JsonNode prof = leer("datos/profesiones.json");
        avisoContratacionDirecta = prof.path("aviso").asText();
        for (JsonNode p : prof.path("profesiones")) {
            profesiones.add(new Profesion(
                    p.path("id").asText(), p.path("nombre").asText(),
                    lista(p.path("sinonimos")), lista(p.path("categorias")),
                    lista(p.path("palabras_clave")), lista(p.path("palabras_excluir")),
                    p.path("contratacion_directa").asBoolean(false)));
        }
        for (JsonNode s : leer("datos/segmentos.json")) {
            segmentos.add(new Segmento(s.path("code").asText(), s.path("label").asText(), s.path("ejemplos").asText()));
        }
        unspsc = leer("datos/unspsc.json");
        for (String grupo : List.of("familias", "clases")) {
            unspsc.path(grupo).forEach(filas -> filas.forEach(f -> nombres.put(f.get(0).asText(), f.get(1).asText())));
        }
        segmentos.forEach(s -> nombres.putIfAbsent(s.code(), s.label()));
    }

    private JsonNode leer(String ruta) {
        try (InputStream in = Catalogo.class.getResourceAsStream("/" + ruta)) {
            if (in == null) throw new IOException("No existe " + ruta);
            return json.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + ruta, e);
        }
    }

    private static List<String> lista(JsonNode n) {
        List<String> l = new ArrayList<>();
        n.forEach(x -> l.add(x.asText()));
        return l;
    }

    public List<Profesion> profesiones() {
        return profesiones;
    }

    public Optional<Profesion> profesion(String id) {
        return profesiones.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public List<Segmento> segmentos() {
        return segmentos;
    }

    public String avisoContratacionDirecta() {
        return avisoContratacionDirecta;
    }

    /** Familia (4 dígitos) o clase (6) UNSPSC con cuántos procesos la usan. */
    public record AreaUnspsc(String codigo, String nombre, int procesos) {
    }

    private List<AreaUnspsc> hijos(String grupo, String padre) {
        List<AreaUnspsc> l = new ArrayList<>();
        unspsc.path(grupo).path(padre).forEach(f -> l.add(new AreaUnspsc(f.get(0).asText(), f.get(1).asText(), f.get(2).asInt())));
        return l;
    }

    public List<AreaUnspsc> familias(String segmento) {
        return hijos("familias", segmento);
    }

    public List<AreaUnspsc> clases(String familia) {
        return hijos("clases", familia);
    }

    // ---- Selección jerárquica de áreas (port de src/lib/categorias.js) ----

    private static boolean esDescendiente(String codigo, String ancestro) {
        return codigo.length() > ancestro.length() && codigo.startsWith(ancestro);
    }

    /** Quita los códigos que tienen un descendiente elegido (afinar reemplaza al área general). */
    public static List<String> quitarAncestros(List<String> codigos) {
        List<String> unicos = new ArrayList<>(new java.util.LinkedHashSet<>(codigos));
        return unicos.stream().filter(c -> unicos.stream().noneMatch(o -> esDescendiente(o, c))).toList();
    }

    /** El área (o algo dentro de ella) está elegida. */
    public static boolean areaActiva(List<String> codigos, String codigo) {
        return codigos.stream().anyMatch(c -> c.equals(codigo) || esDescendiente(c, codigo));
    }

    /**
     * Marca o desmarca un área. Elegir una familia o clase reemplaza a su
     * ancestro; quitar la última subcategoría de un segmento devuelve el
     * segmento completo, para que el área no desaparezca sin querer.
     */
    public static List<String> alternarArea(List<String> codigos, String codigo) {
        if (areaActiva(codigos, codigo)) {
            List<String> restantes = codigos.stream().filter(c -> !c.equals(codigo) && !esDescendiente(c, codigo)).toList();
            String segmento = codigo.substring(0, 2);
            if (codigo.length() > 2 && restantes.stream().noneMatch(c -> c.startsWith(segmento))) {
                List<String> l = new ArrayList<>(restantes);
                l.add(segmento);
                return l;
            }
            return restantes;
        }
        List<String> l = new ArrayList<>(codigos.stream().filter(c -> !esDescendiente(c, codigo)).toList());
        l.add(codigo);
        return quitarAncestros(l);
    }

    /** Nombre de un segmento, familia o clase UNSPSC (o null). */
    public String nombreUnspsc(String codigo) {
        return nombres.get(codigo);
    }

    /** Todo el catálogo en un solo JSON para el formulario de perfiles. */
    public Map<String, Object> paraFormulario() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aviso", avisoContratacionDirecta);
        m.put("profesiones", profesiones);
        m.put("segmentos", segmentos);
        m.put("familias", unspsc.path("familias"));
        m.put("clases", unspsc.path("clases"));
        m.put("nombres", nombres);
        return m;
    }
}
