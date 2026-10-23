package co.secopradar.modelo.servicio;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Términos y política de datos (resources/legal/*.md) convertidos en
 * bloques de texto que la vista pinta como títulos, párrafos y viñetas.
 */
public class LegalService {

    private static final String FECHA_PUBLICACION = "19 de octubre de 2026";

    public enum TipoBloque { TITULO, SUBTITULO, PARRAFO, VINETA }

    public record Bloque(TipoBloque tipo, String texto) {
    }

    private final String correoContacto;

    public LegalService(String correoContacto) {
        this.correoContacto = correoContacto;
    }

    public List<Bloque> documento(String nombre) {
        String md;
        try (InputStream in = LegalService.class.getResourceAsStream("/legal/" + nombre + ".md")) {
            if (in == null) throw new IOException("No existe el documento " + nombre);
            md = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("[NOMBRE_MARCA]", "SECOP RADAR")
                    .replace("[URL_SITIO]", "la aplicación de escritorio SECOP RADAR")
                    .replace("[FECHA_PUBLICACION]", FECHA_PUBLICACION)
                    .replace("[CORREO_CONTACTO]", correoContacto);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<Bloque> bloques = new ArrayList<>();
        StringBuilder parrafo = new StringBuilder();
        for (String linea : md.split("\\R")) {
            String t = linea.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("- ") || t.startsWith("* ")) {
                if (!parrafo.isEmpty()) {
                    bloques.add(new Bloque(TipoBloque.PARRAFO, limpiar(parrafo.toString())));
                    parrafo.setLength(0);
                }
            }
            if (t.isEmpty()) continue;
            if (t.startsWith("## ")) bloques.add(new Bloque(TipoBloque.SUBTITULO, limpiar(t.substring(3))));
            else if (t.startsWith("# ")) bloques.add(new Bloque(TipoBloque.TITULO, limpiar(t.substring(2))));
            else if (t.startsWith("#")) bloques.add(new Bloque(TipoBloque.SUBTITULO, limpiar(t.replaceFirst("^#+\\s*", ""))));
            else if (t.startsWith("- ") || t.startsWith("* ")) bloques.add(new Bloque(TipoBloque.VINETA, limpiar(t.substring(2))));
            else parrafo.append(parrafo.isEmpty() ? "" : " ").append(t);
        }
        if (!parrafo.isEmpty()) bloques.add(new Bloque(TipoBloque.PARRAFO, limpiar(parrafo.toString())));
        return bloques;
    }

    /** Quita las marcas de Markdown (negrillas, cursivas y enlaces). */
    private static String limpiar(String s) {
        return s.replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("(?<!\\w)_(.+?)_(?!\\w)", "$1")
                .replaceAll("\\[(.+?)]\\((.+?)\\)", "$1 ($2)");
    }
}
