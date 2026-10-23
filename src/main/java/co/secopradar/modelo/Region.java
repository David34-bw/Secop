package co.secopradar.modelo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Regiones naturales de Colombia con los departamentos tal como los
 * nombra SECOP. Bogotá D.C. va en Andina y San Andrés en Caribe.
 */
public record Region(String id, String nombre, List<String> departamentos) {

    public static final List<Region> TODAS = List.of(
            new Region("andina", "Andina", List.of(
                    "Antioquia", "Boyacá", "Caldas", "Cundinamarca", "Distrito Capital de Bogotá", "Huila",
                    "Norte de Santander", "Quindío", "Risaralda", "Santander", "Tolima")),
            new Region("caribe", "Caribe", List.of(
                    "Atlántico", "Bolívar", "Cesar", "Córdoba", "La Guajira", "Magdalena", "Sucre",
                    "San Andrés, Providencia y Santa Catalina")),
            new Region("pacifico", "Pacífico", List.of("Chocó", "Valle del Cauca", "Cauca", "Nariño")),
            new Region("orinoquia", "Orinoquía", List.of("Arauca", "Casanare", "Meta", "Vichada")),
            new Region("amazonia", "Amazonía", List.of("Amazonas", "Caquetá", "Guainía", "Guaviare", "Putumayo", "Vaupés")));

    private static final Map<String, String> NOMBRES_CORTOS = Map.of(
            "Distrito Capital de Bogotá", "Bogotá D.C.",
            "San Andrés, Providencia y Santa Catalina", "San Andrés");

    public static Optional<Region> porId(String id) {
        return TODAS.stream().filter(r -> r.id.equals(id)).findFirst();
    }

    public static Optional<Region> de(String departamento) {
        return TODAS.stream().filter(r -> r.departamentos.contains(departamento)).findFirst();
    }

    public static String nombreCorto(String departamento) {
        return NOMBRES_CORTOS.getOrDefault(departamento, departamento);
    }
}
