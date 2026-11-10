package co.secopradar.modelo.servicio;

import co.secopradar.modelo.repositorio.FotoRepositorio;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

/**
 * Foto de perfil: valida el archivo, lo recorta en cuadrado (al centro) y lo
 * reduce a 256x256 en JPEG antes de guardarlo. Así pesa unos 20 KB, sube y
 * carga rápido en cualquier modo (JDBC o API).
 */
public class FotoPerfilService {

    /** Lado de la foto guardada, en píxeles. */
    public static final int LADO = 256;
    /** Tope del archivo original que elige el usuario. */
    public static final long MAX_ORIGINAL = 10L * 1024 * 1024;
    /** Tope de ancho y alto del original (evita imágenes enormes que llenen la memoria). */
    static final int MAX_PIXELES_LADO = 12_000;

    /** Error que el usuario debe corregir (archivo inválido). */
    public static class ErrorFoto extends RuntimeException {
        public ErrorFoto(String mensaje) {
            super(mensaje);
        }
    }

    private final FotoRepositorio fotos;

    public FotoPerfilService(FotoRepositorio fotos) {
        this.fotos = fotos;
    }

    public Optional<byte[]> de(int usuarioId) {
        return fotos.de(usuarioId);
    }

    /** Lee el archivo, lo prepara y lo guarda (reemplaza la foto anterior). */
    public void cambiar(int usuarioId, Path archivo) {
        byte[] original;
        try {
            if (Files.size(archivo) > MAX_ORIGINAL) throw new ErrorFoto("La imagen pesa más de 10 MB.");
            original = Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new ErrorFoto("No se pudo leer el archivo: " + e.getMessage());
        }
        fotos.guardar(usuarioId, preparar(original), "image/jpeg");
    }

    public void quitar(int usuarioId) {
        fotos.quitar(usuarioId);
    }

    /** JPG o PNG -> JPEG cuadrado de LADO x LADO (o menor si el original es más pequeño). */
    public static byte[] preparar(byte[] original) {
        if (original == null || original.length == 0) throw new ErrorFoto("El archivo está vacío.");
        if (original.length > MAX_ORIGINAL) throw new ErrorFoto("La imagen pesa más de 10 MB.");
        if (!esJpeg(original) && !esPng(original)) throw new ErrorFoto("La foto debe ser una imagen JPG o PNG.");
        BufferedImage img = leer(original);

        int lado = Math.min(img.getWidth(), img.getHeight());
        BufferedImage cuadrada = img.getSubimage((img.getWidth() - lado) / 2, (img.getHeight() - lado) / 2, lado, lado);
        int destino = Math.min(LADO, lado);

        // Reducir a la mitad varias veces da mejor calidad que un solo salto grande.
        BufferedImage actual = cuadrada;
        int tam = lado;
        while (tam / 2 >= destino) {
            tam /= 2;
            actual = escalar(actual, tam);
        }
        if (tam != destino) actual = escalar(actual, destino);
        return jpeg(actual);
    }

    static boolean esJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff;
    }

    static boolean esPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    /** Revisa el tamaño en píxeles antes de decodificar, para no reservar memoria de más. */
    private static BufferedImage leer(byte[] datos) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(in);
            if (!lectores.hasNext()) throw new ErrorFoto("La imagen no se pudo leer.");
            ImageReader r = lectores.next();
            try {
                r.setInput(in, true, true);
                if (r.getWidth(0) > MAX_PIXELES_LADO || r.getHeight(0) > MAX_PIXELES_LADO) {
                    throw new ErrorFoto("La imagen es demasiado grande (máximo " + MAX_PIXELES_LADO + " píxeles por lado).");
                }
                BufferedImage img = r.read(0);
                if (img == null || img.getWidth() < 1 || img.getHeight() < 1) throw new ErrorFoto("La imagen no se pudo leer.");
                return img;
            } finally {
                r.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ErrorFoto f) throw f;
            throw new ErrorFoto("La imagen no se pudo leer (puede estar dañada).");
        }
    }

    /** Escala a tam x tam sobre fondo blanco (los PNG transparentes quedan con fondo blanco). */
    private static BufferedImage escalar(BufferedImage img, int tam) {
        BufferedImage r = new BufferedImage(tam, tam, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = r.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, tam, tam);
            g.drawImage(img, 0, 0, tam, tam, null);
        } finally {
            g.dispose();
        }
        return r;
    }

    private static byte[] jpeg(BufferedImage img) {
        BufferedImage rgb = img.getType() == BufferedImage.TYPE_INT_RGB ? img : escalar(img, img.getWidth());
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(salida)) {
            w.setOutput(out);
            ImageWriteParam p = w.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(0.88f);
            w.write(null, new IIOImage(rgb, null, null), p);
        } catch (IOException e) {
            throw new ErrorFoto("No se pudo preparar la foto: " + e.getMessage());
        } finally {
            w.dispose();
        }
        return salida.toByteArray();
    }
}
