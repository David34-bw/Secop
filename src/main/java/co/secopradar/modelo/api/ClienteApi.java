package co.secopradar.modelo.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cliente de la API de SECOP RADAR por HTTPS (Edge Function "api" de
 * Supabase, puerto 443). La app solo conoce la URL y la clave publishable;
 * la contraseña de la base nunca está en este computador.
 *
 * Cada operación es un POST a {urlBase}/{operacion} con un objeto JSON.
 * Después de iniciar sesión, el token viaja en la cabecera x-secop-sesion.
 */
public class ClienteApi {

    /** Error que respondió la API (o de red). */
    public static class ErrorApi extends RuntimeException {
        public final int estado;
        public final String codigo;
        public final Map<String, String> campos;

        public ErrorApi(int estado, String codigo, String mensaje, Map<String, String> campos) {
            super(mensaje);
            this.estado = estado;
            this.codigo = codigo;
            this.campos = campos == null ? Map.of() : campos;
        }

        /** Error de validación o de una regla (lo que el usuario debe corregir). */
        public boolean esDelUsuario() {
            return estado == 400 || estado == 404 || estado == 429;
        }
    }

    public static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final String urlBase;
    private final String clave;
    private final HttpClient http;
    private volatile String token;

    public ClienteApi(String urlBase, String clave) {
        this(urlBase, clave, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public ClienteApi(String urlBase, String clave, HttpClient http) {
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.clave = clave;
        this.http = http;
    }

    public String urlBase() {
        return urlBase;
    }

    // ---- Sesión ----

    public void usarToken(String token) {
        this.token = token;
    }

    public void olvidarToken() {
        this.token = null;
    }

    public boolean conSesion() {
        return token != null;
    }

    // ---- Llamadas ----

    /** Objeto JSON para el cuerpo: cuerpo("id", 5, "valor", true). */
    public static ObjectNode cuerpo(Object... claveValor) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < claveValor.length; i += 2) m.put((String) claveValor[i], claveValor[i + 1]);
        return JSON.valueToTree(m);
    }

    /** Llama una operación y devuelve la respuesta como árbol JSON. Lanza ErrorApi si falla. */
    public JsonNode llamar(String operacion, Object cuerpo) {
        String body;
        try {
            body = JSON.writeValueAsString(cuerpo == null ? Map.of() : cuerpo);
        } catch (IOException e) {
            throw new ErrorApi(0, "cliente", "No se pudo preparar la petición: " + e.getMessage(), null);
        }
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(urlBase + "/" + operacion))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("apikey", clave)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        String t = token;
        if (t != null) req.header("x-secop-sesion", t);
        HttpResponse<String> res;
        try {
            res = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ErrorApi(0, "red", "No hay conexión con el servidor de SECOP RADAR (" + e.getMessage() + ").", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorApi(0, "red", "Se interrumpió la conexión con el servidor.", null);
        }
        JsonNode json;
        try {
            json = res.body() == null || res.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(res.body());
        } catch (IOException e) {
            throw new ErrorApi(res.statusCode(), "respuesta", "El servidor respondió algo inesperado (" + res.statusCode() + ").", null);
        }
        if (res.statusCode() >= 200 && res.statusCode() < 300) return json;

        JsonNode error = json.path("error");
        Map<String, String> campos = new LinkedHashMap<>();
        error.path("campos").fields().forEachRemaining(e -> campos.put(e.getKey(), e.getValue().asText()));
        String mensaje = error.path("mensaje").asText("El servidor respondió " + res.statusCode() + ".");
        if (res.statusCode() == 401) token = null;
        throw new ErrorApi(res.statusCode(), error.path("codigo").asText("desconocido"), mensaje, campos);
    }

    /** Llama y convierte la respuesta (o una parte, si campo no es null) al tipo pedido. */
    public <T> T llamar(String operacion, Object cuerpo, String campo, Class<T> tipo) {
        JsonNode n = llamar(operacion, cuerpo);
        return convertir(campo == null ? n : n.path(campo), JSON.getTypeFactory().constructType(tipo));
    }

    public <T> java.util.List<T> lista(String operacion, Object cuerpo, String campo, Class<T> tipo) {
        JsonNode n = llamar(operacion, cuerpo).path(campo);
        return convertir(n, JSON.getTypeFactory().constructCollectionType(java.util.List.class, tipo));
    }

    public static <T> T convertir(JsonNode n, JavaType tipo) {
        try {
            return JSON.treeToValue(n, tipo);
        } catch (IOException e) {
            throw new ErrorApi(0, "respuesta", "No se pudo leer la respuesta del servidor: " + e.getMessage(), null);
        }
    }

    public static <T> T convertir(JsonNode n, Class<T> tipo) {
        return convertir(n, JSON.getTypeFactory().constructType(tipo));
    }

    // ---- Archivos (Storage con URL firmada; sin la clave de la app) ----

    public void subir(String url, byte[] datos, String contentType) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(datos))
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) throw new ErrorApi(res.statusCode(), "subida", "No se pudo subir el archivo (" + res.statusCode() + ").", null);
        } catch (IOException e) {
            throw new ErrorApi(0, "red", "No se pudo subir el archivo: " + e.getMessage(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorApi(0, "red", "Se interrumpió la subida.", null);
        }
    }

    public byte[] descargar(String url) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).GET().build();
        try {
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 != 2) throw new ErrorApi(res.statusCode(), "descarga", "No se pudo descargar el archivo (" + res.statusCode() + ").", null);
            return res.body();
        } catch (IOException e) {
            throw new ErrorApi(0, "red", "No se pudo descargar el archivo: " + e.getMessage(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorApi(0, "red", "Se interrumpió la descarga.", null);
        }
    }

    /** Respuesta de documentos.descargar / postulaciones.descargarPropuesta: URL firmada o base64. */
    public byte[] contenidoDe(JsonNode r) {
        if (r.hasNonNull("url")) return descargar(r.get("url").asText());
        if (r.hasNonNull("base64")) return java.util.Base64.getDecoder().decode(r.get("base64").asText());
        throw new ErrorApi(0, "respuesta", "El servidor no devolvió el archivo.", null);
    }
}
