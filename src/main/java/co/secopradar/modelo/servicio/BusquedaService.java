package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Oportunidad;
import co.secopradar.modelo.Catalogo;
import co.secopradar.modelo.Perfil;
import co.secopradar.modelo.Proceso;
import co.secopradar.modelo.dao.ProcesoDAO;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Búsqueda pública, sin sesión: no guarda nada (port de api/buscar).
 * Dos formas de buscar:
 * - texto libre: lo que la persona escribió ("frase", OR, -excluir);
 * - profesión: las categorías y frases del diccionario, con el mismo
 *   puntaje del cruce de perfiles.
 */
public class BusquedaService {

    public static final int TOPE = 10;

    public record Resultado(int total, List<Oportunidad> oportunidades) {
    }

    private final ProcesoDAO procesos;

    public BusquedaService(ProcesoDAO procesos) {
        this.procesos = procesos;
    }

    public Resultado porTexto(String texto) {
        Consulta consulta = Consulta.parsear(texto);
        if (!consulta.tienePositivos()) return new Resultado(0, List.of());
        Instant ahora = Instant.now();
        List<Oportunidad> encontradas = new ArrayList<>();
        for (Proceso p : procesos.vigentes()) {
            int puntos = consulta.puntos(Matching.textosDe(p));
            if (puntos <= 0) continue;
            encontradas.add(new Oportunidad(0, puntos + bonus(p, ahora), null, false, p));
        }
        return ordenar(encontradas);
    }

    public Resultado porProfesion(Catalogo.Profesion prof) {
        Perfil borrador = Perfil.borrador(prof.categorias(), prof.palabrasClave(), prof.palabrasExcluir());
        Instant ahora = Instant.now();
        List<Oportunidad> encontradas = new ArrayList<>();
        for (Proceso p : procesos.vigentes()) {
            Integer score = Matching.calcularScore(borrador, p, ahora);
            if (score != null) encontradas.add(new Oportunidad(0, score, null, false, p));
        }
        return ordenar(encontradas);
    }

    /** Los mismos bonus que el cruce: abierto +15 y cierra en 7 días +10. */
    private static int bonus(Proceso p, Instant ahora) {
        int b = "Abierto".equals(p.estadoApertura()) ? 15 : 0;
        OffsetDateTime cierre = p.fechaCierre();
        if (cierre != null) {
            double dias = Duration.between(ahora, cierre.toInstant()).toMillis() / 86_400_000.0;
            if (dias > 0 && dias <= 7) b += 10;
        }
        return b;
    }

    private static Resultado ordenar(List<Oportunidad> lista) {
        lista.sort(Comparator.comparingInt((Oportunidad o) -> -o.score())
                .thenComparing(o -> o.proceso().fechaCierre(), Comparator.nullsLast(Comparator.naturalOrder())));
        return new Resultado(lista.size(), lista.stream().limit(TOPE).toList());
    }
}
