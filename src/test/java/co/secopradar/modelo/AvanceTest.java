package co.secopradar.modelo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Etapas de una postulación: orden, documentos y resumen. */
class AvanceTest {

    private static Postulacion post(boolean revisado, boolean pliego, List<Postulacion.Requisito> extra, Long oferta,
                                    OffsetDateTime presentada, Postulacion.Resultado r) {
        return new Postulacion(1, 1, "CO1", "Alcaldía", "Soporte", "Mínima cuantía", 10_000_000L,
                OffsetDateTime.now().plusDays(5), null, revisado, pliego, null, extra, oferta, null, presentada, r,
                null, false, OffsetDateTime.now());
    }

    private static Preparacion prep(boolean registrado, boolean conDocumentos) {
        List<Documento> docs = conDocumentos ? List.of(
                new Documento(1, 1, TipoDocumento.CEDULA, "c.pdf", 1, null, OffsetDateTime.now()),
                new Documento(2, 1, TipoDocumento.INHABILIDADES, "i.pdf", 1, null, OffsetDateTime.now()),
                new Documento(3, 1, TipoDocumento.SEGURIDAD_SOCIAL, "s.pdf", 1, LocalDate.now().plusDays(20), OffsetDateTime.now()))
                : List.of();
        Proponente v = Proponente.vacio();
        return new Preparacion(docs, new Proponente(v.tipoPersona(), v.tipoDocumento(), null, null, null, null, null,
                null, null, null, null, null, registrado, false, null, false));
    }

    @Test
    void empiezaEnElRegistro() {
        Avance a = new Avance(post(false, false, List.of(), null, null, null), prep(false, false));
        assertEquals(Etapa.REGISTRO, a.actual());
        assertEquals(Avance.Estado.ACTUAL, a.estado(Etapa.REGISTRO));
        assertEquals(Avance.Estado.BLOQUEADA, a.estado(Etapa.REVISAR));
        assertFalse(a.habilitada(Etapa.PLIEGO));
        assertEquals(0.0, a.progreso());
        assertEquals("Etapa 1 de 7: Registrarte en SECOP II", a.resumen());
    }

    @Test
    void losDocumentosSeCompletanSolos() {
        Avance sinDocs = new Avance(post(true, true, List.of(), null, null, null), prep(true, false));
        assertEquals(Etapa.DOCUMENTOS, sinDocs.actual());
        assertEquals(3, sinDocs.faltantesDocumentos().size());

        Avance conDocs = new Avance(post(true, true, List.of(), null, null, null), prep(true, true));
        assertEquals(Etapa.PROPUESTA, conDocs.actual());

        Avance requisitoPendiente = new Avance(post(true, true, List.of(new Postulacion.Requisito("Curso de alturas", false)),
                null, null, null), prep(true, true));
        assertEquals(List.of("Curso de alturas"), requisitoPendiente.faltantesDocumentos());
        assertEquals(Etapa.DOCUMENTOS, requisitoPendiente.actual());
    }

    @Test
    void unaEtapaMarcadaNoCuentaSiFaltaUnaAnterior() {
        // Marcó la propuesta pero después quitó un documento: la etapa 4 vuelve a ser la actual.
        Avance a = new Avance(post(true, true, List.of(), 9_000_000L, null, null), prep(true, false));
        assertEquals(Etapa.DOCUMENTOS, a.actual());
        assertEquals(Avance.Estado.BLOQUEADA, a.estado(Etapa.PROPUESTA));
        assertEquals(3, a.completas());
    }

    @Test
    void terminada() {
        Avance a = new Avance(post(true, true, List.of(), 9_000_000L, OffsetDateTime.now(), Postulacion.Resultado.GANADA),
                prep(true, true));
        assertNull(a.actual());
        assertEquals(1.0, a.progreso());
        assertEquals("¡Ganada!", a.resumen());
        assertTrue(a.postulacion().terminada());

        Avance subsanar = new Avance(post(true, true, List.of(), 9_000_000L, OffsetDateTime.now(), Postulacion.Resultado.SUBSANAR),
                prep(true, true));
        assertEquals(Etapa.EVALUACION, subsanar.actual());
        assertFalse(subsanar.postulacion().terminada());
    }
}
