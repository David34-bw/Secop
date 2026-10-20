package co.secopradar.dominio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Modalidades de contratación y sus explicaciones en lenguaje simple. */
public final class Modalidades {

    /** Modalidades con ofertas (competitivas) que descarga la ingesta. */
    public static final List<String> COMPETITIVAS = List.of(
            "Mínima cuantía",
            "Concurso de méritos abierto",
            "Licitación pública",
            "Licitación pública Obra Publica",
            "Selección Abreviada de Menor Cuantía",
            // Variante sin la etapa de manifestación de interés; así viene escrita en la fuente.
            "Seleccion Abreviada Menor Cuantia Sin Manifestacion Interes",
            "Selección abreviada subasta inversa",
            "Contratación régimen especial (con ofertas)",
            "Solicitud de información a los Proveedores");

    /** Las que se ofrecen como filtro al crear un perfil. */
    public static final List<String> PARA_PERFIL = List.of(
            "Mínima cuantía",
            "Concurso de méritos abierto",
            "Licitación pública",
            "Selección Abreviada de Menor Cuantía",
            "Seleccion Abreviada Menor Cuantia Sin Manifestacion Interes",
            "Selección abreviada subasta inversa");

    public static final Map<String, String> DESCRIPCION = new LinkedHashMap<>();

    static {
        DESCRIPCION.put("Mínima cuantía", "contrato pequeño, proceso rápido");
        DESCRIPCION.put("Selección Abreviada de Menor Cuantía", "contrato mediano, con menos trámites");
        DESCRIPCION.put("Seleccion Abreviada Menor Cuantia Sin Manifestacion Interes",
                "contrato mediano; se presenta la oferta directamente, sin anunciar antes el interés");
        DESCRIPCION.put("Selección abreviada subasta inversa", "los proveedores compiten bajando el precio");
        DESCRIPCION.put("Concurso de méritos abierto", "se elige por experiencia y capacidad técnica");
        DESCRIPCION.put("Licitación pública", "proceso grande y formal, abierto a cualquiera");
        DESCRIPCION.put("Licitación pública Obra Publica", "igual que licitación pública, pero para obras de construcción");
        DESCRIPCION.put("Contratación régimen especial (con ofertas)", "la entidad aplica sus propias reglas de contratación");
        DESCRIPCION.put("Solicitud de información a los Proveedores", "la entidad está indagando el mercado, aún no es un contrato");
    }

    public static final String AVISO_TEMPRANO_LABEL = "Aviso temprano — aún no es contrato";

    private Modalidades() {
    }

    public static String label(String m) {
        String d = DESCRIPCION.get(m);
        return d != null ? m + " — " + d : m;
    }

    /**
     * "Solicitud de información a los Proveedores" es un sondeo de mercado,
     * no un contrato abierto a ofertas: hay que distinguirla.
     */
    public static boolean esAvisoTemprano(String m) {
        return "Solicitud de información a los Proveedores".equals(m);
    }
}
