package co.secopradar.modelo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lo que la persona va llenando en el formulario de un perfil nuevo,
 * antes de validarlo y guardarlo. El controlador solo copia aquí lo que
 * hay en la pantalla; las reglas están en PerfilesService.
 */
public class BorradorPerfil {

    /** Estado de un grupo obligatorio: opciones marcadas, "todos" y lo escrito en "otro". */
    public static class Eleccion {
        public final Set<String> marcadas = new LinkedHashSet<>();
        public boolean todos;
        public boolean otro;
        public String textoOtro = "";

        /** Lo escrito en "otro", varios separados por coma. */
        public List<String> valoresOtro() {
            if (!otro || textoOtro == null) return List.of();
            List<String> l = new ArrayList<>();
            for (String t : textoOtro.split(",")) if (!t.isBlank()) l.add(t.trim());
            return l;
        }
    }

    public String nombre = "";
    /** Id del catálogo, o null si eligió "Otra". */
    public String profesionId;
    public boolean profesionOtra;
    public String textoProfesionOtra = "";

    public final List<String> categorias = new ArrayList<>();
    public final List<String> palabrasClave = new ArrayList<>();
    public final List<String> palabrasExcluir = new ArrayList<>();
    public final List<String> ciudades = new ArrayList<>();
    public Integer diasMinRestantes;

    /** En "Otro rango": montos en pesos tal como los escribió (puede traer puntos). */
    public String rangoOtroMin = "";
    public String rangoOtroMax = "";

    public final Map<String, Eleccion> grupos = new LinkedHashMap<>();

    public BorradorPerfil() {
        for (GrupoOpciones g : GrupoOpciones.TODOS) grupos.put(g.campo(), new Eleccion());
    }

    public Eleccion grupo(String campo) {
        return grupos.get(campo);
    }
}
