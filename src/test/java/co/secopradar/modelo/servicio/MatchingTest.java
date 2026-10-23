package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Perfil;
import co.secopradar.modelo.Proceso;
import co.secopradar.modelo.util.Texto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Port de src/lib/matching.test.mjs. */
class MatchingTest {

    private static Proceso proceso(String nombre, String objeto) {
        return proceso(nombre, objeto, "ALCALDÍA MUNICIPAL", "UNSPECIFIED");
    }

    private static Proceso proceso(String nombre, String objeto, String entidad) {
        return proceso(nombre, objeto, entidad, "UNSPECIFIED");
    }

    private static Proceso proceso(String nombre, String objeto, String entidad, String categoria) {
        return new Proceso("id-" + nombre + objeto + entidad + categoria, entidad, nombre, objeto, null, categoria,
                null, null, null, null, null, null, null, null, null);
    }

    private static Perfil clave(String... palabras) {
        return Perfil.borrador(List.of(), List.of(palabras), List.of());
    }

    private static Perfil claveYExcluir(List<String> clave, List<String> excluir) {
        return Perfil.borrador(List.of(), clave, excluir);
    }

    private static Perfil categorias(String... c) {
        return Perfil.borrador(List.of(c), List.of(), List.of());
    }

    private static boolean entra(Perfil perfil, Proceso p) {
        return Matching.calcularScore(perfil, p) != null;
    }

    @Test
    void normalizaMinusculasTildesEnieYSignos() {
        assertEquals("nandu arbol nino", Texto.normalizar("  Ñandú, ÁRBOL—“niño” "));
    }

    @Test
    void coincidePorPalabraCompletaNoPorPedazo() {
        assertFalse(entra(clave("red"), proceso("Redacción de documentos", "")));
        assertFalse(entra(clave("contabilidad"), proceso("", "interventoría contable")));
    }

    @Test
    void aceptaSingularYPluralSimple() {
        assertTrue(entra(clave("red"), proceso("Mantenimiento de redes", "")));
        assertTrue(entra(clave("redes de datos"), proceso("", "instalación de red de dato")));
        assertTrue(entra(clave("estudiante"), proceso("", "transporte de ESTUDIANTES")));
        assertTrue(entra(clave("luz"), proceso("", "luces navideñas")));
    }

    @Test
    void laFraseDebeIrCompletaYEnOrden() {
        assertFalse(entra(clave("bases de datos"), proceso("", "datos de bases")));
        assertTrue(entra(clave("diseno"), proceso("", "DISEÑO gráfico")));
        assertTrue(entra(clave("aseo cafeteria"), proceso("", "servicio de aseo, cafetería.")));
    }

    @Test
    void elNombreValeMasQueLaDescripcion() {
        int enNombre = Matching.calcularScore(clave("software"), proceso("Desarrollo de software", "x"));
        int enDescripcion = Matching.calcularScore(clave("software"), proceso("x", "desarrollo de software"));
        assertTrue(enNombre > enDescripcion);
    }

    @Test
    void laEntidadSolaNoEsSenal() {
        Proceso papeleria = proceso("Suministro de papelería", "compra de papelería y útiles de oficina", "MINISTERIO DE TRANSPORTE");
        assertFalse(entra(clave("transporte"), papeleria));
    }

    @Test
    void laEntidadSumaSiYaHayCoincidencia() {
        Proceso p = proceso("Transporte escolar", "transporte de estudiantes", "SECRETARÍA DE EDUCACIÓN");
        int conEntidad = Matching.calcularScore(clave("transporte", "educacion"), p);
        int sinEntidad = Matching.calcularScore(clave("transporte"), p);
        assertEquals(10, conEntidad - sinEntidad);
    }

    @Test
    void excluirMiraNombreYDescripcionNoLaEntidad() {
        Proceso software = proceso("Desarrollo de software", "sistema de información", "SECRETARÍA DE SALUD");
        assertTrue(entra(claveYExcluir(List.of("software"), List.of("salud")), software));
        Proceso licencia = proceso("Licencia de software", "");
        assertFalse(entra(claveYExcluir(List.of("software"), List.of("licencias")), licencia));
    }

    @Test
    void unspscJerarquico() {
        String cat = "V1.81111504";
        assertEquals(60, Matching.puntosCategoria(List.of("811115"), cat));
        assertEquals(45, Matching.puntosCategoria(List.of("8111"), cat));
        assertEquals(25, Matching.puntosCategoria(List.of("81"), cat));
        assertEquals(60, Matching.puntosCategoria(List.of("81", "8111", "811115"), cat));
        assertEquals(0, Matching.puntosCategoria(List.of("8110"), cat));
        assertEquals(0, Matching.puntosCategoria(List.of("81"), "UNSPECIFIED"));
    }

    @Test
    void perfilesViejosConSegmentosSiguenFuncionando() {
        Proceso p = proceso("Interventoría de obra", "", "ALCALDÍA MUNICIPAL", "V1.81101500");
        assertTrue(entra(categorias("81"), p));
        assertFalse(entra(categorias("8111"), p));
    }

    @Test
    void comprasDeBienesQuitaSuministrosNoServiciosConInsumos() {
        Perfil perfil = claveYExcluir(List.of("aseo"), List.of("compras de bienes"));
        assertFalse(entra(perfil, proceso("Suministro de insumos de aseo y cafetería", "para las sedes")));
        assertFalse(entra(perfil, proceso("ADQUISICIÓN DE ELEMENTOS DE ASEO", "")));
        assertTrue(entra(perfil, proceso("Servicio de aseo y cafetería con suministro de insumos", "")));
        assertTrue(entra(perfil, proceso("Suministro de personal de aseo", "prestación del servicio de aseo")));
        // Sin la exclusión especial, la frase normal no aplica la regla
        assertTrue(entra(clave("aseo"), proceso("Suministro de insumos de aseo", "")));
    }
}
