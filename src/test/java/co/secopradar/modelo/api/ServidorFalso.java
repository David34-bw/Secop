package co.secopradar.modelo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Servidor HTTP en memoria que imita la Edge Function "api" (y Storage)
 * para probar la implementación Api sin red real.
 */
public class ServidorFalso implements AutoCloseable {

    /** Una petición recibida. */
    public record Peticion(String operacion, String metodo, Map<String, List<String>> cabeceras, byte[] cuerpo) {
        public JsonNode json() {
            try {
                return ClienteApi.JSON.readTree(cuerpo.length == 0 ? "{}".getBytes() : cuerpo);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        public String cabecera(String nombre) {
            List<String> v = cabeceras.get(nombre);
            return v == null || v.isEmpty() ? null : v.get(0);
        }
    }

    /** Respuesta: estado HTTP y cuerpo JSON (texto). */
    public record Respuesta(int estado, String json) {
        public static Respuesta ok(String json) {
            return new Respuesta(200, json);
        }

        public static Respuesta error(int estado, String codigo, String mensaje) {
            return new Respuesta(estado, "{\"error\":{\"codigo\":\"" + codigo + "\",\"mensaje\":\"" + mensaje + "\",\"campos\":null}}");
        }
    }

    private final HttpServer servidor;
    private final Map<String, Function<Peticion, Respuesta>> rutas = new ConcurrentHashMap<>();
    public final List<Peticion> recibidas = new CopyOnWriteArrayList<>();

    public ServidorFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/functions/v1/api";
    }

    public String urlStorage(String ruta) {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/storage/" + ruta;
    }

    public ServidorFalso en(String operacion, Function<Peticion, Respuesta> manejador) {
        rutas.put(operacion, manejador);
        return this;
    }

    public ServidorFalso en(String operacion, String json) {
        return en(operacion, p -> Respuesta.ok(json));
    }

    public List<Peticion> de(String operacion) {
        return recibidas.stream().filter(p -> p.operacion().equals(operacion)).toList();
    }

    private void atender(HttpExchange ex) throws IOException {
        String ruta = ex.getRequestURI().getPath();
        String operacion = ruta.startsWith("/storage/") ? "storage" : ruta.substring(ruta.lastIndexOf('/') + 1);
        Peticion p = new Peticion(operacion, ex.getRequestMethod(), Map.copyOf(ex.getRequestHeaders().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(e -> e.getKey().toLowerCase(), Map.Entry::getValue, (a, b) -> a))),
                ex.getRequestBody().readAllBytes());
        recibidas.add(p);
        Function<Peticion, Respuesta> m = rutas.get(operacion);
        Respuesta r = m == null ? Respuesta.error(404, "no_encontrado", "Operación desconocida") : m.apply(p);
        byte[] b = r.json().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(r.estado(), b.length);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(b);
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
