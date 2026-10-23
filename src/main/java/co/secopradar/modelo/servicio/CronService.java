package co.secopradar.modelo.servicio;

import co.secopradar.modelo.dao.ProcesoDAO;
import co.secopradar.modelo.util.Formato;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Actualización de datos (port de worker/cron.mjs): descarga de SECOP ->
 * cruce con los perfiles -> ajuste de perfiles al plan. Corre en segundo
 * plano: al abrir la app si la base está vacía, cada día a la hora
 * configurada mientras la app esté abierta, y cuando el administrador
 * presiona "Actualizar ahora".
 */
public class CronService {

    private static final Logger log = Logger.getLogger(CronService.class.getName());

    private final IngestaService ingesta;
    private final CoincidenciasService coincidencias;
    private final PlanesService planes;
    private final PortadaService portada;
    private final ProcesoDAO procesos;
    private final int diasAtras;
    private final int horaDiaria;

    private final ScheduledExecutorService hilo = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "actualizacion-secop");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean corriendo = new AtomicBoolean(false);
    private final Map<String, Consumer<String>> oyentes = new ConcurrentHashMap<>();
    private volatile String ultimoResultado = "Todavía no se ha actualizado en esta sesión";
    private volatile Instant ultimaCorrida;

    public CronService(IngestaService ingesta, CoincidenciasService coincidencias, PlanesService planes,
                       PortadaService portada, ProcesoDAO procesos, int diasAtras, int horaDiaria) {
        this.ingesta = ingesta;
        this.coincidencias = coincidencias;
        this.planes = planes;
        this.portada = portada;
        this.procesos = procesos;
        this.diasAtras = diasAtras;
        this.horaDiaria = horaDiaria;
    }

    /** Programa la corrida diaria y, si la base está vacía, descarga ya los últimos días. */
    public void iniciar(boolean descargarSiVacia, int diasPrimeraVez) {
        if (descargarSiVacia && procesos.contar() == 0) {
            log.info("La base no tiene procesos: descargando los últimos " + diasPrimeraVez + " días de SECOP II...");
            correrEnSegundoPlano(diasPrimeraVez);
        }
        ZonedDateTime ahora = ZonedDateTime.now(Formato.BOGOTA);
        ZonedDateTime proxima = ahora.withHour(horaDiaria).withMinute(0).withSecond(0).withNano(0);
        if (!proxima.isAfter(ahora)) proxima = proxima.plusDays(1);
        hilo.scheduleAtFixedRate(() -> correr(diasAtras),
                Duration.between(ahora, proxima).toMinutes(), TimeUnit.DAYS.toMinutes(1), TimeUnit.MINUTES);
    }

    public void detener() {
        hilo.shutdownNow();
    }

    /** Lanza la actualización en segundo plano. Devuelve false si ya hay una en curso. */
    public boolean correrEnSegundoPlano(int dias) {
        if (corriendo.get()) return false;
        hilo.execute(() -> correr(dias));
        return true;
    }

    private void correr(int dias) {
        if (!corriendo.compareAndSet(false, true)) return;
        ultimaCorrida = Instant.now();
        avisar("Descargando procesos de SECOP II (" + dias + " días)...");
        try {
            IngestaService.Resultado r = ingesta.ejecutar(dias);
            avisar("Cruzando los procesos con los perfiles...");
            CoincidenciasService.Resultado c = coincidencias.ejecutar();
            planes.ajustarTodos();
            portada.invalidar();
            avisar(String.format("Actualizado: %d procesos leídos (%d nuevos), %d coincidencias nuevas",
                    r.leidos(), r.nuevos(), c.nuevas()));
        } catch (Exception e) {
            log.log(Level.SEVERE, "La actualización falló", e);
            avisar("Error al actualizar: " + e.getMessage());
        } finally {
            corriendo.set(false);
            avisar(ultimoResultado);
        }
    }

    private void avisar(String mensaje) {
        ultimoResultado = mensaje;
        oyentes.values().forEach(o -> o.accept(mensaje));
    }

    /**
     * Las vistas se suscriben para mostrar el avance (se llama desde el
     * hilo de fondo). Una por clave: al volver a abrir una pantalla, su
     * nuevo oyente reemplaza al viejo en vez de acumularse.
     */
    public void alCambiarEstado(String clave, Consumer<String> oyente) {
        oyentes.put(clave, oyente);
    }

    public boolean estaCorriendo() {
        return corriendo.get();
    }

    public String ultimoResultado() {
        return ultimoResultado;
    }

    public Instant ultimaCorrida() {
        return ultimaCorrida;
    }
}
