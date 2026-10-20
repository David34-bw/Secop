package co.secopradar.servicio;

import co.secopradar.repositorio.ProcesoRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Corrida diaria (port de worker/cron.mjs): ingesta -> cruce -> planes ->
 * alertas. Reemplaza al GitHub Action del original con @Scheduled.
 */
@Service
public class CronService {

    private static final Logger log = LoggerFactory.getLogger(CronService.class);

    private final IngestaService ingesta;
    private final CoincidenciasService coincidencias;
    private final PlanesService planes;
    private final AlertasService alertas;
    private final PortadaService portada;
    private final ProcesoRepositorio procesos;
    private final int diasAtras;
    private final int diasPrimeraVez;
    private final boolean alIniciar;

    private final AtomicBoolean corriendo = new AtomicBoolean(false);
    private volatile String ultimoResultado = "Todavía no ha corrido en esta sesión";
    private volatile Instant ultimaCorrida;

    public CronService(IngestaService ingesta, CoincidenciasService coincidencias, PlanesService planes,
                       AlertasService alertas, PortadaService portada, ProcesoRepositorio procesos,
                       @Value("${secop.ingesta.dias-atras:5}") int diasAtras,
                       @Value("${secop.ingesta.dias-primera-vez:7}") int diasPrimeraVez,
                       @Value("${secop.ingesta.al-iniciar:true}") boolean alIniciar) {
        this.ingesta = ingesta;
        this.coincidencias = coincidencias;
        this.planes = planes;
        this.alertas = alertas;
        this.portada = portada;
        this.procesos = procesos;
        this.diasAtras = diasAtras;
        this.diasPrimeraVez = diasPrimeraVez;
        this.alIniciar = alIniciar;
    }

    @Scheduled(cron = "${secop.cron}", zone = "America/Bogota")
    public void diario() {
        correr(diasAtras);
    }

    /** Primera vez: si la base está vacía, descarga los últimos días en segundo plano. */
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        if (alIniciar && procesos.contar() == 0) {
            log.info("[cron] La base no tiene procesos: descargando los últimos {} días de SECOP II en segundo plano...", diasPrimeraVez);
            Thread.ofVirtual().name("ingesta-inicial").start(() -> correr(diasPrimeraVez));
        }
    }

    /** Lanza la corrida en segundo plano (botón de /admin). Devuelve false si ya hay una en curso. */
    public boolean correrEnSegundoPlano(int dias) {
        if (corriendo.get()) return false;
        Thread.ofVirtual().name("cron-manual").start(() -> correr(dias));
        return true;
    }

    public void correr(int dias) {
        if (!corriendo.compareAndSet(false, true)) {
            log.warn("[cron] Ya hay una corrida en curso; se omite esta.");
            return;
        }
        ultimaCorrida = Instant.now();
        ultimoResultado = "En curso...";
        try {
            IngestaService.Resultado r = ingesta.ejecutar(dias);
            CoincidenciasService.Resultado c = coincidencias.ejecutar();
            planes.ajustarTodos();
            alertas.ejecutar();
            portada.invalidar();
            ultimoResultado = String.format("OK: %d procesos leídos (%d nuevos), %d coincidencias nuevas",
                    r.leidos(), r.nuevos(), c.nuevas());
            log.info("[cron] Terminado sin errores");
        } catch (Exception e) {
            ultimoResultado = "Error: " + e.getMessage();
            log.error("[cron] Abortado: {}", e.getMessage(), e);
        } finally {
            corriendo.set(false);
        }
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
