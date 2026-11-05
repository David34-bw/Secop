package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Oportunidad;
import co.secopradar.modelo.Plan;
import co.secopradar.modelo.dao.BaseDatos;
import co.secopradar.modelo.dao.CoincidenciaDAO;
import co.secopradar.modelo.dao.ProcesoOcultoDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ocultar / volver a mostrar oportunidades, contra una base H2 en memoria
 * con el mismo schema.sql de la app.
 */
class OcultosTest {

    private static final OportunidadesService servicio = new OportunidadesService(new CoincidenciaDAO(), new ProcesoOcultoDAO());
    private static int ana;
    private static int beto;

    @BeforeAll
    static void base() {
        BaseDatos.iniciar("jdbc:h2:mem:ocultos;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "sa", "");
        ana = usuario("ana@correo.co");
        beto = usuario("beto@correo.co");
        int perfilAna = perfil(ana);
        int perfilBeto = perfil(beto);
        OffsetDateTime cierre = OffsetDateTime.now().plusDays(10);
        for (int i = 1; i <= 3; i++) {
            BaseDatos.actualizar("INSERT INTO procesos (id_del_proceso, entidad, objeto, modalidad, valor, fecha_cierre, "
                    + "fecha_publicacion, url) VALUES (?, ?, ?, 'Mínima cuantía', ?, ?, ?, ?)", "P" + i, "Entidad " + i,
                    "Objeto " + i, 1_000_000L * i, cierre, OffsetDateTime.now(), i == 3 ? null : "https://secop/" + i);
            coincidencia(perfilAna, "P" + i, 50 + i);
            coincidencia(perfilBeto, "P" + i, 50 + i);
        }
    }

    @AfterAll
    static void cerrar() {
        BaseDatos.cerrar();
    }

    @BeforeEach
    void limpiar() {
        BaseDatos.actualizar("DELETE FROM procesos_ocultos");
    }

    private static int usuario(String email) {
        return BaseDatos.insertar("INSERT INTO users (email, password_hash, nombre) VALUES (?, 'x', 'Prueba')", email);
    }

    private static int perfil(int userId) {
        return BaseDatos.insertar("INSERT INTO perfiles (user_id, nombre) VALUES (?, 'Perfil')", userId);
    }

    private static void coincidencia(int perfilId, String procesoId, int score) {
        BaseDatos.actualizar("INSERT INTO coincidencias (perfil_id, proceso_id, score) VALUES (?, ?, ?)", perfilId, procesoId, score);
    }

    private static Filtros filtros(boolean ocultas) {
        Map<String, String> m = new HashMap<>();
        m.put("ocultas", String.valueOf(ocultas));
        return Filtros.leer(m);
    }

    private static List<String> ids(OportunidadesService.Panel p) {
        return p.oportunidades().stream().map(o -> o.proceso().id()).sorted().toList();
    }

    @Test
    void ocultarLoQuitaDelPanelYDeLasCifras() {
        servicio.ocultar(ana, "P2");
        OportunidadesService.Panel p = servicio.panel(ana, Plan.EMPRESA, filtros(false));
        assertEquals(List.of("P1", "P3"), ids(p));
        assertEquals(2, p.total());
        assertEquals(4_000_000L, p.valorTotal());
        assertEquals(1, p.ocultasPorUsuario());
    }

    @Test
    void mostrarOcultasLasIncluyeMarcadas() {
        servicio.ocultar(ana, "P2");
        OportunidadesService.Panel p = servicio.panel(ana, Plan.EMPRESA, filtros(true));
        assertEquals(List.of("P1", "P2", "P3"), ids(p));
        assertEquals(java.util.Set.of("P2"), p.procesosOcultos());
    }

    @Test
    void volverAMostrar() {
        servicio.ocultar(ana, "P2");
        servicio.mostrar(ana, "P2");
        OportunidadesService.Panel p = servicio.panel(ana, Plan.EMPRESA, filtros(false));
        assertEquals(List.of("P1", "P2", "P3"), ids(p));
        assertEquals(0, p.ocultasPorUsuario());
    }

    @Test
    void ocultarDosVecesNoFalla() {
        servicio.ocultar(ana, "P1");
        servicio.ocultar(ana, "P1");
        assertEquals(1, BaseDatos.numero("SELECT count(*) FROM procesos_ocultos WHERE usuario_id = ?", ana));
    }

    @Test
    void soloAfectaAlUsuarioQueOculta() {
        servicio.ocultar(ana, "P1");
        assertEquals(List.of("P1", "P2", "P3"), ids(servicio.panel(beto, Plan.EMPRESA, filtros(false))));
    }

    @Test
    void lasOcultasNoGastanElLimiteDelPlan() {
        // Gratis ve 5; con 3 procesos y 1 oculto ve los otros 2 sin "ocultas por plan".
        servicio.ocultar(ana, "P3");
        OportunidadesService.Panel p = servicio.panel(ana, Plan.GRATIS, filtros(false));
        assertEquals(List.of("P1", "P2"), ids(p));
        assertEquals(0, p.ocultasPorPlan());
    }

    @Test
    void lasOcultasNoSalenEnLosAvisos() {
        servicio.ocultar(ana, "P1");
        List<String> nuevas = servicio.alertasPendientes(ana).oportunidades().stream()
                .map(Oportunidad::proceso).map(pr -> pr.id()).sorted().toList();
        assertEquals(List.of("P2", "P3"), nuevas);
    }

    @Test
    void marcarVistasSoloTocaCoincidenciasPropias() {
        List<Integer> deAna = servicio.alertasPendientes(ana).idsCoincidencias();
        // Beto intenta marcar las de Ana: no cambia nada.
        servicio.marcarVistas(beto, deAna);
        assertFalse(servicio.alertasPendientes(ana).idsCoincidencias().isEmpty());
        servicio.marcarVistas(ana, deAna);
        assertTrue(servicio.alertasPendientes(ana).idsCoincidencias().isEmpty());
        BaseDatos.actualizar("UPDATE coincidencias SET notificado = FALSE");
    }
}
