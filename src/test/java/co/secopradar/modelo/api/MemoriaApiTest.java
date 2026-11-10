package co.secopradar.modelo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static co.secopradar.modelo.api.ClienteApi.cuerpo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La memoria de ClienteApi: rápida, pero sin datos viejos ni de otra sesión. */
class MemoriaApiTest {

    private ServidorFalso servidor;
    private ClienteApi api;
    private final AtomicInteger version = new AtomicInteger(1);

    @BeforeEach
    void iniciar() throws Exception {
        servidor = new ServidorFalso();
        api = new ClienteApi(servidor.url(), "sb_publishable_prueba");
        api.usarToken("t1");
        servidor.en("perfiles.listar", p -> ServidorFalso.Respuesta.ok("{\"v\":" + version.get() + "}"));
        servidor.en("estado.datos", p -> ServidorFalso.Respuesta.ok("{\"v\":" + version.get() + "}"));
        servidor.en("perfiles.eliminar", "{\"ok\":true}");
    }

    @AfterEach
    void cerrar() {
        servidor.close();
    }

    private int v(String op) {
        return api.llamar(op, null).path("v").asInt();
    }

    @Test
    void laSegundaLecturaNoVaAlServidor() {
        assertEquals(1, v("perfiles.listar"));
        assertEquals(1, v("perfiles.listar"));
        assertEquals(1, servidor.de("perfiles.listar").size());
    }

    @Test
    void cuerposDistintosSeGuardanAparte() {
        servidor.en("oportunidades.vigentes", p -> ServidorFalso.Respuesta.ok("{\"o\":" + p.json().path("incluirOcultas") + "}"));
        assertEquals("false", api.llamar("oportunidades.vigentes", cuerpo("incluirOcultas", false)).path("o").asText());
        assertEquals("true", api.llamar("oportunidades.vigentes", cuerpo("incluirOcultas", true)).path("o").asText());
        api.llamar("oportunidades.vigentes", cuerpo("incluirOcultas", false));
        assertEquals(2, servidor.de("oportunidades.vigentes").size());
    }

    @Test
    void guardarAlgoBorraLoDelUsuarioPeroNoLoPublico() {
        v("perfiles.listar");
        v("estado.datos");
        version.set(2);
        api.llamar("perfiles.eliminar", cuerpo("id", 3));

        assertEquals(2, v("perfiles.listar"), "después de escribir se vuelve a pedir");
        assertEquals(1, v("estado.datos"), "las cifras públicas siguen en memoria");
    }

    @Test
    void unaEscrituraQueFallaTambienBorra() {
        servidor.en("perfiles.eliminar", p -> ServidorFalso.Respuesta.error(500, "interno", "falló"));
        v("perfiles.listar");
        version.set(2);
        assertThrows(ClienteApi.ErrorApi.class, () -> api.llamar("perfiles.eliminar", cuerpo("id", 3)));
        assertEquals(2, v("perfiles.listar"));
    }

    @Test
    void otraSesionNoVeLoDeLaAnterior() {
        v("perfiles.listar");
        version.set(2);
        api.usarToken("t2");
        assertEquals(2, v("perfiles.listar"));

        v("estado.datos");
        version.set(3);
        api.olvidarToken();
        assertEquals(3, v("estado.datos"), "cerrar sesión borra todo");
    }

    @Test
    void un401BorraTodo() {
        v("estado.datos");
        servidor.en("cuenta.yo", p -> ServidorFalso.Respuesta.error(401, "sesion", "Inicia sesión de nuevo"));
        assertThrows(ClienteApi.ErrorApi.class, () -> api.llamar("cuenta.yo", null));
        version.set(2);
        assertEquals(2, v("estado.datos"));
    }

    @Test
    void losErroresNoSeGuardan() {
        AtomicInteger n = new AtomicInteger();
        servidor.en("documentos.listar", p -> n.incrementAndGet() == 1
                ? ServidorFalso.Respuesta.error(500, "interno", "falló")
                : ServidorFalso.Respuesta.ok("{\"documentos\":[]}"));
        assertThrows(ClienteApi.ErrorApi.class, () -> api.llamar("documentos.listar", null));
        assertTrue(api.llamar("documentos.listar", null).path("documentos").isArray());
    }

    @Test
    void cadaConsultaRecibeUnaCopia() {
        ((ObjectNode) api.llamar("perfiles.listar", null)).put("v", 99);
        assertEquals(1, v("perfiles.listar"));
    }

    @Test
    void lasDescargasNoSeGuardanNiBorran() {
        servidor.en("documentos.descargar", "{\"url\":\"x\"}");
        v("perfiles.listar");
        api.llamar("documentos.descargar", cuerpo("id", 1));
        api.llamar("documentos.descargar", cuerpo("id", 1));
        assertEquals(2, servidor.de("documentos.descargar").size());
        v("perfiles.listar");
        assertEquals(1, servidor.de("perfiles.listar").size());
    }

    @Test
    void dosPedidosIgualesALaVezHacenUnaSolaLlamada() throws Exception {
        CountDownLatch entro = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        servidor.en("postulaciones.listar", p -> {
            entro.countDown();
            esperar(soltar);
            return ServidorFalso.Respuesta.ok("{\"postulaciones\":[1]}");
        });
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<JsonNode> a = hilos.submit(() -> api.llamar("postulaciones.listar", null));
            assertTrue(entro.await(5, TimeUnit.SECONDS));
            Future<JsonNode> b = hilos.submit(() -> api.llamar("postulaciones.listar", null));
            Thread.sleep(200); // b ya está esperando la misma respuesta
            soltar.countDown();
            assertEquals(1, a.get(5, TimeUnit.SECONDS).path("postulaciones").size());
            assertEquals(1, b.get(5, TimeUnit.SECONDS).path("postulaciones").size());
            assertEquals(1, servidor.de("postulaciones.listar").size());
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    void unaLecturaQueViajabaMientrasSeGuardabaNoQuedaEnMemoria() throws Exception {
        CountDownLatch entro = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        AtomicInteger n = new AtomicInteger();
        servidor.en("perfiles.listar", p -> {
            if (n.incrementAndGet() == 1) {
                entro.countDown();
                esperar(soltar);
                return ServidorFalso.Respuesta.ok("{\"v\":1}");
            }
            return ServidorFalso.Respuesta.ok("{\"v\":2}");
        });
        ExecutorService hilo = Executors.newSingleThreadExecutor();
        try {
            Future<JsonNode> vieja = hilo.submit(() -> api.llamar("perfiles.listar", null));
            assertTrue(entro.await(5, TimeUnit.SECONDS));
            api.llamar("perfiles.eliminar", cuerpo("id", 3));
            soltar.countDown();
            assertEquals(1, vieja.get(5, TimeUnit.SECONDS).path("v").asInt());
            assertEquals(2, v("perfiles.listar"), "la respuesta vieja no se guardó");
        } finally {
            hilo.shutdownNow();
        }
    }

    private static void esperar(CountDownLatch l) {
        try {
            l.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
