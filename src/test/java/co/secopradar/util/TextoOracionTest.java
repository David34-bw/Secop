package co.secopradar.util;

import org.junit.jupiter.api.Test;

import static co.secopradar.util.TextoOracion.aOracion;
import static co.secopradar.util.TextoOracion.aTitulo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Port de src/lib/textoOracion.test.mjs. */
class TextoOracionTest {

    @Test
    void pasaATipoOracion() {
        assertEquals("Prestación del servicio de aseo y cafetería", aOracion("PRESTACIÓN DEL SERVICIO DE ASEO Y CAFETERÍA"));
        assertEquals("Municipio de san andrés de tumaco", aOracion("MUNICIPIO DE SAN ANDRÉS DE TUMACO"));
    }

    @Test
    void conservaLasSiglas() {
        assertEquals("Convenio SENA ICBF para IPS y ESE con TIC, SIG, CCTV, UPS y Wi-Fi",
                aOracion("CONVENIO SENA ICBF PARA IPS Y ESE CON TIC, SIG, CCTV, UPS Y WI-FI"));
        assertEquals("Instituto nacional de salud (ins) - DANE.", aOracion("INSTITUTO NACIONAL DE SALUD (INS) - DANE."));
    }

    @Test
    void palabrasSinVocalesQuedanEnMayusculas() {
        assertEquals("Sistema de gestión SST y PQRS con GNSS", aOracion("SISTEMA DE GESTIÓN SST Y PQRS CON GNSS"));
        assertEquals("Muy bien", aOracion("MUY BIEN"));
    }

    @Test
    void romanosDigitosYAbreviaturas() {
        assertEquals("Transporte escolar semestre-III fase II", aOracion("TRANSPORTE ESCOLAR SEMESTRE-III FASE II"));
        assertEquals("TIC29 contratar CO1.REQ.123 para transmilenio S.A.", aOracion("TIC29 CONTRATAR CO1.REQ.123 PARA TRANSMILENIO S.A."));
    }

    @Test
    void mayusculaDespuesDePunto() {
        assertEquals("Adquisición de equipos. Incluye instalación; garantía: un año",
                aOracion("ADQUISICIÓN DE EQUIPOS. INCLUYE INSTALACIÓN; GARANTÍA: UN AÑO"));
    }

    @Test
    void textosConMinusculasSeDejan() {
        assertEquals("Contratar el SOPORTE técnico", aOracion("Contratar el SOPORTE técnico"));
        assertFalse(TextoOracion.esTodoMayusculas("Hola"));
        assertNull(aOracion(null));
        assertEquals("2026 - 123", aOracion("2026 - 123"));
    }

    @Test
    void entidadesEnTitulo() {
        assertEquals("Alcaldía de Caucasia", aTitulo("ALCALDÍA DE CAUCASIA"));
        assertEquals("Instituto Nacional de Vías", aTitulo("INSTITUTO NACIONAL DE VÍAS"));
        assertEquals("Secretaría de la Mujer y los Derechos en el Municipio para las Víctimas por a e del",
                aTitulo("SECRETARÍA DE LA MUJER Y LOS DERECHOS EN EL MUNICIPIO PARA LAS VÍCTIMAS POR A E DEL"));
    }

    @Test
    void entidadesConservanSiglas() {
        assertEquals("Corporacion Autonoma Regional de Cundinamarca - CAR.", aTitulo("CORPORACION AUTONOMA REGIONAL DE CUNDINAMARCA - CAR."));
        assertEquals("Transmilenio S.A.", aTitulo("TRANSMILENIO S.A."));
        assertEquals("ESE Hospital San Rafael", aTitulo("ESE HOSPITAL SAN RAFAEL"));
        assertEquals("Servicio Nacional de Aprendizaje SENA Regional Bogota-Cundinamarca",
                aTitulo("SERVICIO NACIONAL DE APRENDIZAJE SENA REGIONAL BOGOTA-CUNDINAMARCA"));
        assertEquals("El Instituto", aTitulo("EL INSTITUTO"));
        assertEquals("Ministerio de Salud", aTitulo("Ministerio de Salud"));
    }
}
