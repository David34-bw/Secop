package co.secopradar.dominio;

import java.time.OffsetDateTime;

/** Un proceso de contratación de SECOP II (sin el JSON crudo). */
public record Proceso(
        String id,
        String entidad,
        String nombre,
        String objeto,
        String modalidad,
        String categoria,
        String tipoContrato,
        Long valor,
        String departamento,
        String ciudad,
        String estadoApertura,
        OffsetDateTime fechaPublicacion,
        OffsetDateTime fechaCierre,
        String url,
        String ordenEntidad) {
}
