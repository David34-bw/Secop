package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Perfil;
import co.secopradar.modelo.Plan;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.dao.PerfilDAO;
import co.secopradar.modelo.dao.UsuarioDAO;
import co.secopradar.modelo.util.Registro;

import java.util.List;

/** Límites del plan aplicados en el servidor (port de src/lib/limites.js y worker/planes.mjs). */
public class PlanesService {

    private static final Registro log = Registro.de(PlanesService.class);

    public record Resultado(int desactivados, int reactivados) {
    }

    private final UsuarioDAO usuarios;
    private final PerfilDAO perfiles;

    public PlanesService(UsuarioDAO usuarios, PerfilDAO perfiles) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
    }

    public Plan planDe(int userId) {
        return usuarios.porId(userId).map(Plan::efectivo).orElse(Plan.GRATIS);
    }

    /**
     * Deja activos a lo sumo plan.maxPerfiles perfiles. Si sobran, apaga los
     * más recientes y los marca desactivado_por_plan. Si hay cupo, reactiva
     * los que el plan había apagado, en orden de creación. Nunca borra
     * perfiles ni toca los que no apagó el plan.
     */
    public Resultado sincronizarPerfiles(int userId, Plan plan) {
        List<Perfil> lista = perfiles.deUsuarioPorAntiguedad(userId);
        int desactivados = 0;
        int reactivados = 0;

        if (plan.maxPerfiles() == null) {
            for (Perfil p : lista) {
                if (p.desactivadoPorPlan()) {
                    perfiles.cambiarEstado(p.id(), true, false);
                    reactivados++;
                }
            }
            return new Resultado(0, reactivados);
        }

        int activos = 0;
        for (Perfil p : lista) {
            if (!p.activo()) continue;
            activos++;
            if (activos > plan.maxPerfiles()) {
                perfiles.cambiarEstado(p.id(), false, true);
                desactivados++;
            }
        }
        int cupo = Math.max(plan.maxPerfiles() - Math.min(activos, plan.maxPerfiles()), 0);
        for (Perfil p : lista) {
            if (cupo == 0) break;
            if (p.desactivadoPorPlan() && !p.activo()) {
                perfiles.cambiarEstado(p.id(), true, false);
                reactivados++;
                cupo--;
            }
        }
        return new Resultado(desactivados, reactivados);
    }

    /** Paso del cron: ajusta los perfiles de todas las cuentas a su plan efectivo. */
    public void ajustarTodos() {
        int vencidas = usuarios.vencerGracias();
        if (vencidas > 0) log.info("[planes] {} cuenta(s) pasan a Gratis por gracia vencida", vencidas);
        int des = 0, rea = 0;
        for (Usuario u : usuarios.todos()) {
            Plan plan = Plan.efectivo(u);
            Resultado r = sincronizarPerfiles(u.id(), plan);
            des += r.desactivados();
            rea += r.reactivados();
        }
        log.info("[planes] Terminado. {} perfiles apagados, {} reactivados", des, rea);
    }
}
