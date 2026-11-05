package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Plan;
import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.TipoDocumento;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.api.ClienteApi;
import co.secopradar.modelo.api.CoincidenciaApi;
import co.secopradar.modelo.api.OtrosApi;
import co.secopradar.modelo.api.ServidorFalso;
import co.secopradar.modelo.api.ServidorFalso.Respuesta;
import co.secopradar.modelo.api.UsuarioApi;
import co.secopradar.modelo.dao.BaseDatos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Servicios en modo API y elección de modo, contra un servidor HTTP simulado. */
class ServiciosApiTest {

    private ServidorFalso servidor;
    private ClienteApi api;

    private static final String USUARIO = "{\"id\":7,\"email\":\"ana.perez@gmail.com\",\"nombre\":\"Ana Pérez\",\"rol\":\"usuario\","
            + "\"plan\":\"gratis\",\"alertasActivas\":true}";

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

    private CuentaServiceApi cuentas() {
        return new CuentaServiceApi(new UsuarioApi(api), api, dominio -> true);
    }

    @Test
    void loginGuardaElTokenYErroresSeMuestran() {
        servidor.en("auth.login", p -> p.json().path("password").asText().equals("Lic1taciones#Bog")
                ? Respuesta.ok("{\"token\":\"tk-1\",\"expira\":\"2026-11-12T00:00:00Z\",\"usuario\":" + USUARIO + "}")
                : Respuesta.error(400, "invalido", "Correo o contraseña incorrectos."));
        CuentaServiceApi cuentas = cuentas();

        assertTrue(cuentas.autenticar(" ANA.perez@gmail.com ", "mala").isEmpty());
        assertFalse(api.conSesion());
        Usuario u = cuentas.autenticar("ana.perez@gmail.com", "Lic1taciones#Bog").orElseThrow();
        assertEquals(7, u.id());
        assertTrue(api.conSesion());
        assertEquals("ana.perez@gmail.com", servidor.de("auth.login").get(0).json().path("email").asText(), "el correo va limpio");

        servidor.en("auth.login", p -> Respuesta.error(429, "demasiados_intentos", "Demasiados intentos. Espera 15 minutos."));
        CuentaService.ErrorCuenta e = assertThrows(CuentaService.ErrorCuenta.class, () -> cuentas.autenticar("ana.perez@gmail.com", "x"));
        assertTrue(e.getMessage().startsWith("Demasiados intentos"));
    }

    @Test
    void registroValidaAntesDeEnviar() {
        servidor.en("auth.registrar", "{\"token\":\"tk-2\",\"usuario\":" + USUARIO + "}");
        CuentaServiceApi cuentas = cuentas();
        assertThrows(CuentaService.ErrorCuenta.class, () -> cuentas.registrar("ana.perez@gmail.com", "Lic1taciones#Bog", "asdf", true));
        assertThrows(CuentaService.ErrorCuenta.class, () -> cuentas.registrar("ana.perez@gmail.com", "clave", "Ana Pérez", true));
        assertTrue(servidor.recibidas.isEmpty(), "con datos malos no se llama al servidor");

        Usuario u = cuentas.registrar("ana.perez@gmail.com", "Lic1taciones#Bog", "Ana Pérez", true);
        assertEquals("Ana Pérez", u.nombre());
        assertTrue(api.conSesion());
        assertTrue(servidor.de("auth.registrar").get(0).json().path("aceptaTerminos").asBoolean());
    }

    @Test
    void cerrarSesionAvisaAlServidor() {
        servidor.en("auth.logout", "{\"ok\":true}");
        api.usarToken("tk");
        cuentas().cerrarSesion();
        assertFalse(api.conSesion());
        assertEquals("tk", servidor.de("auth.logout").get(0).cabecera("x-secop-sesion"));
    }

    @Test
    void panelConLasCifrasDelServidor() {
        String fila = "{\"id\":%d,\"score\":%d,\"notificado\":false,\"perfilId\":1,\"perfil\":\"P\",\"proceso\":{\"id\":\"%s\","
                + "\"entidad\":\"E%s\",\"objeto\":\"%s\",\"modalidad\":\"Mínima cuantía\",\"valor\":1000,\"departamento\":\"Antioquia\"}}";
        servidor.en("oportunidades.vigentes", "{\"filas\":[" + String.format(fila, 1, 60, "P1", "1", "software de nómina") + ","
                + String.format(fila, 2, 50, "P2", "2", "aseo de oficinas") + "],\"ocultasPorPlan\":7,\"ocultasPorUsuario\":2,"
                + "\"procesosOcultos\":[\"P9\"]}");
        OportunidadesService s = new ServiciosApi.Oportunidades(new CoincidenciaApi(api), new OtrosApi.ProcesoOcultoApi(api), api);
        Map<String, String> m = new HashMap<>();
        m.put("q", "software");
        OportunidadesService.Panel panel = s.panel(7, Plan.GRATIS, Filtros.leer(m));

        assertEquals(7, panel.ocultasPorPlan(), "el límite del plan lo calcula el servidor");
        assertEquals(2, panel.ocultasPorUsuario());
        assertEquals(2, panel.total());
        assertEquals(1, panel.oportunidades().size(), "los filtros de la pantalla siguen en la app");
        assertEquals("P1", panel.oportunidades().get(0).proceso().id());
        assertFalse(servidor.de("oportunidades.vigentes").get(0).json().path("incluirOcultas").asBoolean());
    }

