package co.secopradar.dominio;

import java.time.OffsetDateTime;
import java.util.List;

/** Perfil de búsqueda de un usuario: filtros duros y señales temáticas. */
public record Perfil(
        Integer id,
        Integer userId,
        String nombre,
        List<String> categorias,
        List<String> palabrasClave,
        List<String> palabrasExcluir,
        List<String> departamentos,
        List<String> ciudades,
        List<String> modalidades,
        List<String> tiposContrato,
        List<String> tiposEntidad,
        List<String> rangosValor,
        Integer diasMinRestantes,
        String profesionId,
        boolean activo,
        boolean desactivadoPorPlan,
        OffsetDateTime createdAt) {

    /** Perfil "borrador" sin guardar, para la vista previa y la búsqueda pública. */
    public static Perfil borrador(List<String> categorias, List<String> palabrasClave, List<String> palabrasExcluir) {
        return new Perfil(null, null, "", categorias, palabrasClave, palabrasExcluir,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, true, false, null);
    }
}
