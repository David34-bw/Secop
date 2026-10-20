package co.secopradar.servicio;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Términos y política de datos (resources/legal/*.md) convertidos a HTML. */
@Service
public class LegalService {

    private static final String FECHA_PUBLICACION = "19 de octubre de 2026";

    private final String appUrl;
    private final String correo;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public LegalService(@Value("${secop.app-url}") String appUrl, @Value("${secop.correo-contacto}") String correo) {
        this.appUrl = appUrl.replaceAll("/$", "");
        this.correo = correo;
    }

    public String html(String documento) {
        return cache.computeIfAbsent(documento, this::generar);
    }

    private String generar(String documento) {
        try {
            String md = new ClassPathResource("legal/" + documento + ".md").getContentAsString(StandardCharsets.UTF_8)
                    .replace("[NOMBRE_MARCA]", "SECOP RADAR")
                    .replace("[URL_SITIO]", appUrl)
                    .replace("[FECHA_PUBLICACION]", FECHA_PUBLICACION)
                    .replace("[CORREO_CONTACTO]", correo);
            // El Markdown es nuestro: se permite su HTML tal cual.
            return HtmlRenderer.builder().build().render(Parser.builder().build().parse(md));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
