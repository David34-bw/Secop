package co.secopradar.modelo.servicio;


import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de intentos por IP y ruta (port de src/lib/rateLimit.js). En
 * memoria: suficiente para una sola instancia local.
 */
public class LimiteIntentos {

    private record Ventana(Instant inicio, int conteo) {
    }

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    public boolean permitido(String ruta, String ip, int limite, Duration ventana) {
        String clave = ruta + ":" + ip;
        Instant ahora = Instant.now();
        Ventana v = ventanas.compute(clave, (k, actual) ->
                actual == null || actual.inicio().isBefore(ahora.minus(ventana))
                        ? new Ventana(ahora, 1)
                        : new Ventana(actual.inicio(), actual.conteo() + 1));
        if (ventanas.size() > 10_000) ventanas.entrySet().removeIf(e -> e.getValue().inicio().isBefore(ahora.minus(Duration.ofHours(2))));
        return v.conteo() <= limite;
    }
}
