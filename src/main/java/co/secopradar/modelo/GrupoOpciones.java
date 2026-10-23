package co.secopradar.modelo;

import co.secopradar.modelo.util.Formato;

import java.util.List;

/**
 * Un grupo obligatorio del formulario de perfiles. Hay que elegir al
 * menos una opción de la lista, la opción "todos" (no filtra) u "otro"
 * (texto libre, o un rango propio en el caso del valor).
 */
public record GrupoOpciones(String campo, String titulo, String ayuda, List<Opcion> opciones,
                            String etiquetaTodos, String etiquetaOtro, String placeholderOtro, String error) {

    /** valor = lo que se guarda; etiqueta = lo que se ve; descripcion = ayuda emergente (o null). */
    public record Opcion(String valor, String etiqueta, String descripcion) {
    }

    public static final String DEPARTAMENTOS = "departamentos";
    public static final String MODALIDADES = "modalidades";
    public static final String TIPOS_CONTRATO = "tipos_contrato";
    public static final String TIPOS_ENTIDAD = "tipos_entidad";
    public static final String RANGOS_VALOR = "rangos_valor";

    public boolean esRangoValor() {
        return RANGOS_VALOR.equals(campo);
    }

    public static final List<GrupoOpciones> TODOS = List.of(
            new GrupoOpciones(DEPARTAMENTOS, "Departamentos", "¿Dónde puedes trabajar?",
                    Catalogo.DEPARTAMENTOS.stream().map(d -> new Opcion(d, d, null)).toList(),
                    "Todo el país", "Otro", "Escribe el departamento (separa varios con coma)",
                    "Elige al menos un departamento, «Todo el país» u «Otro»"),
            new GrupoOpciones(MODALIDADES, "Modalidades", "Pasa el mouse sobre cada una para ver qué es.",
                    Modalidades.PARA_PERFIL.stream().map(m -> new Opcion(m, m, Modalidades.DESCRIPCION.get(m))).toList(),
                    "Todas las modalidades", "Otra", "Escribe la modalidad como aparece en SECOP",
                    "Elige al menos una modalidad, «Todas» u «Otra»"),
            new GrupoOpciones(TIPOS_CONTRATO, "Tipos de contrato", null,
                    Catalogo.TIPOS_CONTRATO.stream().map(t -> new Opcion(t, t, null)).toList(),
                    "Todos los tipos", "Otro", "Escribe el tipo de contrato (separa varios con coma)",
                    "Elige al menos un tipo de contrato, «Todos» u «Otro»"),
            new GrupoOpciones(TIPOS_ENTIDAD, "Tipo de entidad", null,
                    Catalogo.TIPOS_ENTIDAD.stream().map(t -> new Opcion(t, t, null)).toList(),
                    "Todas las entidades", "Otra", "Escribe el tipo de entidad",
                    "Elige al menos un tipo de entidad, «Todas» u «Otra»"),
            new GrupoOpciones(RANGOS_VALOR, "Valor del proceso", null,
                    RangoValor.TODOS.stream().map(r -> new Opcion(r.id(), Formato.labelRango(r), null)).toList(),
                    "Cualquier valor", "Otro rango", null,
                    "Elige al menos un rango de valor, «Cualquier valor» u «Otro rango»"));

    public static GrupoOpciones porCampo(String campo) {
        return TODOS.stream().filter(g -> g.campo.equals(campo)).findFirst().orElseThrow();
    }
}
