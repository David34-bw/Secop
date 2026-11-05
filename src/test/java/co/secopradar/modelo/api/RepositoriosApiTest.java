package co.secopradar.modelo.api;

import co.secopradar.modelo.Proceso;
import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.repositorio.CoincidenciaRepositorio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cliente y repositorios de la API contra un servidor HTTP simulado (sin red real). */
class RepositoriosApiTest {

    private ServidorFalso servidor;
    private ClienteApi api;

    @BeforeEach
    void iniciar() throws Exception {
        servidor = new ServidorFalso();
        api = new ClienteApi(servidor.url(), "sb_publishable_prueba");
    }

    @AfterEach
    void cerrar() {
        servidor.close();
        Sesion.cerrar();
    }

    private static final String USUARIO = "{\"id\":7,\"email\":\"ana@correo.co\",\"nombre\":\"Ana\",\"rol\":\"usuario\",\"plan\":\"gratis\","
            + "\"pruebaHasta\":\"2026-12-01T00:00:00Z\",\"graciaHasta\":null,\"prepagoPlan\":null,\"prepagoHasta\":null,"
            + "\"alertasActivas\":true,\"createdAt\":\"2026-10-01T10:00:00Z\",\"planEfectivo\":{\"id\":\"profesional\"}}";

    private static String proceso(String id, String cierre) {
        return "{\"id\":\"" + id + "\",\"entidad\":\"Alcaldía\",\"nombre\":\"N\",\"objeto\":\"Soporte\",\"modalidad\":\"Mínima cuantía\","
                + "\"categoria\":\"V1.43211500\",\"tipoContrato\":\"Prestación de servicios\",\"valor\":5000000,\"departamento\":\"Antioquia\","
                + "\"ciudad\":\"Medellín\",\"estadoApertura\":\"Abierto\",\"fechaPublicacion\":\"2026-11-01T05:00:00Z\","
                + "\"fechaCierre\":" + (cierre == null ? "null" : "\"" + cierre + "\"") + ",\"url\":\"https://secop/" + id + "\",\"ordenEntidad\":\"Territorial\"}";
    }

    @Test
    void mandaLaClaveYElTokenEnCabeceras() {
        servidor.en("cuenta.yo", USUARIO);
        Sesion.iniciar(new Usuario(7, "ana@correo.co", "Ana", "usuario", "gratis", null, null, null, null, true, null));
        api.usarToken("token-secreto");
        Usuario u = new UsuarioApi(api).porId(7).orElseThrow();

        assertEquals("Ana", u.nombre());
        assertEquals(OffsetDateTime.parse("2026-12-01T00:00:00Z"), u.pruebaHasta());
        ServidorFalso.Peticion p = servidor.de("cuenta.yo").get(0);
        assertEquals("POST", p.metodo());
        assertEquals("sb_publishable_prueba", p.cabecera("apikey"));
        assertEquals("token-secreto", p.cabecera("x-secop-sesion"));
    }

    @Test
    void sinSesionNoLlamaAlServidor() {
        assertTrue(new UsuarioApi(api).porId(7).isEmpty());
        assertTrue(servidor.recibidas.isEmpty());
    }

    @Test
    void erroresDelServidor() {
        servidor.en("cuenta.avisos", p -> ServidorFalso.Respuesta.error(401, "sin_sesion", "Tu sesión venció."));
        api.usarToken("viejo");
        ClienteApi.ErrorApi e = assertThrows(ClienteApi.ErrorApi.class, () -> new UsuarioApi(api).cambiarAvisos(7, true));
        assertEquals(401, e.estado);
        assertEquals("Tu sesión venció.", e.getMessage());
        assertFalse(api.conSesion(), "un 401 borra el token");

        servidor.close();
        ClienteApi.ErrorApi sinRed = assertThrows(ClienteApi.ErrorApi.class, () -> api.llamar("estado.datos", null));
        assertEquals(0, sinRed.estado);
        assertEquals("red", sinRed.codigo);
    }

    @Test
    void procesosVigentesPideSoloLosCambios() {
        String futuro = OffsetDateTime.now().plusDays(10).toString();
        String cerrado = OffsetDateTime.now().minusDays(5).toString();
        servidor.en("procesos.vigentes", p -> p.json().has("desde")
                ? ServidorFalso.Respuesta.ok("{\"procesos\":[" + proceso("P2", futuro) + "," + proceso("P1", cerrado) + "],\"hasta\":\"T2\"}")
                : ServidorFalso.Respuesta.ok("{\"procesos\":[" + proceso("P1", futuro) + "],\"hasta\":\"T1\"}"));
        ProcesoApi procesos = new ProcesoApi(api);

        assertEquals(List.of("P1"), procesos.vigentes().stream().map(Proceso::id).toList());
        // Segunda vez: manda "desde" y mezcla; P1 cerró hace más de 2 días y sale.
        assertEquals(List.of("P2"), procesos.vigentes().stream().map(Proceso::id).toList());
        assertEquals("T1", servidor.de("procesos.vigentes").get(1).json().path("desde").asText());
    }

