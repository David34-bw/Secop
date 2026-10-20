package co.secopradar.dominio;

import co.secopradar.util.Formato;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Port de src/lib/planes.test.mjs y format.test.mjs. */
class PlanTest {

    private static final Instant AHORA = OffsetDateTime.parse("2026-10-18T12:00:00-05:00").toInstant();

    private static OffsetDateTime dias(double n) {
        return AHORA.plus(Duration.ofMillis((long) (n * 86_400_000))).atOffset(java.time.ZoneOffset.UTC);
    }

    private static Usuario usuario(String rol, String plan, OffsetDateTime prueba, OffsetDateTime gracia) {
        return new Usuario(1, "a@b.co", null, rol, plan, prueba, gracia, null, null, true, null);
    }

    @Test
    void preciosYLimites() {
        assertEquals(0, Plan.GRATIS.precioMensual());
        assertEquals(1, Plan.GRATIS.maxPerfiles());
        assertEquals(5, Plan.GRATIS.maxCoincidencias());
        assertEquals(69_900, Plan.PROFESIONAL.precioMensual());
        assertEquals(2, Plan.PROFESIONAL.maxPerfiles());
        assertNull(Plan.PROFESIONAL.maxCoincidencias());
        assertEquals(149_900, Plan.EMPRESA.precioMensual());
        assertEquals(10, Plan.EMPRESA.maxPerfiles());
    }

    @Test
    void enPruebaRigeProfesional() {
        Plan p = Plan.efectivo(usuario("usuario", "gratis", dias(30), null), AHORA);
        assertEquals("profesional", p.id());
        assertEquals("prueba", p.origen());
        assertEquals(30, p.diasRestantes());
    }

    @Test
    void alVencerLaPruebaPasaAGratis() {
        assertEquals("gratis", Plan.efectivo(usuario("usuario", "gratis", dias(-0.01), null), AHORA).id());
    }

    @Test
    void planPagadoYGracia() {
        assertEquals("empresa", Plan.efectivo(usuario("usuario", "empresa", dias(10), null), AHORA).id());
        Plan enGracia = Plan.efectivo(usuario("usuario", "empresa", null, dias(3)), AHORA);
        assertEquals("empresa", enGracia.id());
        assertEquals("gracia", enGracia.origen());
        assertEquals("gratis", Plan.efectivo(usuario("usuario", "empresa", null, dias(-1)), AHORA).id());
    }

    @Test
    void adminSinLimites() {
        Plan p = Plan.efectivo(usuario("admin", "gratis", null, null), AHORA);
        assertNull(p.maxPerfiles());
        assertNull(p.maxCoincidencias());
    }

    @Test
    void diasRestantesCuentaDiasDeColombia() {
        Instant manana10 = OffsetDateTime.parse("2026-10-18T10:00:00-05:00").toInstant();
        assertEquals(0, Formato.diasRestantes(OffsetDateTime.parse("2026-10-18T23:59:59-05:00"), manana10));
        assertEquals("Cierra hoy", Formato.textoCierre(0));
        assertEquals(1, Formato.diasRestantes(OffsetDateTime.parse("2026-10-19T23:59:59-05:00"), manana10));
        assertEquals(7, Formato.diasRestantes(OffsetDateTime.parse("2026-10-25T23:59:59-05:00"), manana10));
        assertEquals(-1, Formato.diasRestantes(OffsetDateTime.parse("2026-10-17T23:59:59-05:00"), manana10));
        // A las 11 p. m. de Colombia (ya otro día en UTC) sigue siendo "hoy".
        Instant noche = OffsetDateTime.parse("2026-10-18T23:00:00-05:00").toInstant();
        assertEquals(0, Formato.diasRestantes(OffsetDateTime.parse("2026-10-18T23:59:59-05:00"), noche));
    }
}
