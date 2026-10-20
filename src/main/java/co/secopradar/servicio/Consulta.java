package co.secopradar.servicio;

import co.secopradar.util.Texto;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Consulta de texto libre con la sintaxis de búsqueda web que usaba el
 * original (websearch_to_tsquery de PostgreSQL):
 * <ul>
 *   <li>palabras sueltas: deben aparecer todas;</li>
 *   <li>"frase exacta" entre comillas;</li>
 *   <li>OR entre dos términos: basta uno;</li>
 *   <li>-palabra o -"frase": excluye.</li>
 * </ul>
 * Se compara contra el nombre y la descripción del proceso con las mismas
 * reglas del cruce (palabra completa, plural simple, sin tildes).
 */
public final class Consulta {

    /** Palabras vacías del español que la búsqueda ignora (como el diccionario 'spanish'). */
    private static final Set<String> VACIAS = Set.of(
            "de", "la", "que", "el", "en", "y", "a", "los", "del", "se", "las", "por", "un", "para", "con", "no",
            "una", "su", "al", "lo", "como", "mas", "pero", "sus", "le", "ya", "o", "este", "si", "porque", "esta",
            "entre", "cuando", "muy", "sin", "sobre", "tambien", "me", "hasta", "hay", "donde", "quien", "desde",
            "todo", "nos", "durante", "todos", "uno", "les", "ni", "contra", "otros", "ese", "eso", "ante", "ellos",
            "e", "esto", "mi", "antes", "algunos", "unos", "otro", "otras", "otra", "tanto", "esa", "estos", "mucho",
            "nada", "muchos", "cual", "poco", "ella", "estar", "estas", "algunas", "algo", "u");

    /** Grupos que deben cumplirse todos; cada grupo es una lista de frases alternativas (OR). */
    private final List<List<List<String>>> grupos = new ArrayList<>();
    private final List<List<String>> excluir = new ArrayList<>();

    private Consulta() {
    }

    public static Consulta parsear(String texto) {
        Consulta c = new Consulta();
        if (texto == null) return c;
        String s = texto.trim();
        boolean siguienteEsOr = false;
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            boolean negado = false;
            if (ch == '-' && i + 1 < s.length() && !Character.isWhitespace(s.charAt(i + 1))) {
                negado = true;
                i++;
                ch = s.charAt(i);
            }
            String crudo;
            boolean entreComillas = ch == '"';
            if (entreComillas) {
                int fin = s.indexOf('"', i + 1);
                if (fin < 0) fin = s.length();
                crudo = s.substring(i + 1, fin);
                i = fin + 1;
            } else {
                int fin = i;
                while (fin < s.length() && !Character.isWhitespace(s.charAt(fin)) && s.charAt(fin) != '"') fin++;
                crudo = s.substring(i, fin);
                i = fin;
            }

            if (!entreComillas && !negado && crudo.equalsIgnoreCase("or")) {
                siguienteEsOr = !c.grupos.isEmpty();
                continue;
            }

            List<String> frase = Texto.palabras(crudo);
            // Una palabra suelta que es vacía ("de", "la") no cuenta.
            if (!entreComillas) frase = frase.stream().filter(w -> !VACIAS.contains(w)).toList();
            if (frase.isEmpty()) {
                siguienteEsOr = false;
                continue;
            }

            if (negado) {
                c.excluir.add(frase);
            } else if (siguienteEsOr) {
                c.grupos.get(c.grupos.size() - 1).add(frase);
            } else {
                List<List<String>> grupo = new ArrayList<>();
                grupo.add(frase);
                c.grupos.add(grupo);
            }
            siguienteEsOr = false;
        }
        return c;
    }

    /** Une frases con OR: la forma de buscar por profesión. */
    public static Consulta deFrases(List<String> frases, List<String> excluir) {
        Consulta c = new Consulta();
        List<List<String>> grupo = new ArrayList<>();
        for (String f : frases) {
            List<String> p = Texto.palabras(f);
            if (!p.isEmpty()) grupo.add(p);
        }
        if (!grupo.isEmpty()) c.grupos.add(grupo);
        for (String f : excluir) {
            List<String> p = Texto.palabras(f);
            if (!p.isEmpty()) c.excluir.add(p);
        }
        return c;
    }

    /** Sin términos positivos (solo palabras vacías, o nada): no filtra. */
    public boolean vacia() {
        return grupos.isEmpty() && excluir.isEmpty();
    }

    public boolean tienePositivos() {
        return !grupos.isEmpty();
    }

    public boolean excluye(Matching.Textos t) {
        for (List<String> f : excluir) {
            if (Matching.puntosFrase(t, f) > 0) return true;
        }
        return false;
    }

    /**
     * Puntos de relevancia (nombre 25, descripción 15 por cada grupo que
     * coincide) o -1 si no cumple la consulta.
     */
    public int puntos(Matching.Textos t) {
        if (excluye(t)) return -1;
        int total = 0;
        for (List<List<String>> grupo : grupos) {
            int mejor = 0;
            for (List<String> frase : grupo) mejor = Math.max(mejor, Matching.puntosFrase(t, frase));
            if (mejor == 0) return -1;
            total += mejor;
        }
        return total;
    }

    /** Para los filtros: basta con que tenga algún punto o que la consulta esté vacía. */
    public boolean coincide(Matching.Textos t) {
        return vacia() || puntos(t) >= 0;
    }
}
