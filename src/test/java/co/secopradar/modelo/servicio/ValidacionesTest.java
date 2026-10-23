package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Perfil;
import co.secopradar.modelo.Proceso;
import co.secopradar.modelo.RangoValor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidacionesTest {

    // ---- Correo ----

    @Test
    void correosValidos() {
        assertNull(CuentaService.validarFormatoCorreo("ana.perez@gmail.com"));
        assertNull(CuentaService.validarFormatoCorreo("contacto+secop@empresa.com.co"));
        assertNull(CuentaService.validarFormatoCorreo("j_g-2026@alcaldia-bogota.gov.co"));
    }

    @Test
    void correosConFormatoInvalido() {
        for (String malo : List.of("", "hola", "hola@", "@gmail.com", "ana@gmail", "ana@@gmail.com", "ana perez@gmail.com",
                ".ana@gmail.com", "ana.@gmail.com", "ana..perez@gmail.com", "ana@-gmail.com", "ana@gmail.c",
                "ana@gmail.123", "ana@gmail..com", "añá@gmail.com", "ana,perez@gmail.com")) {
            assertNotNull(CuentaService.validarFormatoCorreo(malo), malo);
        }
    }

    @Test
    void dominiosMalEscritosTemporalesYDeEjemplo() {
        assertEquals("¿Quisiste decir ana@gmail.com?", CuentaService.validarFormatoCorreo("ana@gmail.con"));
        assertEquals("No se permiten correos temporales", CuentaService.validarFormatoCorreo("ana@yopmail.com"));
        assertEquals("Usa tu correo real, no uno de ejemplo", CuentaService.validarFormatoCorreo("ana@example.com"));
        assertEquals("Usa tu correo real, no uno de ejemplo", CuentaService.validarFormatoCorreo("ana@secop-radar.test"));
    }

    @Test
    void dominioSinServidorDeCorreo() {
        CuentaService sinMx = new CuentaService(null, dominio -> false);
        assertEquals(CuentaService.SIN_MX, sinMx.validarCorreo("ana@dominio-inventado-xyz.com"));
        // Si el DNS no responde (null) no se bloquea a nadie.
        assertNull(new CuentaService(null, dominio -> null).validarCorreo("ana@empresa.com.co"));
    }

    // ---- "Otro" en el cruce ----

    private static Proceso proceso(String departamento, String tipo, long valor) {
        return new Proceso(departamento + tipo + valor, "ALCALDÍA", "Desarrollo de software", "", "Mínima cuantía",
                null, tipo, valor, departamento, null, null, null, null, null, null);
    }

    private static Perfil perfil(List<String> departamentos, List<String> tipos, List<String> rangos) {
        return new Perfil(null, null, "", List.of(), List.of("software"), List.of(), departamentos, List.of(), List.of(),
                tipos, List.of(), rangos, null, null, true, false, null);
    }

    @Test
    void lasOpcionesDeLaListaSonExactas() {
        Proceso norte = proceso("Norte de Santander", "Obra", 1);
        assertNull(Matching.calcularScore(perfil(List.of("Santander"), List.of(), List.of()), norte));
        assertNotNull(Matching.calcularScore(perfil(List.of("Norte de Santander"), List.of(), List.of()), norte));
    }

    @Test
    void loEscritoEnOtroIgnoraTildesYBastaUnaParte() {
        Proceso bogota = proceso("Distrito Capital de Bogotá", "Prestación de servicios", 1);
        assertNotNull(Matching.calcularScore(perfil(List.of("bogota"), List.of(), List.of()), bogota));
        assertNotNull(Matching.calcularScore(perfil(List.of(), List.of("PRESTACION DE SERVICIOS"), List.of()), bogota));
        assertNull(Matching.calcularScore(perfil(List.of("medellin"), List.of(), List.of()), bogota));
    }

    @Test
    void rangoDeValorPropio() {
        String id = RangoValor.personalizado(5_000_000, 20_000_000L).orElseThrow().id();
        assertEquals("otro:5000000-20000000", id);
        assertTrue(RangoValor.porId("otro:1000-").isPresent());
        assertFalse(RangoValor.personalizado(10, 5L).isPresent());
        assertFalse(RangoValor.personalizado(0, null).isPresent());

        Perfil p = perfil(List.of(), List.of(), List.of(id));
        assertNotNull(Matching.calcularScore(p, proceso("Meta", "Obra", 10_000_000)));
        assertNull(Matching.calcularScore(p, proceso("Meta", "Obra", 30_000_000)));
    }
}
