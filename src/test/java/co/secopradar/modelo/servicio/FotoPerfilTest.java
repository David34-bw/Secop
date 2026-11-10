package co.secopradar.modelo.servicio;

import co.secopradar.modelo.api.ClienteApi;
import co.secopradar.modelo.api.OtrosApi;
import co.secopradar.modelo.api.ServidorFalso;
import co.secopradar.modelo.dao.BaseDatos;
import co.secopradar.modelo.dao.FotoDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Foto de perfil: preparación de la imagen, H2 y API (servidor simulado). */
class FotoPerfilTest {

    private static int ana;
    private static int beto;

    @BeforeAll
    static void base() {
        BaseDatos.iniciar("jdbc:h2:mem:fotos;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "sa", "");
        ana = BaseDatos.insertar("INSERT INTO users (email, password_hash, nombre) VALUES ('ana@correo.co', 'x', 'Ana')");
        beto = BaseDatos.insertar("INSERT INTO users (email, password_hash, nombre) VALUES ('beto@correo.co', 'x', 'Beto')");
    }

    @AfterAll
    static void cerrar() {
        BaseDatos.cerrar();
    }

    private static byte[] imagen(int ancho, int alto, String formato, boolean transparente) throws IOException {
        BufferedImage img = new BufferedImage(ancho, alto, transparente ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, ancho / 2, alto);
        g.dispose();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(img, formato, o);
        return o.toByteArray();
    }

    private static BufferedImage leer(byte[] b) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(b));
    }

    @Test
    void unaFotoGrandeQuedaCuadradaDe256EnJpeg() throws Exception {
        byte[] r = FotoPerfilService.preparar(imagen(1600, 900, "jpg", false));
        assertTrue(FotoPerfilService.esJpeg(r));
        BufferedImage img = leer(r);
        assertEquals(256, img.getWidth());
        assertEquals(256, img.getHeight());
        assertTrue(r.length < 60_000, "pesa poco: " + r.length);
    }

    @Test
    void unPngPequenoYTransparenteNoSeAgranda() throws Exception {
        BufferedImage img = leer(FotoPerfilService.preparar(imagen(120, 80, "png", true)));
        assertEquals(80, img.getWidth());
        assertEquals(80, img.getHeight());
    }

    @Test
    void rechazaArchivosQueNoSonImagenes() {
        assertThrows(FotoPerfilService.ErrorFoto.class, () -> FotoPerfilService.preparar(new byte[0]));
        assertThrows(FotoPerfilService.ErrorFoto.class, () -> FotoPerfilService.preparar("%PDF-1.7 hola".getBytes()));
        // Encabezado de PNG pero contenido dañado.
        byte[] falso = Arrays.copyOf(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, 64);
        assertThrows(FotoPerfilService.ErrorFoto.class, () -> FotoPerfilService.preparar(falso));
    }

    @Test
    void rechazaUnArchivoDeMasDe10Mb() throws Exception {
        Path f = Files.createTempFile("grande", ".jpg");
        try {
            Files.write(f, new byte[(int) FotoPerfilService.MAX_ORIGINAL + 1]);
            FotoPerfilService s = new FotoPerfilService(new FotoDAO());
            assertThrows(FotoPerfilService.ErrorFoto.class, () -> s.cambiar(ana, f));
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void guardarCambiarYQuitarEnH2SinTocarALosDemas() throws Exception {
        FotoPerfilService s = new FotoPerfilService(new FotoDAO());
        Path f = Files.createTempFile("foto", ".png");
        try {
            Files.write(f, imagen(500, 500, "png", false));
            s.cambiar(ana, f);
            byte[] primera = s.de(ana).orElseThrow();
            assertEquals(256, leer(primera).getWidth());
            assertTrue(s.de(beto).isEmpty());

            Files.write(f, imagen(300, 600, "jpg", false));
            s.cambiar(ana, f); // reemplaza, no duplica
            assertEquals(1L, BaseDatos.numero("SELECT COUNT(*) FROM fotos_perfil WHERE usuario_id = ?", ana));
            assertTrue(!Arrays.equals(primera, s.de(ana).orElseThrow()));

            s.quitar(ana);
            assertTrue(s.de(ana).isEmpty());
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void porLaApiViajaEnBase64YSeVuelveAPedirDespuesDeCambiarla() throws Exception {
        AtomicReference<String> guardada = new AtomicReference<>();
        try (ServidorFalso srv = new ServidorFalso()) {
            srv.en("cuenta.foto", p -> ServidorFalso.Respuesta.ok(guardada.get() == null
                    ? "{\"tipo\":null,\"base64\":null}" : "{\"tipo\":\"image/jpeg\",\"base64\":\"" + guardada.get() + "\"}"));
            srv.en("cuenta.cambiarFoto", p -> {
                guardada.set(p.json().path("base64").asText());
                return ServidorFalso.Respuesta.ok("{\"ok\":true}");
            });
            ClienteApi api = new ClienteApi(srv.url(), "sb_publishable_prueba");
            api.usarToken("t");
            FotoPerfilService s = new FotoPerfilService(new OtrosApi.FotoApi(api));

            assertTrue(s.de(7).isEmpty());
            byte[] jpeg = FotoPerfilService.preparar(imagen(400, 400, "png", false));
            new OtrosApi.FotoApi(api).guardar(7, jpeg, "image/jpeg");
            assertArrayEquals(jpeg, Base64.getDecoder().decode(guardada.get()));
            assertArrayEquals(jpeg, s.de(7).orElseThrow(), "después de cambiarla no se usa la memoria vieja");
            s.de(7);
            assertEquals(2, srv.de("cuenta.foto").size(), "la segunda lectura sale de la memoria");
        }
    }
}
