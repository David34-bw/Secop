package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Perfil;
import co.secopradar.modelo.Proceso;
import co.secopradar.modelo.RangoValor;
import co.secopradar.modelo.util.Texto;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/**
 * Puntaje de un proceso frente a un perfil (port de src/lib/matching.js).
 * Devuelve null si el proceso no cumple los filtros duros o no tiene
 * ninguna señal temática.
 */
public final class Matching {

    /**
     * Puntos por nivel de coincidencia UNSPSC. Un código de proceso tiene 8
     * dígitos: segmento (2), familia (4), clase (6) y producto (8).
     */
    private static final Map<Integer, Integer> PUNTOS_UNSPSC = Map.of(6, 60, 4, 45, 2, 25);

    public static final Pattern CODIGO_UNSPSC_VALIDO = Pattern.compile("^\\d{2}(\\d{2}){0,2}$");
    private static final Pattern OCHO_DIGITOS = Pattern.compile("^\\d{8}$");

    /** El nombre resume el proceso; la descripción trae más ruido. */
    private static final int PESO_NOMBRE = 25;
    private static final int PESO_DESCRIPCION = 15;
    private static final int PESO_ENTIDAD = 10;

    /**
     * Exclusión especial: con "compras de bienes" en palabras_excluir se
     * quitan los procesos cuyo nombre empieza por suministro, compra o
     * adquisición y que no dicen "servicio".
     */
    public static final String EXCLUIR_COMPRAS = "compras de bienes";
    private static final Set<String> INICIO_COMPRA = Set.of("suministro", "compra", "compraventa", "adquisicion", "adquirir");

    /** Textos tokenizados de un proceso. */
    public record Textos(List<String> nombre, List<String> descripcion, List<String> entidad) {
    }

    // El mismo proceso se evalúa contra muchos perfiles: se tokeniza una vez.
    private static final Map<Proceso, Textos> CACHE = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private Matching() {
    }

    public static int puntosCategoria(List<String> codigosPerfil, String categoriaProceso) {
        String codigo = categoriaProceso == null ? "" : categoriaProceso.replace("V1.", "");
        if (!OCHO_DIGITOS.matcher(codigo).matches()) return 0;
        int mejor = 0;
        if (codigosPerfil == null) return 0;
        for (String c : codigosPerfil) {
            int puntos = PUNTOS_UNSPSC.getOrDefault(c.length(), 0);
            if (puntos > mejor && codigo.startsWith(c)) mejor = puntos;
        }
        return mejor;
    }

    /**
     * Misma palabra aceptando plural simple: dato/datos, red/redes,
     * luz/luces. Comparar pares evita errores como estudiante -> "estudiant".
     */
    static boolean mismaPalabra(String a, String b) {
        if (a.equals(b)) return true;
        String corta = a.length() < b.length() ? a : b;
        String larga = a.length() < b.length() ? b : a;
        if (larga.equals(corta + "s") || larga.equals(corta + "es")) return true;
        return corta.endsWith("z") && larga.equals(corta.substring(0, corta.length() - 1) + "ces");
    }

    /** La frase debe aparecer completa y en orden, palabra por palabra. */
    public static boolean contieneFrase(List<String> texto, List<String> frase) {
        if (frase.isEmpty() || frase.size() > texto.size()) return false;
        for (int i = 0; i <= texto.size() - frase.size(); i++) {
            int j = 0;
            while (j < frase.size() && mismaPalabra(texto.get(i + j), frase.get(j))) j++;
            if (j == frase.size()) return true;
        }
        return false;
    }

    private static boolean esCompraDeBienes(Textos t) {
        String primera = !t.nombre().isEmpty() ? t.nombre().get(0)
                : !t.descripcion().isEmpty() ? t.descripcion().get(0) : null;
        if (primera == null || !INICIO_COMPRA.contains(primera)) return false;
        return !diceServicio(t.nombre()) && !diceServicio(t.descripcion());
    }

    private static boolean diceServicio(List<String> palabras) {
        return palabras.stream().anyMatch(w -> mismaPalabra(w, "servicio"));
    }

    public static Textos textosDe(Proceso p) {
        return CACHE.computeIfAbsent(p, k -> new Textos(
                Texto.palabras(k.nombre()), Texto.palabras(k.objeto()), Texto.palabras(k.entidad())));
    }

    /** Puntos de la mejor ubicación de la frase en nombre o descripción (0 si no aparece). */
    public static int puntosFrase(Textos t, List<String> frase) {
        if (contieneFrase(t.nombre(), frase)) return PESO_NOMBRE;
        if (contieneFrase(t.descripcion(), frase)) return PESO_DESCRIPCION;
        return 0;
    }

    private static boolean vacia(List<String> l) {
        return l == null || l.isEmpty();
    }

