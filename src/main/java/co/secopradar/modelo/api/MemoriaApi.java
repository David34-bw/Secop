package co.secopradar.modelo.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Respuestas de la API guardadas en memoria (RAM) para no volver a pedirlas
 * cada vez que se cambia de pantalla o de filtro.
 *
 * Reglas para no mostrar nunca datos viejos ni de otra persona:
 * - Solo se guardan las lecturas de la lista LECTURAS, cada una con su vigencia.
 * - Cualquier otra operación (guardar, borrar, ocultar...) borra todo lo del
 *   usuario; las cifras públicas de procesos se conservan, salvo "Actualizar ahora".
 * - Iniciar o cerrar sesión (o un 401) borra todo.
 * - Si algo se borra mientras una lectura viaja, esa respuesta no se guarda.
 * - Los errores nunca se guardan.
 * - Dos pedidos iguales al mismo tiempo (la precarga y una pantalla) hacen una sola llamada.
 * - Cada consulta recibe una copia: nadie puede cambiar lo guardado.
 */
public class MemoriaApi {

    private static final Duration CORTA = Duration.ofSeconds(30);
    private static final Duration MEDIA = Duration.ofMinutes(5);
    private static final Duration LARGA = Duration.ofMinutes(10);

    /** Lecturas que se guardan y cuánto duran. Lo que cambia el job diario dura menos. */
    private static final Map<String, Duration> LECTURAS = Map.ofEntries(
            // Públicas (las cambia el job diario o "Actualizar ahora").
            Map.entry("estado.datos", CORTA),
            Map.entry("inicio.datos", MEDIA),
            Map.entry("procesos.porIds", LARGA),
            // Del usuario (solo cambian con sus propias acciones, que borran la memoria).
            Map.entry("oportunidades.vigentes", MEDIA),
            Map.entry("oportunidades.ocultos", LARGA),
            Map.entry("cuenta.yo", LARGA),
            Map.entry("cuenta.foto", LARGA),
            Map.entry("perfiles.listar", LARGA),
            Map.entry("proponente.obtener", LARGA),
            Map.entry("documentos.listar", LARGA),
            Map.entry("postulaciones.listar", LARGA),
            Map.entry("postulaciones.idsPorProceso", LARGA),
            Map.entry("postulaciones.obtener", LARGA),
            // Administración: cambian con la actividad de todos.
            Map.entry("admin.resumen", CORTA),
            Map.entry("admin.usuarios", CORTA),
            Map.entry("admin.historial", CORTA));

    /** Se conservan cuando el usuario guarda algo (no dependen de él). */
    private static final Set<String> PUBLICAS = Set.of("estado.datos", "inicio.datos", "procesos.porIds");

    /**
     * Ni se guardan ni borran nada: procesos.vigentes ya se guarda aparte (ProcesoApi, por
     * partes) y las descargas devuelven URLs firmadas que vencen.
     */
    private static final Set<String> NEUTRAS = Set.of("procesos.vigentes", "documentos.descargar",
            "postulaciones.descargarPropuesta");

    /** Tope de respuestas guardadas (las menos usadas salen primero). */
    private static final int MAXIMO = 48;

    private record Guardada(JsonNode json, long venceNanos) {
    }

    private final Object candado = new Object();
    private final Map<String, Guardada> guardadas = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Guardada> e) {
            return size() > MAXIMO;
        }
    };
    private final Map<String, CompletableFuture<JsonNode>> enCamino = new HashMap<>();
    /** Sube cada vez que se borra algo; una respuesta que salió antes ya no se guarda. */
    private long generacion;
    private long aciertos;
    private long llamadas;

    public static boolean esLectura(String operacion) {
        return LECTURAS.containsKey(operacion);
    }

    /** Devuelve la respuesta guardada o la pide (una sola vez aunque la pidan varios a la vez). */
    JsonNode leer(String operacion, String cuerpo, Supplier<JsonNode> pedir) {
        String clave = operacion + " " + cuerpo;
        CompletableFuture<JsonNode> ajena;
        CompletableFuture<JsonNode> propia = null;
        long gen = 0;
        synchronized (candado) {
            Guardada g = guardadas.get(clave);
            if (g != null && System.nanoTime() - g.venceNanos() < 0) {
                aciertos++;
                return g.json().deepCopy();
            }
            if (g != null) guardadas.remove(clave);
            ajena = enCamino.get(clave);
            if (ajena == null) {
                propia = new CompletableFuture<>();
                enCamino.put(clave, propia);
                gen = generacion;
                llamadas++;
            } else {
                aciertos++;
            }
        }
        if (ajena != null) return esperar(ajena).deepCopy();

        JsonNode json;
        try {
            json = pedir.get();
        } catch (RuntimeException e) {
            synchronized (candado) {
                enCamino.remove(clave, propia);
            }
            propia.completeExceptionally(e);
            throw e;
        }
        synchronized (candado) {
            enCamino.remove(clave, propia);
            if (gen == generacion) {
                guardadas.put(clave, new Guardada(json, System.nanoTime() + LECTURAS.get(operacion).toNanos()));
            }
        }
        propia.complete(json);
        return json.deepCopy();
    }

    private static JsonNode esperar(CompletableFuture<JsonNode> f) {
        try {
            return f.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw e;
        }
    }

    /** Después de cualquier operación que no es lectura. */
    void despuesDeEscribir(String operacion) {
        if (NEUTRAS.contains(operacion)) return;
        if (operacion.startsWith("auth.") || operacion.equals("admin.actualizarAhora")) {
            borrarTodo();
            return;
        }
        synchronized (candado) {
            generacion++;
            guardadas.keySet().removeIf(clave -> !PUBLICAS.contains(clave.substring(0, clave.indexOf(' '))));
            enCamino.clear();
        }
    }

    /** Olvida todo (cambio de sesión, 401, botón Actualizar). */
    public void borrarTodo() {
        synchronized (candado) {
            generacion++;
            guardadas.clear();
            enCamino.clear();
        }
    }

    /** Cuántas consultas se resolvieron sin ir al servidor y cuántas fueron al servidor. */
    public long[] estadisticas() {
        synchronized (candado) {
            return new long[]{aciertos, llamadas};
        }
    }

    public int tamano() {
        synchronized (candado) {
            return guardadas.size();
        }
    }
}
