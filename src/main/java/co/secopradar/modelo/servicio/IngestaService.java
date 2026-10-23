package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Modalidades;
import co.secopradar.modelo.Proceso;
import co.secopradar.modelo.dao.LogDAO;
import co.secopradar.modelo.dao.ProcesoDAO;
import co.secopradar.modelo.util.Formato;
import co.secopradar.modelo.util.Texto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.secopradar.modelo.util.Registro;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Descarga los procesos de SECOP II del portal de datos abiertos
 * (datos.gov.co, conjunto p6dx-8zbt) y los guarda en la base (port de
 * worker/ingesta.mjs).
 */
public class IngestaService {

    private static final Registro log = Registro.de(IngestaService.class);

    private static final String BASE = "https://www.datos.gov.co/resource/p6dx-8zbt.json";
    private static final int PAGINA = 1000;

    public record Resultado(int leidos, int nuevos, int actualizados) {
    }

    private final ProcesoDAO procesos;
    private final LogDAO logs;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String appToken;

    public IngestaService(ProcesoDAO procesos, LogDAO logs, ObjectMapper json,
                          String appToken) {
        this.procesos = procesos;
        this.logs = logs;
        this.json = json;
        this.appToken = appToken == null ? "" : appToken.trim();
    }

    public Resultado ejecutar(int diasAtras) {
        int leidos = 0, nuevos = 0, actualizados = 0;
        long inicio = System.currentTimeMillis();
        try {
            LocalDate desde = LocalDate.now(Formato.BOGOTA).minusDays(diasAtras);
            log.info("[ingesta] Buscando procesos publicados desde {}", desde);

            for (int offset = 0; ; offset += PAGINA) {
                JsonNode crudos = traerPagina(desde, offset);
                if (crudos.isEmpty()) break;

                Resultado r = guardar(crudos);
                leidos += crudos.size();
                nuevos += r.nuevos();
                actualizados += r.actualizados();
                log.info("[ingesta]   lote {}: {} leídos, {} nuevos, {} actualizados",
                        offset / PAGINA + 1, crudos.size(), r.nuevos(), r.actualizados());

                if (crudos.size() < PAGINA) break;
            }

            log.info("[ingesta] Terminado: {} leídos, {} nuevos, {} actualizados en {} s. Total en BD: {}",
                    leidos, nuevos, actualizados, (System.currentTimeMillis() - inicio) / 1000, procesos.contar());

            // Si no entra un proceso nuevo en varios días, algo está roto
            // (API caída, filtro de modalidad desactualizado...).
            OffsetDateTime ultimo = procesos.ultimoIngreso();
            if (ultimo != null) {
                double rezago = Duration.between(ultimo, OffsetDateTime.now()).toMinutes() / 1440.0;
                if (rezago >= 3) {
                    throw new IllegalStateException(String.format(
                            "No entra un proceso nuevo hace %.1f días. Revisa la API de Socrata o la ventana de días.", rezago));
                }
            }
            logs.ingesta(leidos, nuevos, actualizados, true, null);
            return new Resultado(leidos, nuevos, actualizados);
        } catch (Exception e) {
            log.error("[ingesta] Error: {}", e.getMessage());
            logs.ingesta(leidos, nuevos, actualizados, false, e.getMessage());
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
        }
    }

    private JsonNode traerPagina(LocalDate desde, int offset) throws IOException, InterruptedException {
        String lista = Modalidades.COMPETITIVAS.stream().map(m -> "'" + m + "'").collect(Collectors.joining(","));
        String where = "fecha_de_publicacion_del > '" + desde + "T00:00:00.000'"
                + " AND modalidad_de_contratacion in(" + lista + ")";

        Map<String, String> params = new LinkedHashMap<>();
        params.put("$where", where);
        params.put("$order", "fecha_de_publicacion_del DESC");
        params.put("$limit", String.valueOf(PAGINA));
        params.put("$offset", String.valueOf(offset));
        String qs = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(BASE + "?" + qs))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json");
        if (!appToken.isEmpty()) req.header("X-App-Token", appToken);