    @Test
    void subirDocumentoEnTresPasos(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("cedula.pdf");
        Files.writeString(pdf, "%PDF-1.4 prueba");
        servidor.en("documentos.pedirSubida", "{\"subidaId\":\"abc\",\"url\":\"" + servidor.urlStorage("7/abc.pdf") + "\","
                + "\"metodo\":\"PUT\",\"contentType\":\"application/pdf\"}");
        servidor.en("storage", "{}");
        servidor.en("documentos.confirmarSubida", "{\"id\":5,\"usuarioId\":7,\"tipo\":\"CEDULA\",\"nombreArchivo\":\"cedula.pdf\","
                + "\"tamano\":15,\"vence\":null,\"subidoAt\":\"2026-11-05T15:00:00Z\"}");
        DocumentosService docs = new ServiciosApi.Documentos(new OtrosApi.ProponenteApi(api), new OtrosApi.DocumentoApi(api), api);

        var d = docs.subir(7, TipoDocumento.CEDULA, pdf, LocalDate.of(2027, 1, 1));
        assertEquals(5, d.id());
        assertEquals(TipoDocumento.CEDULA, d.tipo());
        var pedir = servidor.de("documentos.pedirSubida").get(0).json();
        assertEquals("CEDULA", pedir.path("tipo").asText());
        assertEquals(15, pedir.path("tamano").asInt());
        assertEquals("2027-01-01", pedir.path("vence").asText());
        var put = servidor.de("storage").get(0);
        assertEquals("PUT", put.metodo());
        assertArrayEquals(Files.readAllBytes(pdf), put.cuerpo());
        assertEquals("abc", servidor.de("documentos.confirmarSubida").get(0).json().path("subidaId").asText());

        // Un archivo renombrado no sale del computador.
        Path falso = dir.resolve("falso.pdf");
        Files.writeString(falso, "MZ no es pdf");
        assertThrows(DocumentosService.ErrorArchivo.class, () -> docs.subir(7, TipoDocumento.RUT, falso, null));
        assertEquals(1, servidor.de("documentos.pedirSubida").size());
    }

    @Test
    void etapasLasValidaElServidor() {
        String post = "{\"postulacion\":{\"id\":3,\"usuarioId\":7,\"procesoId\":\"P1\",\"modalidad\":\"Mínima cuantía\",\"valor\":1000,"
                + "\"revisado\":true,\"pliegoLeido\":false,\"requisitosExtra\":[],\"desistida\":false},\"avance\":{}}";
        servidor.en("postulaciones.actualizar", p -> p.json().path("accion").asText().equals("propuesta")
                ? Respuesta.error(400, "invalido", "Primero completa la etapa 3: Leer la invitación o el pliego.")
                : Respuesta.ok(post));
        servidor.en("proponente.obtener", "{\"registradoSecop\":true,\"tieneRup\":false,\"sigepActualizada\":false}");
        servidor.en("documentos.listar", "{\"documentos\":[]}");
        DocumentosService docs = new ServiciosApi.Documentos(new OtrosApi.ProponenteApi(api), new OtrosApi.DocumentoApi(api), api);
        PostulacionesService s = new ServiciosApi.Postulaciones(new OtrosApi.PostulacionApi(api), null, docs, api);

        var d = s.marcarRevisado(3, 7, true);
        assertTrue(d.postulacion().revisado());
        assertNotNull(d.avance());
        var enviado = servidor.de("postulaciones.actualizar").get(0).json();
        assertEquals(3, enviado.path("id").asInt());
        assertEquals("revisar", enviado.path("accion").asText());
        assertTrue(enviado.path("valor").asBoolean());

        PostulacionesService.ErrorPostulacion e = assertThrows(PostulacionesService.ErrorPostulacion.class,
                () -> s.guardarPropuesta(3, 7, 900L));
        assertTrue(e.getMessage().contains("etapa 3"));
    }

    @Test
    void eligeModoApiOCaeALaBaseLocal() {
        servidor.en("estado.datos", "{\"procesos\":1,\"ultimoIngreso\":null}");
        Properties p = new Properties();
        p.setProperty("bd.modo", "api");
        p.setProperty("api.url", servidor.url());
        p.setProperty("api.clave", "sb_publishable_prueba");
        Servicios s = new Servicios(p);
        assertNotNull(s.api);
        assertNull(s.avisoConexion);
        assertInstanceOf(CuentaServiceApi.class, s.cuentas);
        assertInstanceOf(ServiciosApi.Postulaciones.class, s.postulaciones);
        assertTrue(s.enLaNube());
        assertTrue(s.descripcionDatos().startsWith("Supabase por HTTPS"));

        // Clave mala: base local H2 y aviso en la pantalla.
        servidor.en("estado.datos", x -> Respuesta.error(403, "prohibido", "Falta la clave de la aplicación."));
        p.setProperty("bd.local.url", "jdbc:h2:mem:respaldo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        try {
            Servicios local = new Servicios(p);
            assertNull(local.api);
            assertTrue(local.avisoConexion.contains("api.clave"), local.avisoConexion);
            assertFalse(local.cuentas instanceof CuentaServiceApi);
        } finally {
            BaseDatos.cerrar();
        }
    }
}
