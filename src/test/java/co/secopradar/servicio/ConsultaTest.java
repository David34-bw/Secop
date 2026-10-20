package co.secopradar.servicio;

import co.secopradar.dominio.Proceso;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsultaTest {

    private static Matching.Textos textos(String nombre, String objeto) {
        return Matching.textosDe(new Proceso(nombre + "|" + objeto, "ALCALDÍA", nombre, objeto, null, null, null, null,
                null, null, null, null, null, null, null));
    }

    @Test
    void palabrasSueltasDebenEstarTodas() {
        Consulta c = Consulta.parsear("soporte tecnico");
        assertTrue(c.coincide(textos("Soporte técnico de equipos", "")));
        assertFalse(c.coincide(textos("Soporte de equipos", "")));
    }

    @Test
    void fraseOrYExclusion() {
        Consulta c = Consulta.parsear("\"soporte técnico\" OR redes -licencias");
        assertTrue(c.coincide(textos("Mantenimiento de redes", "")));
        assertTrue(c.coincide(textos("", "servicio de soporte técnico")));
        assertFalse(c.coincide(textos("Renovación de licencias", "soporte técnico")));
        assertFalse(c.coincide(textos("técnico de soporte", "")));
    }

    @Test
    void soloPalabrasVaciasNoFiltra() {
        Consulta c = Consulta.parsear("de la");
        assertTrue(c.vacia());
        assertTrue(c.coincide(textos("Cualquier cosa", "")));
    }

    @Test
    void entidadParecida() {
        assertTrue(OportunidadesService.entidadParecida("icbf", "INSTITUTO COLOMBIANO DE BIENESTAR FAMILIAR ICBF"));
        assertTrue(OportunidadesService.entidadParecida("Bienestar Familiar", "INSTITUTO COLOMBIANO DE BIENESTAR FAMILIAR"));
        assertTrue(OportunidadesService.entidadParecida("gobernacion antioqia", "GOBERNACIÓN DE ANTIOQUIA"));
        assertFalse(OportunidadesService.entidadParecida("ejercito", "GOBERNACIÓN DE ANTIOQUIA"));
    }
}