    /** Valores de las listas del formulario (normalizados): esos se comparan exactos. */
    private static final Set<String> VALORES_DE_CATALOGO = java.util.stream.Stream.of(
                    co.secopradar.modelo.Catalogo.DEPARTAMENTOS, co.secopradar.modelo.Catalogo.TIPOS_CONTRATO,
                    co.secopradar.modelo.Catalogo.TIPOS_ENTIDAD, co.secopradar.modelo.Modalidades.COMPETITIVAS,
                    co.secopradar.modelo.Region.TODAS.stream().flatMap(r -> r.departamentos().stream()).toList())
            .flatMap(List::stream).map(Texto::normalizar).collect(java.util.stream.Collectors.toUnmodifiableSet());

    /**
     * El valor del proceso está en la lista del perfil. Las opciones de la
     * lista se comparan exactas (sin tildes ni mayúsculas): "Santander" no
     * trae "Norte de Santander". Lo escrito en "Otro" basta con que sea una
     * frase del valor: "bogota" encuentra "Distrito Capital de Bogotá".
     */
    static boolean incluye(List<String> lista, String valor) {
        if (valor == null) return false;
        if (lista.contains(valor)) return true;
        String normalizado = Texto.normalizar(valor);
        List<String> palabrasValor = Texto.palabras(valor);
        for (String elegido : lista) {
            String e = Texto.normalizar(elegido);
            if (e.isEmpty()) continue;
            if (e.equals(normalizado)) return true;
            if (!VALORES_DE_CATALOGO.contains(e) && contieneFrase(palabrasValor, Texto.palabras(elegido))) return true;
        }
        return false;
    }

    public static Integer calcularScore(Perfil perfil, Proceso proceso) {
        return calcularScore(perfil, proceso, Instant.now());
    }

    public static Integer calcularScore(Perfil perfil, Proceso proceso, Instant ahora) {
        Textos textos = textosDe(proceso);
        Double dias = proceso.fechaCierre() == null ? null
                : Duration.between(ahora, proceso.fechaCierre().toInstant()).toMillis() / 86_400_000.0;

        // ── Filtros duros: si no pasan, el proceso queda descartado ──
        // Sin tildes ni mayúsculas: los valores escritos en "Otro" ("bogota",
        // "valle") también encuentran lo que trae SECOP.
        if (!vacia(perfil.departamentos()) && !incluye(perfil.departamentos(), proceso.departamento())) return null;
        if (!vacia(perfil.ciudades()) && !incluye(perfil.ciudades(), proceso.ciudad())) return null;
        if (!vacia(perfil.modalidades()) && !incluye(perfil.modalidades(), proceso.modalidad())) return null;
        if (!vacia(perfil.tiposContrato()) && !incluye(perfil.tiposContrato(), proceso.tipoContrato())) return null;
        if (!vacia(perfil.tiposEntidad()) && !incluye(perfil.tiposEntidad(), proceso.ordenEntidad())) return null;

        // Procesos en $0 o sin informar nunca se descartan por rango de valor.
        long valor = proceso.valor() == null ? 0 : proceso.valor();
        if (!vacia(perfil.rangosValor()) && valor > 0) {
            boolean enRango = perfil.rangosValor().stream()
                    .anyMatch(id -> RangoValor.porId(id).map(r -> r.contiene(valor)).orElse(false));
            if (!enRango) return null;
        }

        if (perfil.diasMinRestantes() != null && perfil.diasMinRestantes() > 0 && dias != null) {
            if (dias < perfil.diasMinRestantes()) return null;
        }

        // Solo nombre y descripción: excluir "salud" no debe quitar un
        // proceso de software de la Secretaría de Salud.
        if (perfil.palabrasExcluir() != null) {
            for (String palabra : perfil.palabrasExcluir()) {
                if (Texto.normalizar(palabra).equals(EXCLUIR_COMPRAS)) {
                    if (esCompraDeBienes(textos)) return null;
                } else if (puntosFrase(textos, Texto.palabras(palabra)) > 0) {
                    return null;
                }
            }
        }

        // ── Puntaje: al menos una señal temática debe coincidir ──
        int score = puntosCategoria(perfil.categorias(), proceso.categoria());

        int puntosTema = 0;
        int puntosEntidad = 0;
        if (perfil.palabrasClave() != null) {
            for (String palabra : perfil.palabrasClave()) {
                List<String> frase = Texto.palabras(palabra);
                int puntos = puntosFrase(textos, frase);
                if (puntos > 0) puntosTema += puntos;
                else if (contieneFrase(textos.entidad(), frase)) puntosEntidad += PESO_ENTIDAD;
            }
        }
        score += puntosTema;
        if (puntosTema > 0) score += puntosEntidad;

        // Sin señal temática no hay coincidencia, salvo que el perfil no
        // tenga ni categorías ni palabras: ahí mandan los filtros duros.
        boolean sinCriterio = vacia(perfil.categorias()) && vacia(perfil.palabrasClave());
        if (score == 0 && !sinCriterio) return null;
        if (sinCriterio) score = 10;

        // Bonus: procesos que todavía se pueden presentar
        if ("Abierto".equals(proceso.estadoApertura())) score += 15;
        // Bonus: cierra pronto (más urgente de avisar)
        if (dias != null && dias > 0 && dias <= 7) score += 10;

        return score;
    }
}
