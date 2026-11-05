package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Documento;
import co.secopradar.modelo.Preparacion;
import co.secopradar.modelo.Proponente;
import co.secopradar.modelo.TipoDocumento;
import co.secopradar.modelo.dao.ProponenteDAO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mis documentos: modalidades, vigencia y validación de los datos del proponente. */
class DocumentosTest {

    private static Documento doc(TipoDocumento t, LocalDate vence) {
        return new Documento(1, 1, t, "a.pdf", 1000, vence, OffsetDateTime.now());
    }

    private static Proponente registrado(boolean si) {
        Proponente v = Proponente.vacio();
        return new Proponente(v.tipoPersona(), v.tipoDocumento(), null, null, null, null, null, null, null, null, null,
                null, si, false, null, false);
    }

    @Test
    void modalidadesDeSecop() {
        assertEquals(Preparacion.Grupo.MINIMA_CUANTIA, Preparacion.Grupo.de("Mínima cuantía").orElseThrow());
        assertEquals(Preparacion.Grupo.SELECCION_ABREVIADA, Preparacion.Grupo.de("Seleccion Abreviada Menor Cuantia Sin Manifestacion Interes").orElseThrow());
        assertEquals(Preparacion.Grupo.SELECCION_ABREVIADA, Preparacion.Grupo.de("Selección abreviada subasta inversa").orElseThrow());
        assertEquals(Preparacion.Grupo.LICITACION, Preparacion.Grupo.de("Licitación pública Obra Publica").orElseThrow());
        assertEquals(Preparacion.Grupo.CONCURSO_MERITOS, Preparacion.Grupo.de("Concurso de méritos abierto").orElseThrow());
        assertTrue(Preparacion.Grupo.de("Solicitud de información a los Proveedores").isEmpty());
    }

    @Test
    void listoParaMinimaCuantiaPeroNoParaLicitacion() {
        List<Documento> docs = List.of(doc(TipoDocumento.CEDULA, null), doc(TipoDocumento.INHABILIDADES, null),
                doc(TipoDocumento.SEGURIDAD_SOCIAL, LocalDate.now().plusDays(20)));
        Preparacion p = new Preparacion(docs, registrado(true));
        assertTrue(p.estado(Preparacion.Grupo.MINIMA_CUANTIA).listo());
        assertEquals(List.of("Certificado del RUP", "Certificados de experiencia"),
                p.estado(Preparacion.Grupo.LICITACION).faltantes());
        assertFalse(new Preparacion(docs, registrado(false)).estado(Preparacion.Grupo.MINIMA_CUANTIA).listo());
    }

    @Test
    void unDocumentoVencidoNoCuenta() {
        List<Documento> docs = List.of(doc(TipoDocumento.CEDULA, null), doc(TipoDocumento.INHABILIDADES, null),
                doc(TipoDocumento.SEGURIDAD_SOCIAL, LocalDate.now().minusDays(1)));
        Preparacion p = new Preparacion(docs, registrado(true));
        assertEquals(List.of("Seguridad social al día"), p.estado(Preparacion.Grupo.MINIMA_CUANTIA).faltantes());
        assertEquals(Documento.Vigencia.POR_VENCER, doc(TipoDocumento.RUP, LocalDate.now().plusDays(10)).vigencia());
    }

    @Test
    void formatoDeArchivos() {
        assertTrue(DocumentosService.coincideFormato("pdf", "%PDF-1.7 ...".getBytes()));
        assertFalse(DocumentosService.coincideFormato("pdf", "MZ ejecutable".getBytes()));
        assertTrue(DocumentosService.coincideFormato("jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
    }

    @Test
    void datosDelProponente() {
        List<Proponente> guardados = new ArrayList<>();
        DocumentosService s = new DocumentosService(new ProponenteDAO() {
            @Override
            public void guardar(int usuarioId, Proponente p) {
                guardados.add(p);
            }
        }, null);

        Proponente ok = new Proponente("Persona natural", "Cédula de ciudadanía", " 1.012.345.678 ", "+57 300 123 4567",
                "Antioquia", "Medellín", "Ingeniero de sistemas", "Ingeniero de sistemas", "Universidad de Antioquia",
                "05255-123456", 3, "  Desarrollo   de software ", true, true, "12345", true);
        Proponente g = s.guardarDatos(1, ok);
        assertEquals("1012345678", g.numeroDocumento());
        assertEquals("3001234567", g.telefono());
        assertEquals("Desarrollo de software", g.resumen());
        assertEquals(1, guardados.size());

        Proponente malo = new Proponente("Persona natural", "Cédula de ciudadanía", "12ab", "123", null, "Medellín 2",
                "123", null, null, "#$%", 80, null, false, true, "!!", false);
        var e = assertThrows(DocumentosService.ErrorDatos.class, () -> s.guardarDatos(1, malo));
        assertTrue(e.errores.keySet().containsAll(List.of("numeroDocumento", "telefono", "ciudad", "profesion",
                "matriculaProfesional", "aniosExperiencia", "numeroRup")), e.errores.toString());

        Proponente nit = new Proponente("Persona jurídica", "NIT", "900123456-7", null, null, null, null, null, null,
                null, null, null, false, false, null, false);
        assertEquals("900123456-7", s.guardarDatos(1, nit).numeroDocumento());
    }
}
