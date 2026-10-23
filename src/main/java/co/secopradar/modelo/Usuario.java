package co.secopradar.modelo;

import java.time.OffsetDateTime;

/** Fila de users con lo necesario para la app y el cálculo del plan. */
public record Usuario(
        Integer id,
        String email,
        String nombre,
        String rol,
        String plan,
        OffsetDateTime pruebaHasta,
        OffsetDateTime graciaHasta,
        String prepagoPlan,
        OffsetDateTime prepagoHasta,
        boolean alertasActivas,
        OffsetDateTime createdAt) {

    public boolean esAdmin() {
        return "admin".equals(rol);
    }
}