    @Test
    void estadoEInicioUsanCache() {
        servidor.en("estado.datos", "{\"procesos\":2637,\"ultimoIngreso\":\"2026-11-05T02:00:00Z\"}");
        servidor.en("inicio.datos", "{\"resumen\":{\"abiertos\":10,\"nuevos_hoy\":2,\"cierran_semana\":3},"
                + "\"porDepartamentoYTipo\":[{\"departamento\":\"Antioquia\",\"tipo\":\"Obra\",\"n\":4}],"
                + "\"ofertasMinimaCuantia\":{\"cerrados\":5,\"una_o_ninguna\":2,\"desde\":\"2026-10-01T05:00:00Z\"}}");
        ProcesoApi procesos = new ProcesoApi(api);
        assertEquals(2637, procesos.contar());
        assertEquals(OffsetDateTime.parse("2026-11-05T02:00:00Z"), procesos.ultimoIngreso());
        assertEquals(10, ((Number) procesos.resumenAbiertos(null, null).get("abiertos")).intValue());
        assertEquals("Antioquia", procesos.abiertosPorDepartamentoYTipo(null).get(0).get("departamento"));
        assertTrue(procesos.ofertasMinimaCuantia(null).get("desde") instanceof OffsetDateTime);
        assertEquals(1, servidor.de("estado.datos").size(), "la segunda lectura sale de la caché");
        assertEquals(1, servidor.de("inicio.datos").size());
    }

    @Test
    void coincidenciasDelServidor() {
        servidor.en("oportunidades.vigentes", "{\"filas\":[{\"id\":3,\"score\":55,\"notificado\":false,\"perfilId\":9,"
                + "\"perfil\":\"Software\",\"proceso\":" + proceso("P1", null) + "}],\"ocultasPorPlan\":0}");
        servidor.en("oportunidades.marcarVistas", "{\"ok\":true}");
        CoincidenciaApi c = new CoincidenciaApi(api);
        List<CoincidenciaRepositorio.Fila> filas = c.vigentesDeUsuario(7);
        assertEquals(1, filas.size());
        assertEquals(55, filas.get(0).score());
        assertEquals("Antioquia", filas.get(0).proceso().departamento());
        assertNull(filas.get(0).proceso().fechaCierre());
        assertTrue(servidor.de("oportunidades.vigentes").get(0).json().path("incluirOcultas").asBoolean());

        c.marcarNotificadas(7, List.of(3, 4));
        assertEquals("[3,4]", servidor.de("oportunidades.marcarVistas").get(0).json().path("ids").toString());
        c.marcarNotificadas(7, List.of());
        assertEquals(1, servidor.de("oportunidades.marcarVistas").size(), "sin ids no llama");
    }

    @Test
    void ocultosYPostulaciones() {
        servidor.en("oportunidades.ocultos", "{\"procesos\":[\"P1\",\"P2\"]}");
        servidor.en("postulaciones.idsPorProceso", "{\"P1\":12}");
        assertEquals(Set.of("P1", "P2"), new OtrosApi.ProcesoOcultoApi(api).deUsuario(7));
        assertEquals(Map.of("P1", 12), new OtrosApi.PostulacionApi(api).idsPorProceso(7));
    }

    @Test
    void historialSoloDeTablasConocidas() {
        servidor.en("admin.historial", "{\"filas\":[{\"id\":1,\"corrida_at\":\"2026-11-05T02:00:00Z\",\"leidos\":900,\"exito\":true}]}");
        OtrosApi.LogApi logs = new OtrosApi.LogApi(api);
        Map<String, Object> f = logs.ultimas("ingesta_log", 8).get(0);
        assertTrue(f.get("corrida_at") instanceof OffsetDateTime);
        assertEquals("ingesta", servidor.de("admin.historial").get(0).json().path("tipo").asText());
        assertThrows(IllegalArgumentException.class, () -> logs.ultimas("users", 8));
    }

    @Test
    void loQueHaceElServidorNoSeLlamaDesdeLaApp() {
        assertThrows(SoloServidor.class, () -> new UsuarioApi(api).hashDe("a@b.co"));
        assertThrows(SoloServidor.class, () -> new PerfilApi(api).activos());
        assertThrows(SoloServidor.class, () -> new ProcesoApi(api).insertar(List.of()));
        assertThrows(SoloServidor.class, () -> new CoincidenciaApi(api).insertar(1, Map.of(), false));
        assertThrows(SoloServidor.class, () -> new OtrosApi.PostulacionApi(api).marcarRevisado(1, 1, true));
        assertTrue(servidor.recibidas.isEmpty());
    }

    @Test
    void descargaPorUrlFirmadaOBase64() {
        servidor.en("storage", p -> ServidorFalso.Respuesta.ok("%PDF-1.4"));
        servidor.en("documentos.descargar", p -> p.json().path("id").asInt() == 1
                ? ServidorFalso.Respuesta.ok("{\"nombre\":\"a.pdf\",\"url\":\"" + servidor.urlStorage("x/a.pdf") + "\"}")
                : ServidorFalso.Respuesta.ok("{\"nombre\":\"b.pdf\",\"base64\":\"JVBERi0xLjQ=\"}"));
        OtrosApi.DocumentoApi docs = new OtrosApi.DocumentoApi(api);
        assertEquals("%PDF-1.4", new String(docs.contenido(1, 7).orElseThrow()));
        assertEquals("%PDF-1.4", new String(docs.contenido(2, 7).orElseThrow()));
        assertEquals(null, servidor.de("storage").get(0).cabecera("apikey"), "la URL firmada no lleva la clave de la app");
    }
}
