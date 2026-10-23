package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Perfil;
import co.secopradar.modelo.Proceso;
import co.secopradar.modelo.dao.CoincidenciaDAO;
import co.secopradar.modelo.dao.LogDAO;
import co.secopradar.modelo.dao.PerfilDAO;
import co.secopradar.modelo.dao.ProcesoDAO;
import co.secopradar.modelo.util.Registro;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cruce de perfiles contra procesos vigentes (port de worker/coincidencias.mjs). */
public class CoincidenciasService {

    private static final Registro log = Registro.de(CoincidenciasService.class);

    public record Resultado(int perfiles, int procesos, int nuevas, int quitadas) {
    }

    private final PerfilDAO perfiles;
    private final ProcesoDAO procesos;
    private final CoincidenciaDAO coincidencias;
    private final LogDAO logs;

    public CoincidenciasService(PerfilDAO perfiles, ProcesoDAO procesos,
                                CoincidenciaDAO coincidencias, LogDAO logs) {
        this.perfiles = perfiles;
        this.procesos = procesos;
        this.coincidencias = coincidencias;
        this.logs = logs;
    }

    public Resultado ejecutar() {
        int totalNuevas = 0, totalQuitadas = 0, nPerfiles = 0, nProcesos = 0;
        try {
            log.info("[coincidencias] Iniciando cruce");
            List<Perfil> activos = perfiles.activos();
            if (activos.isEmpty()) {
                log.info("[coincidencias] No hay perfiles activos. Crea uno en /perfiles");
                logs.coincidencias(0, 0, 0, 0, true, null);
                return new Resultado(0, 0, 0, 0);
            }
            List<Proceso> vigentes = procesos.vigentes();
            nPerfiles = activos.size();
            nProcesos = vigentes.size();
            log.info("[coincidencias] Perfiles activos: {} | procesos vigentes: {}", nPerfiles, nProcesos);

            Instant ahora = Instant.now();
            for (Perfil perfil : activos) {
                Map<String, Integer> calculadas = calcular(perfil, vigentes, ahora);
                Map<String, Integer> guardadas = coincidencias.dePerfil(perfil.id());

                // El puntaje se recalcula en cada corrida: si cambian las
                // reglas o el perfil, se actualiza sin perder "notificado".
                Map<String, Integer> nuevas = new HashMap<>();
                Map<String, Integer> cambiadas = new HashMap<>();
                calculadas.forEach((id, score) -> {
                    Integer previo = guardadas.get(id);
                    if (previo == null) nuevas.put(id, score);
                    else if (!previo.equals(score)) cambiadas.put(id, score);
                });
                coincidencias.insertar(perfil.id(), nuevas, false);
                coincidencias.actualizarScores(perfil.id(), cambiadas);

                // Procesos vigentes que ya no pasan las reglas: se quitan.
                // Los cerrados no se tocan (quedan como historial).
                List<String> quitar = new ArrayList<>();
                for (Proceso p : vigentes) {
                    if (guardadas.containsKey(p.id()) && !calculadas.containsKey(p.id())) quitar.add(p.id());
                }
                int quitadas = coincidencias.eliminar(perfil.id(), quitar);

                totalNuevas += nuevas.size();
                totalQuitadas += quitadas;
                log.info("[coincidencias]   \"{}\": {} coinciden, {} nuevas, {} quitadas",
                        perfil.nombre(), calculadas.size(), nuevas.size(), quitadas);
            }
            log.info("[coincidencias] Terminado. {} nuevas en total", totalNuevas);
            logs.coincidencias(nPerfiles, nProcesos, totalNuevas, totalQuitadas, true, null);
            return new Resultado(nPerfiles, nProcesos, totalNuevas, totalQuitadas);
        } catch (RuntimeException e) {
            log.error("[coincidencias] Error: {}", e.getMessage());
            logs.coincidencias(nPerfiles, nProcesos, totalNuevas, totalQuitadas, false, e.getMessage());
            throw e;
        }
    }

    private static Map<String, Integer> calcular(Perfil perfil, List<Proceso> vigentes, Instant ahora) {
        Map<String, Integer> m = new HashMap<>();
        for (Proceso p : vigentes) {
            Integer score = Matching.calcularScore(perfil, p, ahora);
            if (score != null) m.put(p.id(), score);
        }
        return m;
    }

    /**
     * Al crear un perfil: lo que ya existe se ve en el panel pero no se
     * alerta (se guarda como notificado). El primer correo solo trae lo
     * que aparezca después.
     */
    public int generarParaPerfilNuevo(Perfil perfil) {
        Map<String, Integer> m = calcular(perfil, procesos.vigentes(), Instant.now());
        coincidencias.insertar(perfil.id(), m, true);
        return m.size();
    }

    /** Cuántos procesos vigentes coincidirían con un perfil sin guardarlo. */
    public int contarVistaPrevia(Perfil borrador) {
        return calcular(borrador, procesos.vigentes(), Instant.now()).size();
    }
}
