package co.secopradar.modelo.api;

import co.secopradar.modelo.Proceso;
import co.secopradar.modelo.repositorio.ProcesoRepositorio;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static co.secopradar.modelo.api.ClienteApi.cuerpo;

/**
 * Procesos por la API. Los vigentes (para Buscar y la vista previa de
 * perfiles) se guardan en memoria y luego solo se piden los que cambiaron,
 * para no bajar miles de procesos cada vez.
 */
public class ProcesoApi implements ProcesoRepositorio {

    private static final Duration VIGENCIA_CACHE_ESTADO = Duration.ofSeconds(30);
    private static final Duration VIGENCIA_CACHE_INICIO = Duration.ofMinutes(5);

    private final ClienteApi api;
    private final Map<String, Proceso> vigentes = new LinkedHashMap<>();
    private String hasta;
    private JsonNode estado;
    private Instant estadoEn = Instant.EPOCH;
    private JsonNode inicio;
    private Instant inicioEn = Instant.EPOCH;

    public ProcesoApi(ClienteApi api) {
        this.api = api;
    }

    @Override
    public synchronized List<Proceso> vigentes() {
        JsonNode r = api.llamar("procesos.vigentes", hasta == null ? null : cuerpo("desde", hasta));
        List<Proceso> nuevos = ClienteApi.convertir(r.path("procesos"),
                ClienteApi.JSON.getTypeFactory().constructCollectionType(List.class, Proceso.class));
        for (Proceso p : nuevos) vigentes.put(p.id(), p);
        hasta = r.path("hasta").asText(null);
        // Igual que en el servidor: sin cierre o que cerraron hace menos de 2 días.
        OffsetDateTime limite = OffsetDateTime.now().minusDays(2);
        vigentes.values().removeIf(p -> p.fechaCierre() != null && p.fechaCierre().isBefore(limite));
        return new ArrayList<>(vigentes.values());
    }

    @Override
    public Set<String> existentes(Collection<String> ids) {
        throw new SoloServidor("guardar procesos de SECOP (job.ingesta)");
    }

    @Override
    public void insertar(List<Fila> filas) {
        throw new SoloServidor("guardar procesos de SECOP (job.ingesta)");
    }

    @Override
    public void actualizar(List<Fila> filas) {
        throw new SoloServidor("guardar procesos de SECOP (job.ingesta)");
    }

    /** Cifras públicas (pantalla de ingreso y barra de estado); se repiten seguido, por eso la caché corta. */
    private synchronized JsonNode estado() {
        if (estado == null || Instant.now().isAfter(estadoEn.plus(VIGENCIA_CACHE_ESTADO))) {
            estado = api.llamar("estado.datos", null);
            estadoEn = Instant.now();
        }
        return estado;
    }

    @Override
    public long contar() {
        return estado().path("procesos").asLong();
    }

    @Override
    public OffsetDateTime ultimoIngreso() {
        JsonNode u = estado().path("ultimoIngreso");
        return u.isNull() || u.isMissingNode() ? null : OffsetDateTime.parse(u.asText());
    }

    private synchronized JsonNode inicio() {
        if (inicio == null || Instant.now().isAfter(inicioEn.plus(VIGENCIA_CACHE_INICIO))) {
            inicio = api.llamar("inicio.datos", null);
            inicioEn = Instant.now();
        }
        return inicio;
    }

    private static Map<String, Object> mapa(JsonNode n) {
        Map<String, Object> m = ClienteApi.JSON.convertValue(n, new TypeReference<LinkedHashMap<String, Object>>() {
        });
        // Igual que BaseDatos.filas: las fechas como OffsetDateTime.
        m.replaceAll((k, v) -> v instanceof String s && k.equals("desde") ? OffsetDateTime.parse(s) : v);
        return m;
    }

    @Override
    public List<Map<String, Object>> abiertosPorDepartamentoYTipo(OffsetDateTime ahora) {
        List<Map<String, Object>> l = new ArrayList<>();
        inicio().path("porDepartamentoYTipo").forEach(n -> l.add(mapa(n)));
        return l;
    }

    @Override
    public Map<String, Object> resumenAbiertos(OffsetDateTime ahora, OffsetDateTime inicioHoy) {
        return mapa(inicio().path("resumen"));
    }

    @Override
    public Map<String, Object> ofertasMinimaCuantia(OffsetDateTime ahora) {
        return mapa(inicio().path("ofertasMinimaCuantia"));
    }

    @Override
    public List<Proceso> porIds(Collection<String> ids) {
        if (ids.isEmpty()) return new ArrayList<>();
        return api.lista("procesos.porIds", cuerpo("ids", List.copyOf(ids)), "procesos", Proceso.class);
    }
}