        // Reintenta cortes de red con espera creciente.
        for (int intento = 1; ; intento++) {
            try {
                HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() != 200) {
                    throw new IllegalStateException("API respondió " + res.statusCode() + ": " + Texto.recortar(res.body(), 300));
                }
                return json.readTree(res.body());
            } catch (IOException e) {
                if (intento >= 3) throw e;
                log.warn("[ingesta]   reintento {}/2: {}", intento, e.getMessage());
                Thread.sleep(intento * 3000L);
            }
        }
    }

    private Resultado guardar(JsonNode crudos) {
        // Si la fuente repite un id en la página, se queda la última versión.
        Map<String, ProcesoDAO.Fila> porId = new LinkedHashMap<>();
        for (JsonNode p : crudos) {
            ProcesoDAO.Fila f = normalizar(p);
            if (f.proceso().id() != null) porId.put(f.proceso().id(), f);
        }
        if (porId.isEmpty()) return new Resultado(0, 0, 0);

        Set<String> existentes = procesos.existentes(porId.keySet());
        List<ProcesoDAO.Fila> nuevos = new ArrayList<>();
        List<ProcesoDAO.Fila> viejos = new ArrayList<>();
        for (ProcesoDAO.Fila f : porId.values()) {
            (existentes.contains(f.proceso().id()) ? viejos : nuevos).add(f);
        }
        if (!nuevos.isEmpty()) procesos.insertar(nuevos);
        if (!viejos.isEmpty()) procesos.actualizar(viejos);
        return new Resultado(porId.size(), nuevos.size(), viejos.size());
    }

    ProcesoDAO.Fila normalizar(JsonNode p) {
        String objeto = texto(p, "descripci_n_del_procedimiento");
        if (objeto == null) objeto = texto(p, "nombre_del_procedimiento");
        Proceso proceso = new Proceso(
                texto(p, "id_del_proceso"),
                Texto.recortar(texto(p, "entidad"), 1000),
                Texto.recortar(texto(p, "nombre_del_procedimiento"), 4000),
                Texto.recortar(objeto, 20000),
                texto(p, "modalidad_de_contratacion"),
                texto(p, "codigo_principal_de_categoria"),
                texto(p, "tipo_de_contrato"),
                numero(p, "precio_base"),
                texto(p, "departamento_entidad"),
                texto(p, "ciudad_entidad"),
                texto(p, "estado_de_apertura_del_proceso"),
                fechaColombia(texto(p, "fecha_de_publicacion_del"), false),
                fechaColombia(texto(p, "fecha_de_recepcion_de"), true),
                p.path("urlproceso").path("url").isTextual() ? p.path("urlproceso").path("url").asText() : null,
                texto(p, "ordenentidad"));
        Long respuestas = numero(p, "respuestas_al_procedimiento");
        return new ProcesoDAO.Fila(proceso, texto(p, "estado_del_procedimiento"),
                respuestas == null ? null : respuestas.intValue(), p.toString());
    }

    private static String texto(JsonNode p, String campo) {
        JsonNode n = p.get(campo);
        if (n == null || n.isNull()) return null;
        String s = n.asText();
        return s.isEmpty() ? null : s;
    }

    private static Long numero(JsonNode p, String campo) {
        String s = texto(p, campo);
        if (s == null) return null;
        try {
            return Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * SECOP entrega fechas sin zona horaria y a medianoche
     * ("2026-10-19T00:00:00.000"): son días de Colombia. El cierre trae solo
     * el día, así que se toma el final de ese día (23:59:59) para no darlo
     * por cerrado mientras aún recibe ofertas; si llega con hora, se respeta.
     */
    static OffsetDateTime fechaColombia(String s, boolean esCierre) {
        if (s == null) return null;
        try {
            LocalDateTime f = LocalDateTime.parse(s.length() > 23 ? s.substring(0, 23) : s);
            if (esCierre && f.toLocalTime().equals(LocalTime.MIDNIGHT)) f = f.withHour(23).withMinute(59).withSecond(59);
            return f.atZone(Formato.BOGOTA).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
