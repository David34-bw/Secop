package co.secopradar.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Rangos de valor de los procesos. Los cortes (10M/30M/80M/200M/500M)
 * dejan cada rango con un volumen comparable de procesos.
 */
public record RangoValor(String id, long min, long max) {

    public static final List<RangoValor> TODOS = List.of(
            new RangoValor("menos_10m", 0, 10_000_000L),
            new RangoValor("10_30m", 10_000_000L, 30_000_000L),
            new RangoValor("30_80m", 30_000_000L, 80_000_000L),
            new RangoValor("80_200m", 80_000_000L, 200_000_000L),
            new RangoValor("200_500m", 200_000_000L, 500_000_000L),
            new RangoValor("mas_500m", 500_000_000L, Long.MAX_VALUE));

    private static final String PREFIJO_OTRO = "otro:";

    /** Acepta los rangos fijos y los escritos por la persona ("otro:MIN-MAX", MAX vacío = sin tope). */
    public static Optional<RangoValor> porId(String id) {
        if (esPersonalizado(id)) {
            String[] partes = id.substring(PREFIJO_OTRO.length()).split("-", -1);
            if (partes.length != 2) return Optional.empty();
            try {
                return personalizado(Long.parseLong(partes[0]), partes[1].isEmpty() ? null : Long.parseLong(partes[1]));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return TODOS.stream().filter(r -> r.id.equals(id)).findFirst();
    }

    public static boolean esPersonalizado(String id) {
        return id != null && id.startsWith(PREFIJO_OTRO);
    }

    /** Rango propio: mínimo >= 0 y máximo (opcional) mayor que el mínimo. */
    public static Optional<RangoValor> personalizado(long min, Long max) {
        if (min < 0 || (max != null && max <= min) || (max == null && min == 0)) return Optional.empty();
        return Optional.of(new RangoValor(PREFIJO_OTRO + min + "-" + (max == null ? "" : max),
                min, max == null ? Long.MAX_VALUE : max));
    }

    public boolean contiene(long valor) {
        return valor >= min && valor < max;
    }
}
